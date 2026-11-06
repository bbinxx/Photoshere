package com.bbinxx.photoshere.stitching

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.bbinxx.photoshere.core.model.CaptureSettings
import com.bbinxx.photoshere.core.model.CapturedImageMeta
import com.bbinxx.photoshere.core.model.ProcessingProgress
import com.bbinxx.photoshere.core.model.Quaternion
import com.bbinxx.photoshere.core.model.SessionMetadata
import com.bbinxx.photoshere.core.model.SphereMetadata
import com.bbinxx.photoshere.core.model.Vec3
import com.bbinxx.photoshere.export.GpanoMetadataWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Spherical projector driven by the fused sensor pose recorded at capture time.
 *
 * For every source pixel we reconstruct the camera ray in device coordinates
 * (principal point at the image centre, focal length from the measured
 * horizontal FOV), rotate it into the world frame with the capture quaternion,
 * and splat it into an equirectangular accumulator. Overlaps are blended with a
 * cosine + edge feather weight, exposure is normalised per frame, and remaining
 * uncovered pixels are hole-filled from valid neighbours.
 *
 * Limitations, stated explicitly:
 *  - Camera pose comes from the rotation vector. Gyro drift or magnetic
 *    disturbance can shift images; feature-based bundle adjustment is the next
 *    quality step and is not silently claimed here.
 *  - Lens distortion is not corrected unless a calibration profile is supplied.
 *  - Moving objects may ghost because no temporal de-ghosting is applied.
 */
