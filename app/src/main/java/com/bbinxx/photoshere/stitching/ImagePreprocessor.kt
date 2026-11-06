package com.bbinxx.photoshere.stitching

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Decodes a captured JPEG, applies EXIF orientation (CameraX stores rotation in
 * EXIF rather than rotating the pixels), and bounds the working resolution so
 * stitching stays inside a predictable memory budget.
 */
class ImagePreprocessor {

    fun load(file: File, maxSide: Int = 1600): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        return applyExifOrientation(decoded, readOrientation(file))
    }

    fun centralMeanLuma(pixels: IntArray, width: Int, height: Int): Double {
        val left = width / 4
        val right = width * 3 / 4
        val top = height / 4
        val bottom = height * 3 / 4
        var sum = 0.0
        var count = 0
        var y = top
        while (y < bottom) {
            var x = left
            val rowOffset = y * width
            while (x < right) {
                val color = pixels[rowOffset + x]
                sum += 0.299 * ((color shr 16) and 0xFF) +
                    0.587 * ((color shr 8) and 0xFF) +
                    0.114 * (color and 0xFF)
                count++
                x += 4
            }
            y += 4
        }
        return if (count == 0) 128.0 else sum / count
    }

    private fun readOrientation(file: File): Int {
        return runCatching {
            val exif = ExifInterface(file.absolutePath)
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return try {
            val transformed = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true,
            )
            if (transformed !== bitmap) bitmap.recycle()
            transformed
        } catch (error: OutOfMemoryError) {
            bitmap
        }
    }
}
