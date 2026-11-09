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
            val dark = validator.validate(file)
            assertFalse(dark.acceptable)
            assertTrue(dark.readable)
            assertTrue(com.bbinxx.photoshere.capture.CaptureAcceptance.accepts(true, dark, false))
            val root = File(context.cacheDir, "manual_session_${UUID.randomUUID()}").apply { mkdirs() }
            try {
                val isolated = object : android.content.ContextWrapper(context) {
                    override fun getFilesDir(): File = root
                }
                val store = com.bbinxx.photoshere.data.repository.SessionStore(isolated)
                val quaternion = com.bbinxx.photoshere.core.model.Quaternion.IDENTITY
                val session = store.create(quaternion, 0f, 65f, 50f, emptyList())
                val original = file.copyTo(File(root, "original.jpg"))
                val image = com.bbinxx.photoshere.core.model.CapturedImageMeta(0, original.path,
                    quaternion.toFloatArray().toList(), 0f, 0f, 0L, accepted = true,
                    note = "Accepted by manual shutter: Too dark", horizontalFovDeg = 65f,
                    verticalFovDeg = 50f, manualOverride = true)
                store.save(session.copy(images = listOf(image)))
                val reopened = store.load(session.id)!!
                assertTrue(reopened.images.single().accepted)
                assertTrue(reopened.images.single().manualOverride)
                assertEquals(1, store.existingImages(reopened).size)
            } finally { root.deleteRecursively() }
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
            file.writeText("not a JPEG")
            val unreadable = validator.validate(file)
            assertFalse(unreadable.readable)
            assertFalse(com.bbinxx.photoshere.capture.CaptureAcceptance.accepts(true, unreadable, true))
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }
}