class SensorPoseStitchingEngine(
    private val context: Context,
) : StitchingEngine {

    private val preprocessor = ImagePreprocessor()

    override suspend fun stitch(
        session: SessionMetadata,
        settings: CaptureSettings,
        onProgress: (ProcessingProgress) -> Unit,
    ): StitchingResult = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        val warnings = mutableListOf<String>()
        val images = session.images.filter { image ->
            image.accepted && image.quaternion.size >= 4 && File(image.filePath).exists()
        }
        if (images.size < 3) {
            throw StitchingException("At least 3 captured images are required to stitch a sphere")
        }

        val width = ((settings.outputWidth.coerceIn(1536, 8192) / 2) * 2)
        val height = width / 2
        val pixelCount = width * height
        val maxSourceSide = (1300 * settings.processingQuality.coerceIn(0.5f, 1.5f))
            .toInt().coerceIn(900, 2200)

        var accum: FloatArray? = null
        var weights: FloatArray? = null
        try {
            val rgbAccum = FloatArray(pixelCount * 3)
            val weightAccum = FloatArray(pixelCount)
            accum = rgbAccum
            weights = weightAccum

            val referenceYawRad = Math.toRadians(session.referenceYawDeg.toDouble())
            val horizontalFov = session.horizontalFovDeg.coerceIn(25f, 120f)
            var runningLuma = 0.0
            var gainSamples = 0
            var used = 0

            images.forEachIndexed { index, meta ->
                coroutineContext.ensureActive()
                val bitmap = preprocessor.load(File(meta.filePath), maxSourceSide)
                if (bitmap == null) {
                    warnings.add("Skipped unreadable image ${File(meta.filePath).name}")
                    return@forEachIndexed
                }
                val sourceWidth = bitmap.width
                val sourceHeight = bitmap.height
                try {
                    projectImage(
                        bitmap = bitmap,
                        meta = meta,
                        outputWidth = width,
                        outputHeight = height,
                        horizontalFovDeg = horizontalFov,
                        referenceYawRad = referenceYawRad,
                        rgbAccum = rgbAccum,
                        weightAccum = weightAccum,
                        preprocessor = preprocessor,
                        gainProvider = { luma ->
                            val gain = if (gainSamples == 0) 1.0 else {
                                val target = runningLuma / gainSamples
                                (target / luma.coerceAtLeast(1.0)).coerceIn(0.75, 1.35)
                            }
                            runningLuma += luma
                            gainSamples++
                            gain.toFloat()
                        },
                    )
                    used++
                } finally {
                    bitmap.recycle()
                }
                onProgress(
                    ProcessingProgress(
                        fraction = (index + 1).toFloat() / images.size * 0.85f,
                        stage = "Projecting ${index + 1} of ${images.size}",
                    ),
                )
                Log.d(TAG, "Projected ${meta.filePath} (${sourceWidth}x${sourceHeight})")
            }

            if (used < 3) {
                throw StitchingException("Not enough readable images to stitch")
            }

            onProgress(ProcessingProgress(0.9f, "Blending seams"))
            val coverage = normaliseAndFill(rgbAccum, weightAccum, width, height)
            if (coverage < 0.90f) {
                warnings.add(
                    "Only ${(coverage * 100).toInt()}% of the sphere was covered. Retake missed areas for a clean result.",
                )
            }

            onProgress(ProcessingProgress(0.95f, "Encoding equirectangular JPEG"))
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(pixelCount)
            for (i in 0 until pixelCount) {
                val r = (rgbAccum[i * 3] * 255f).toInt().coerceIn(0, 255)
                val g = (rgbAccum[i * 3 + 1] * 255f).toInt().coerceIn(0, 255)
                val b = (rgbAccum[i * 3 + 2] * 255f).toInt().coerceIn(0, 255)
                pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            output.setPixels(pixels, 0, width, 0, 0, width, height)

            val outputFile = File(context.filesDir, "sessions/${session.id}/stitched.jpg")
            outputFile.parentFile?.mkdirs()
            val compressed = outputFile.outputStream().use { stream ->
                output.compress(Bitmap.CompressFormat.JPEG, settings.jpegQuality.coerceIn(70, 100), stream)
            }
            output.recycle()
            if (!compressed) throw StitchingException("JPEG encoding failed")

            val poseHeading = ((session.referenceYawDeg % 360f) + 360f) % 360f
            val metadata = SphereMetadata(
                width = width,
                height = height,
                fullPanoWidth = width,
                fullPanoHeight = height,
                croppedAreaWidth = width,
                croppedAreaHeight = height,
                poseHeadingDeg = poseHeading,
            )
            GpanoMetadataWriter.write(outputFile, metadata)

            onProgress(ProcessingProgress(1f, "Photo Sphere ready"))
            StitchingResult(
                outputFile = outputFile,
                width = width,
                height = height,
                coverage = coverage,
                usedImages = used,
                warnings = warnings.toList(),
                elapsedMs = System.currentTimeMillis() - start,
            )
        } catch (error: OutOfMemoryError) {
            accum = null
            weights = null
            throw StitchingException(
                "Not enough memory to stitch at this resolution. Lower processing quality and retry.",
                error,
            )
        }
    }

    private fun projectImage(
        bitmap: Bitmap,
        meta: CapturedImageMeta,
        outputWidth: Int,
        outputHeight: Int,
        horizontalFovDeg: Float,
        referenceYawRad: Double,
        rgbAccum: FloatArray,
        weightAccum: FloatArray,
        preprocessor: ImagePreprocessor,
        gainProvider: (Double) -> Float,
    ) {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 4 || height < 4) return
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val quaternion = Quaternion.fromFloatArray(meta.quaternion.toFloatArray()).normalized()
        val focal = (0.5 * width / tan(Math.toRadians(horizontalFovDeg.toDouble()) / 2.0)).toFloat()
        val cameraForward = quaternion.rotate(Vec3.FORWARD)
        val gain = gainProvider(preprocessor.centralMeanLuma(pixels, width, height))
        val feather = (0.07f * minOf(width, height)).coerceAtLeast(4f)
        val centerX = width / 2f
        val centerY = height / 2f

        for (y in 0 until height) {
            val rowOffset = y * width
            val ny = -((y + 0.5f) - centerY) / focal
            for (x in 0 until width) {
                val nx = ((x + 0.5f) - centerX) / focal
                val rayLocal = Vec3(nx, ny, -1f).normalized()
                val direction = quaternion.rotate(rayLocal)
                val cosToCenter = direction.dot(cameraForward)
                if (cosToCenter <= 0.02f) continue

                val longitude = wrapRadians(
                    atan2(direction.x.toDouble(), direction.y.toDouble()) - referenceYawRad,
                )
                val latitude = asin(direction.z.coerceIn(-1f, 1f).toDouble())
                val ox = ((longitude + PI) / (2.0 * PI)) * outputWidth
                val oy = ((PI / 2.0 - latitude) / PI) * outputHeight
                if (oy < 0.0 || oy >= outputHeight) continue

                var x0 = floor(ox).toInt()
                val fy = (oy - floor(oy)).toFloat()
                var y0 = floor(oy).toInt()
                if (x0 >= outputWidth) x0 -= outputWidth
                if (x0 < 0) x0 += outputWidth
                val fx = (ox - floor(ox)).toFloat()
                val x1 = if (x0 == outputWidth - 1) 0 else x0 + 1
                var y1 = y0 + 1
                if (y1 >= outputHeight) y1 = outputHeight - 1
                if (y0 >= outputHeight) y0 = outputHeight - 1

                val edge = minOf(x, width - 1 - x, y, height - 1 - y).toFloat()
                val edgeWeight = (edge / feather).coerceIn(0f, 1f)
                if (edgeWeight <= 0f) continue
                val weight = cosToCenter * cosToCenter * edgeWeight

                val color = pixels[rowOffset + x]
                val r = ((color shr 16) and 0xFF) / 255f * gain
                val g = ((color shr 8) and 0xFF) / 255f * gain
                val b = (color and 0xFF) / 255f * gain

                val w00 = (1f - fx) * (1f - fy) * weight
                val w10 = fx * (1f - fy) * weight
                val w01 = (1f - fx) * fy * weight
                val w11 = fx * fy * weight

                splat(rgbAccum, weightAccum, y0 * outputWidth + x0, r, g, b, w00)
                splat(rgbAccum, weightAccum, y0 * outputWidth + x1, r, g, b, w10)
                splat(rgbAccum, weightAccum, y1 * outputWidth + x0, r, g, b, w01)
                splat(rgbAccum, weightAccum, y1 * outputWidth + x1, r, g, b, w11)
            }
        }
    }

    private fun splat(
        accum: FloatArray,
        weights: FloatArray,
        index: Int,
        r: Float,
        g: Float,
        b: Float,
        weight: Float,
    ) {
        if (weight <= 0f) return
        val offset = index * 3
        accum[offset] += r * weight
        accum[offset + 1] += g * weight
        accum[offset + 2] += b * weight
        weights[index] += weight
    }

    /** Returns the fraction of the sphere that had real pixel coverage. */
    private fun normaliseAndFill(
        accum: FloatArray,
        weights: FloatArray,
        width: Int,
        height: Int,
    ): Float {
        val total = width * height
        var covered = 0
        for (i in 0 until total) {
            val weight = weights[i]
            if (weight > 1e-4f) {
                val inv = 1f / weight
                accum[i * 3] *= inv
                accum[i * 3 + 1] *= inv
                accum[i * 3 + 2] *= inv
                covered++
            }
        }
        val coverage = covered.toFloat() / total

        // Fill uncovered pixels from valid neighbours. Repeated passes propagate
        // colour into small holes; large unobserved regions remain flagged.
        if (covered < total) {
            val filled = BooleanArray(total) { weights[it] > 1e-4f }
            for (pass in 0 until 12) {
                var changed = 0
                for (y in 1 until height - 1) {
                    val row = y * width
                    for (x in 1 until width - 1) {
                        val index = row + x
                        if (filled[index]) continue
                        var r = 0f
                        var g = 0f
                        var b = 0f
                        var count = 0
                        for (dy in -1..1) {
                            for (dx in -1..1) {
                                if (dx == 0 && dy == 0) continue
                                val n = index + dy * width + dx
                                if (filled[n]) {
                                    r += accum[n * 3]
                                    g += accum[n * 3 + 1]
                                    b += accum[n * 3 + 2]
                                    count++
                                }
                            }
                        }
                        if (count > 0) {
                            accum[index * 3] = r / count
                            accum[index * 3 + 1] = g / count
                            accum[index * 3 + 2] = b / count
                            filled[index] = true
                            changed++
                        }
                    }
                }
                if (changed == 0) break
            }
        }
        return coverage
    }

    private fun wrapRadians(value: Double): Double {
        var result = value
        while (result > PI) result -= 2.0 * PI
        while (result < -PI) result += 2.0 * PI
        return result
    }

    private companion object {
        const val TAG = "SensorPoseStitcher"
    }
}
