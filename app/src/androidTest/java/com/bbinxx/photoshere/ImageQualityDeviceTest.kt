package com.bbinxx.photoshere

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.bbinxx.photoshere.stitching.ImageQualityValidator
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class ImageQualityDeviceTest {
    @Test fun validatesSharpnessAndBothExposureExtremes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "quality_${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val validator = ImageQualityValidator()
        fun save() { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } }
        try {
            bitmap.eraseColor(Color.BLACK); save()
            assertFalse(validator.validate(file).acceptable)
            bitmap.eraseColor(Color.WHITE); save()
            assertFalse(validator.validate(file).acceptable)
            bitmap.eraseColor(Color.GRAY); save()
            assertFalse(validator.validate(file).acceptable)
            for (y in 0 until 128) for (x in 0 until 128) {
                val value = if ((x / 8 + y / 8) % 2 == 0) 40 else 220
                bitmap.setPixel(x, y, Color.rgb(value, value, value))
            }
            save()
            assertTrue(validator.validate(file).acceptable)
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }
}
