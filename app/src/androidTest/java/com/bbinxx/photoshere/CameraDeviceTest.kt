package com.bbinxx.photoshere

import android.Manifest
import android.graphics.BitmapFactory
import androidx.camera.view.PreviewView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bbinxx.photoshere.camera.CameraXController
import com.bbinxx.photoshere.sensors.OrientationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CameraDeviceTest {
    @Test
    fun rearCameraSavesRealJpegAndSensorsDeliverOrientation() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val controller = CameraXController(context)
        val tracker = OrientationTracker(context)
        val output = File(context.cacheDir, "camera_test_${UUID.randomUUID()}.jpg")
        try {
            lateinit var activity: MainActivity
            lateinit var preview: PreviewView
            scenario.onActivity {
                activity = it
                preview = PreviewView(it)
                it.setContentView(preview)
            }
            withContext(Dispatchers.Main) { controller.bind(preview, activity) }
            val ready = withTimeout(20_000) { controller.state.first { it.ready } }
            assertNotNull(ready.selectedCameraId)
            assertFalse(controller.selectedLens()!!.isFront)
            tracker.start()
            val orientation = withTimeout(10_000) { tracker.state.first { it.sensorAvailable } }
            assertEquals(1f, orientation.quaternion.norm(), 0.001f)
            var shutterReported = false
            controller.onCaptureStarted = { shutterReported = true }
            withTimeout(20_000) { withContext(Dispatchers.Main) { controller.capture(output) } }
            assertTrue("Exposure callback must record the actual capture pose", shutterReported)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(output.path, bounds)
            assertTrue("Camera must save actual image pixels", bounds.outWidth > 0 && bounds.outHeight > 0)
            assertTrue(output.length() > 1000)
            assertFalse(controller.state.value.busy)
            fun checkPreviewAspect() {
                val exif = androidx.exifinterface.media.ExifInterface(output)
                BitmapFactory.decodeFile(output.path, bounds)
                val ratio = if (exif.rotationDegrees % 180 == 0) bounds.outWidth.toFloat() / bounds.outHeight
                    else bounds.outHeight.toFloat() / bounds.outWidth
                assertEquals("Preview must match the complete JPEG field", ratio, controller.state.value.previewAspectRatio, 0.02f)
            }
            checkPreviewAspect()
            val mainFov = controller.selectedLens()!!.horizontalFovDeg
            val ultra = controller.state.value.lenses.firstOrNull { !it.isFront && it.relativeZoom < 0.8f }
            if (ultra != null) {
                withContext(Dispatchers.Main) { controller.selectLens(ultra.id) }
                withTimeout(20_000) { controller.state.first { it.ready && it.selectedCameraId == ultra.id } }
                assertTrue(controller.selectedLens()!!.horizontalFovDeg > mainFov)
                withTimeout(20_000) { withContext(Dispatchers.Main) { controller.capture(output) } }
                checkPreviewAspect()
                android.util.Log.i("CameraDeviceTest", "Ultra-wide capture verified: ${ultra.id}")
            } else android.util.Log.i("CameraDeviceTest", "Ultra-wide is not exposed by this device")
        } finally {
            tracker.stop()
            withContext(Dispatchers.Main) { controller.unbind() }
            scenario.close()
            output.delete()
        }
    }
}
