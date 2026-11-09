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
 * Each destination ray is mapped into the source camera using its sensor pose
 * and measured field of view, then bilinearly sampled. Overlaps use cosine and
 * edge feather weights with per-frame exposure normalisation. Unobserved areas
 * remain unfilled and are reported to the user.
 *
 * Limitations, stated explicitly:
 *  - Sensor poses receive conservative local ORB/RANSAC rotation refinement.
 *    Global bundle adjustment is not implemented; drift/parallax may remain.
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
            onProgress(ProcessingProgress(0f, "Matching overlapping images"))
            val alignment = VisualPoseRefiner().refine(images) {
                onProgress(ProcessingProgress(it * 0.25f, "Refining image alignment"))
            }
            warnings += alignment.warnings
            val rgbAccum = FloatArray(pixelCount * 3)
            val weightAccum = FloatArray(pixelCount)
            accum = rgbAccum
            weights = weightAccum

            val referenceYawRad = Math.toRadians(session.referenceYawDeg.toDouble())
            val horizontalFov = session.horizontalFovDeg.coerceIn(25f, 120f)
            var used = 0

            alignment.images.forEachIndexed { index, meta ->
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
                    )
                    used++
                } finally {
                    bitmap.recycle()
                }
                onProgress(
                    ProcessingProgress(
                        fraction = 0.25f + (index + 1).toFloat() / images.size * 0.60f,
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

    private suspend fun projectImage(
        bitmap: Bitmap,
        meta: CapturedImageMeta,
        outputWidth: Int,
        outputHeight: Int,
        horizontalFovDeg: Float,
        referenceYawRad: Double,
        rgbAccum: FloatArray,
        weightAccum: FloatArray,
    ) {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 4 || height < 4) return
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val inverse = Quaternion.fromFloatArray(meta.quaternion.toFloatArray()).inverse()
        val axisX = inverse.rotate(Vec3(1f, 0f, 0f))
        val axisY = inverse.rotate(Vec3(0f, 1f, 0f))
        val axisZ = inverse.rotate(Vec3(0f, 0f, 1f))
        val hFov = meta.horizontalFovDeg.takeIf { it in 20f..150f } ?: horizontalFovDeg
        val fx = (width * 0.5 / tan(Math.toRadians(hFov.toDouble()) * 0.5)).toFloat()
        val fy = meta.verticalFovDeg.takeIf { it in 20f..150f }?.let {
            (height * 0.5 / tan(Math.toRadians(it.toDouble()) * 0.5)).toFloat()
        } ?: fx
        val feather = (0.07f * minOf(width, height)).coerceAtLeast(4f)
        val sinLon = FloatArray(outputWidth)
        val cosLon = FloatArray(outputWidth)
        for (x in 0 until outputWidth) {
            val longitude = ((x + 0.5) / outputWidth * 2 - 1) * PI + referenceYawRad
            sinLon[x] = sin(longitude).toFloat()
            cosLon[x] = cos(longitude).toFloat()
        }
        val exposureSamples = mutableListOf<Pair<Float, Float>>()
        for (y in 0 until outputHeight step 16) {
            coroutineContext.ensureActive()
            val lat = PI / 2 - (y + 0.5) / outputHeight * PI
            val cp = cos(lat).toFloat()
            val wz = sin(lat).toFloat()
            for (x in 0 until outputWidth step 16) {
                val index = y * outputWidth + x
                val previousWeight = weightAccum[index]
                if (previousWeight < 0.1f) continue
                val wx = cp * sinLon[x]
                val wy = cp * cosLon[x]
                val z = axisX.z * wx + axisY.z * wy + axisZ.z * wz
                if (z >= -0.02f) continue
                val sx = width * 0.5f + fx * (axisX.x * wx + axisY.x * wy + axisZ.x * wz) / -z
                val sy = height * 0.5f - fy * (axisX.y * wx + axisY.y * wy + axisZ.y * wz) / -z
                if (sx < feather || sy < feather || sx >= width - feather || sy >= height - feather) continue
                val color = pixels[sy.toInt() * width + sx.toInt()]
                val source = (0.299f * ((color shr 16) and 255) + 0.587f * ((color shr 8) and 255) +
                    0.114f * (color and 255)) / 255f
                val offset = index * 3
                val previous = (0.299f * rgbAccum[offset] + 0.587f * rgbAccum[offset + 1] +
                    0.114f * rgbAccum[offset + 2]) / previousWeight
                exposureSamples += previous to source
            }
        }
        val gain = OverlapExposure.gain(exposureSamples)
        // Inverse mapping samples every destination ray. Forward splatting
        // leaves resolution-dependent holes, especially around the poles.
        for (y in 0 until outputHeight) {
            coroutineContext.ensureActive()
            val latitude = PI / 2 - (y + 0.5) / outputHeight * PI
            val cp = cos(latitude).toFloat()
            val wz = sin(latitude).toFloat()
            for (x in 0 until outputWidth) {
                val wx = cp * sinLon[x]
                val wy = cp * cosLon[x]
                val cz = axisX.z * wx + axisY.z * wy + axisZ.z * wz
                if (cz >= -0.02f) continue
                val cx = axisX.x * wx + axisY.x * wy + axisZ.x * wz
                val cy = axisX.y * wx + axisY.y * wy + axisZ.y * wz
                val sx = width * 0.5f + fx * cx / -cz - 0.5f
                val sy = height * 0.5f - fy * cy / -cz - 0.5f
                if (sx < 0f || sy < 0f || sx >= width - 1 || sy >= height - 1) continue
                val ix = sx.toInt()
                val iy = sy.toInt()
                val dx = sx - ix
                val dy = sy - iy
                val edge = minOf(sx, width - 1 - sx, sy, height - 1 - sy)
                val weight = cz * cz * (edge / feather).coerceIn(0f, 1f)
                val a = pixels[iy * width + ix]
                val b = pixels[iy * width + ix + 1]
                val c = pixels[(iy + 1) * width + ix]
                val d = pixels[(iy + 1) * width + ix + 1]
                fun channel(shift: Int): Float {
                    val top = ((a shr shift) and 255) * (1 - dx) + ((b shr shift) and 255) * dx
                    val bottom = ((c shr shift) and 255) * (1 - dx) + ((d shr shift) and 255) * dx
                    return (top * (1 - dy) + bottom * dy) / 255f * gain
                }
                splat(rgbAccum, weightAccum, y * outputWidth + x,
                    channel(16), channel(8), channel(0), weight)
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
    private suspend fun normaliseAndFill(
        accum: FloatArray,
        weights: FloatArray,
        width: Int,
        height: Int,
    ): Float {
        val total = width * height
        var covered = 0
        for (i in 0 until total) {
            if (i % width == 0) coroutineContext.ensureActive()
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

        // Never invent sky or ground by propagating neighbouring colours.
        // Missing coverage is reported so the original session can be retaken.
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
