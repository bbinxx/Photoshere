package com.bbinxx.photoshere.core.model

import org.junit.Assert.*
import org.junit.Test

class CameraGeometryTest {
    @Test fun sensorCropAndDisplayRotationChangeCorrectAxes() {
        val landscape = CameraGeometry.fieldOfView(8f, 6f, 4f, 4f / 3, 0)
        val portrait = CameraGeometry.fieldOfView(8f, 6f, 4f, 4f / 3, 90)
        val cropped = CameraGeometry.fieldOfView(8f, 6f, 4f, 16f / 9, 0)
        assertEquals(landscape.first, portrait.second, 0.001f)
        assertEquals(landscape.second, portrait.first, 0.001f)
        assertTrue(cropped.second < landscape.second)
    }
    @Test fun ultraWideHasLargerFieldOfView() {
        val main = CameraGeometry.fieldOfView(8f, 6f, 4f, 4f / 3, 90)
        val ultra = CameraGeometry.fieldOfView(8f, 6f, 2f, 4f / 3, 90)
        assertTrue(ultra.first > main.first && ultra.second > main.second)
    }
    @Test fun aimingProjectsThroughIntrinsicsAndRejectsBehindCamera() {
        assertEquals(0.5f, CameraGeometry.project(Vec3.FORWARD, 90f, 90f)!!.first, 0f)
        assertEquals(1f, CameraGeometry.project(Vec3(1f, 0f, -1f), 90f, 90f)!!.first, 0.001f)
        assertTrue(CameraGeometry.project(Vec3(1f, 0f, -1f), 120f, 90f)!!.first < 1f)
        assertNull(CameraGeometry.project(Vec3(0f, 0f, 1f), 90f, 90f))
    }
}
