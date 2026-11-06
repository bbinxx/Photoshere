package com.bbinxx.photoshere.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuaternionTest {

    @Test
    fun identityHasNoRotation() {
        val forward = Quaternion.IDENTITY.rotate(Vec3.FORWARD)
        assertEquals(0f, forward.x, 1e-5f)
        assertEquals(0f, forward.y, 1e-5f)
        assertEquals(-1f, forward.z, 1e-5f)
    }

    @Test
    fun qAndNegativeQDescribeTheSameOrientation() {
        val q = Quaternion.fromYawPitchRoll(72f, 18f)
        val negated = Quaternion(-q.w, -q.x, -q.y, -q.z)
        assertEquals(0f, q.angularDistanceDegrees(negated), 1e-3f)
    }

    @Test
    fun yawPitchRoundTrip() {
        val yaw = 127f
        val pitch = -33f
        val q = Quaternion.fromYawPitchRoll(yaw, pitch)
        val euler = q.toEulerDegrees()
        assertEquals(yaw, euler.yawDeg, 0.5f)
        assertEquals(pitch, euler.pitchDeg, 0.5f)
    }

    @Test
    fun pitchDirectionsMatchYawPitchConvention() {
        val up = directionFromYawPitch(0f, 90f)
        assertEquals(1f, up.z, 1e-4f)
        val east = directionFromYawPitch(90f, 0f)
        assertEquals(1f, east.x, 1e-4f)
        assertEquals(0f, east.y, 1e-4f)
    }

    @Test
    fun slerpMidpointIsBetweenRotations() {
        val a = Quaternion.fromYawPitchRoll(0f, 0f)
        val b = Quaternion.fromYawPitchRoll(90f, 0f)
        val mid = a.slerp(b, 0.5f)
        val euler = mid.toEulerDegrees()
        assertEquals(45f, euler.yawDeg, 0.5f)
        assertTrue(a.angularDistanceDegrees(mid) > 40f)
    }

    @Test
    fun angularDistanceWrapsAcrossNorth() {
        val a = Quaternion.fromYawPitchRoll(179f, 0f)
        val b = Quaternion.fromYawPitchRoll(-179f, 0f)
        assertEquals(2f, a.angularDistanceDegrees(b), 0.2f)
        assertEquals(2f, shortestAngleDifferenceDegrees(179f, -179f), 0.001f)
        assertFalse(wrapDegrees(180.01f) > 180f)
    }
}
