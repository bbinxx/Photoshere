package com.bbinxx.photoshere

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import com.bbinxx.photoshere.core.model.*
import com.bbinxx.photoshere.stitching.VisualPoseRefiner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.opencv.android.OpenCVLoader
import org.opencv.core.Point
import java.io.File
import java.util.UUID
import kotlin.random.Random

class VisualPoseRefinerDeviceTest {
    @Test fun calibratedRansacRecoversRotationDespiteOutliers() {
        assertTrue(OpenCVLoader.initLocal())
        val geometry = VisualPoseRefiner.Geometry(800, 600, 70f, 55f)
        val expected = Quaternion.fromAxisAngle(Vec3(0.2f, 1f, 0.1f), Math.toRadians(5.0).toFloat())
        val a = mutableListOf<Point>(); val b = mutableListOf<Point>()
        for (row in 0..6) for (column in 0..8) {
            val point = Point(100.0 + column * 70, 90.0 + row * 70)
            val normalized = geometry.normalized(point)
            val ray = expected.rotate(Vec3(normalized.x.toFloat(), -normalized.y.toFloat(), -1f))
            val projected = CameraGeometry.project(ray, 70f, 55f)!!
            a += point
            b += Point(projected.first * 800.0, projected.second * 600.0)
        }
        repeat(10) { i -> a += Point(120.0 + i * 37, 150.0 + i * 11); b += Point(600.0 - i * 31, 90.0 + i * 29) }
        val result = VisualPoseRefiner().estimateRotation(a, b, geometry, geometry)
        assertNotNull(result)
        assertTrue(result!!.inliers >= 50)
        assertEquals(0f, result.quaternion.angularDistanceDegrees(expected), 0.2f)
    }

    @Test fun degenerateOrInsufficientMatchesAreRejected() {
        assertTrue(OpenCVLoader.initLocal())
        val geometry = VisualPoseRefiner.Geometry(800, 600, 70f, 55f)
        val points = List(30) { Point(100.0 + it * 10, 200.0) }
        val refiner = VisualPoseRefiner()
        assertNull(refiner.estimateRotation(points.take(10), points.take(10), geometry, geometry))
        assertNull(refiner.estimateRotation(points, points, geometry, geometry))
        val invalid = List(30) { Point(Double.NaN, 100.0) }
        assertNull(refiner.estimateRotation(invalid, points, geometry, geometry))
    }

    @Test fun orbRefinementCorrectsSmallSensorErrorsInActualJpegs() = runBlocking<Unit> {
        withImages(true, listOf(0f, 4f, -3f)) { images ->
            val result = VisualPoseRefiner().refine(images)
            assertEquals(2, result.refinedCount)
            val anchor = Quaternion.fromFloatArray(images.first().quaternion.toFloatArray())
            result.images.forEach {
                assertEquals(0f, anchor.angularDistanceDegrees(Quaternion.fromFloatArray(it.quaternion.toFloatArray())), 0.2f)
            }
        }
    }

    @Test fun featurelessViewsAndLargeCorrectionsKeepSensorPoses() = runBlocking<Unit> {
        withImages(false, listOf(0f, 4f, -3f)) { images ->
            val result = VisualPoseRefiner().refine(images)
            assertEquals(0, result.refinedCount)
            assertEquals(images, result.images)
        }
        withImages(true, listOf(0f, 20f, -20f)) { images ->
            val result = VisualPoseRefiner().refine(images)
            assertEquals(0, result.refinedCount)
            assertEquals(images, result.images)
        }
    }

    private suspend fun withImages(textured: Boolean, yaws: List<Float>, block: suspend (List<CapturedImageMeta>) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(app.cacheDir, "visual_test_${UUID.randomUUID()}").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(120, 130, 140))
        if (textured) {
            val canvas = Canvas(bitmap)
            val paint = Paint()
            val random = Random(42)
            repeat(1400) {
                paint.color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
                canvas.drawCircle(random.nextFloat() * 640, random.nextFloat() * 480, random.nextFloat() * 8 + 2, paint)
            }
        }
        try {
            val images = yaws.mapIndexed { index, yaw ->
                val file = File(directory, "$index.jpg")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                CapturedImageMeta(index, file.path, Quaternion.fromYawPitchRoll(yaw, 0f).toFloatArray().toList(),
                    yaw, 0f, 0L, horizontalFovDeg = 70f, verticalFovDeg = 55f)
            }
            block(images)
        } finally { bitmap.recycle(); directory.deleteRecursively() }
    }
}
