package com.bbinxx.photoshere.stitching

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bbinxx.photoshere.core.model.QualityReport
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.imgproc.Imgproc
import java.io.File

/**
 * Rejects unusably blurry or badly exposed captures before they enter a
 * Photo Sphere session. Sharpness is the variance of the Laplacian on a
 * downscaled copy; exposure is mean luminance.
 *
 * The image is only read, never modified, so a failed validation can always be
 * retried without losing the original file.
 */
class ImageQualityValidator(
    private val minSharpness: Double = 45.0,
    private val minMeanLuma: Double = 18.0,
    private val maxMeanLuma: Double = 245.0,
) {

    private val nativeAvailable by lazy { runCatching { OpenCVLoader.initLocal() }.getOrDefault(false) }

    fun validate(file: File): QualityReport {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return QualityReport(0f, 0f, false, "Image could not be read")
        }
        val sample = maxOf(options.outWidth, options.outHeight) / 1024
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            ?: return QualityReport(0f, 0f, false, "Image could not be decoded")

        return try {
            if (nativeAvailable) runCatching { validateWithOpenCv(bitmap) }
                .getOrElse { validateWithBitmap(bitmap) }
            else validateWithBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun validateWithOpenCv(bitmap: Bitmap): QualityReport {
        val rgba = Mat()
        val gray = Mat()
        val laplacian = Mat()
        val mean = MatOfDouble()
        val stddev = MatOfDouble()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
            Core.meanStdDev(laplacian, mean, stddev)
            val sharpness = stddev.toArray().firstOrNull()?.let { it * it } ?: 0.0
            val luma = Core.mean(gray).`val`[0]
            return report(sharpness, luma)
        } finally {
            rgba.release()
            gray.release()
            laplacian.release()
            mean.release()
            stddev.release()
        }
    }

    private fun validateWithBitmap(bitmap: Bitmap): QualityReport {
        val scale = minOf(1f, 512f / maxOf(bitmap.width, bitmap.height))
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== bitmap) scaled.recycle()

        var sum = 0.0
        var sumSquares = 0.0
        val luma = DoubleArray(pixels.size)
        for (i in pixels.indices) {
            val color = pixels[i]
            val value = (0.299 * ((color shr 16) and 0xFF) +
                0.587 * ((color shr 8) and 0xFF) +
                0.114 * (color and 0xFF))
            luma[i] = value
            sum += value
            sumSquares += value * value
        }
        val mean = sum / pixels.size
        val variance = (sumSquares / pixels.size) - mean * mean
        var laplacianEnergy = 0.0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val value = 4 * luma[i] - luma[i - 1] - luma[i + 1] -
                    luma[i - width] - luma[i + width]
                laplacianEnergy += value * value
            }
        }
        val sharpness = laplacianEnergy / ((width - 2) * (height - 2)).coerceAtLeast(1)
        val reason = when {
            mean < minMeanLuma -> "Too dark — move to better light"
            mean > maxMeanLuma -> "Overexposed — avoid pointing at direct light"
            sharpness < minSharpness * 0.5 -> "Image is blurry — hold still and retry"
            else -> null
        }
        return QualityReport(
            sharpness = (sharpness * 0.25).toFloat(),
            meanLuma = mean.toFloat(),
            acceptable = reason == null,
            reason = reason,
        )
    }

    private fun report(sharpness: Double, meanLuma: Double): QualityReport {
        val reason = when {
            meanLuma < minMeanLuma -> "Too dark — move to better light"
            meanLuma > maxMeanLuma -> "Overexposed — avoid pointing at direct light"
            sharpness < minSharpness -> "Image is blurry — hold still and retry"
            else -> null
        }
        return QualityReport(
            sharpness = sharpness.toFloat(),
            meanLuma = meanLuma.toFloat(),
            acceptable = reason == null,
            reason = reason,
        )
    }
}
