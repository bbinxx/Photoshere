package com.bbinxx.photoshere

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.SensorManager
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bbinxx.photoshere.core.model.*
import com.bbinxx.photoshere.export.PhotoSphereExporter
import com.bbinxx.photoshere.stitching.SensorPoseStitchingEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SpherePipelineTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun rotationVectorMatchesAndroidRotationMatrix() {
        for (angle in listOf(0.0, 90.0, 180.0, 240.0, 350.0)) {
            val half = Math.toRadians(angle) / 2
            val vector = floatArrayOf(kotlin.math.sin(half).toFloat(), 0f, 0f, kotlin.math.cos(half).toFloat())
            val matrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(matrix, vector)
            val actual = Quaternion.fromRotationVector(vector[0], vector[1], vector[2], vector[3]).rotate(Vec3.FORWARD)
            assertEquals(-matrix[2], actual.x, 0.0001f)
            assertEquals(-matrix[5], actual.y, 0.0001f)
            assertEquals(-matrix[8], actual.z, 0.0001f)
        }
    }

    @Test
    fun sixCameraViewsCoverPolesAndExportReadableGpanoJpeg() = runBlocking {
        val id = "test_${UUID.randomUUID()}"
        val dir = File(context.filesDir, "sessions/$id").apply { mkdirs() }
        val export = File(context.cacheDir, "$id.jpg")
        try {
            val poses = listOf(0f to 0f, 90f to 0f, 180f to 0f, -90f to 0f, 0f to 90f, 0f to -90f)
            val images = poses.mapIndexed { i, (yaw, pitch) ->
                val image = File(dir, "$i.jpg")
                val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.rgb(100, 150, 200))
                image.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
                bitmap.recycle()
                CapturedImageMeta(i, image.absolutePath,
                    Quaternion.fromYawPitchRoll(yaw, pitch).toFloatArray().toList(), yaw, pitch,
                    System.currentTimeMillis(), horizontalFovDeg = 110f, verticalFovDeg = 110f)
            }
            val session = SessionMetadata(id, System.currentTimeMillis(), Quaternion.IDENTITY.toFloatArray().toList(),
                0f, 110f, 110f, emptyList(), images, completed = true)
            val result = SensorPoseStitchingEngine(context).stitch(session,
                CaptureSettings(outputWidth = 1536, autoExport = false)) { }
            assertTrue("Full synthetic sphere coverage: ${result.coverage}", result.coverage > 0.999f)
            val decoded = BitmapFactory.decodeFile(result.outputFile.path)
            assertNotNull(decoded)
            assertEquals(decoded.width, decoded.height * 2)
            for (y in listOf(0, decoded.height / 2, decoded.height - 1)) {
                assertTrue("Missing real pixels at latitude row $y", Color.blue(decoded.getPixel(decoded.width / 2, y)) > 100)
            }
            decoded.recycle()
            val xmp = ExifInterface(result.outputFile).getAttribute(ExifInterface.TAG_XMP)
            assertNotNull("Independent EXIF parser must find XMP", xmp)
            assertTrue(xmp!!.contains("GPano:ProjectionType=\"equirectangular\""))
            assertTrue(PhotoSphereExporter(context).writeToUri(result.outputFile, android.net.Uri.fromFile(export)))
            assertTrue(export.readBytes().contentEquals(result.outputFile.readBytes()))
            assertTrue(images.all { File(it.filePath).exists() })
        } finally {
            dir.deleteRecursively()
            export.delete()
        }
    }
}
