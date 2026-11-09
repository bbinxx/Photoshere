package com.bbinxx.photoshere.capture

import com.bbinxx.photoshere.core.model.CaptureTarget
import com.bbinxx.photoshere.core.model.OrientationSnapshot
import com.bbinxx.photoshere.core.model.TargetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlignmentDetectorTest {

    private val detector = AlignmentDetector(toleranceDegrees = 3f, requiredStableMs = 600)
    private val target = CaptureTarget(
        id = 0,
        yawDeg = 40f,
        pitchDeg = 10f,
        kind = TargetKind.HORIZON,
    )

    @Test
    fun guideUsesImageAxesWhenPhoneIsRolled() {
        val result = detector.evaluate(
            OrientationSnapshot(
                quaternion = com.bbinxx.photoshere.core.model.Quaternion.fromYawPitchRoll(0f, 0f, 90f),
                yawDeg = 0f, relativeYawDeg = 0f, pitchDeg = 0f, sensorAvailable = true,
            ),
            CaptureTarget(1, 10f, 0f, TargetKind.HORIZON),
        )
        assertEquals(0f, result.yawErrorDeg, 0.01f)
        assertEquals(10f, result.pitchErrorDeg, 0.01f)
    }

    @Test
    fun alignedAndStableWhenOrientationMatches() {
        val result = detector.evaluate(
            snapshot = OrientationSnapshot(
                relativeYawDeg = 40f,
                pitchDeg = 10f,
                stable = true,
                stableForMs = 900,
                sensorAvailable = true,
            ),
            target = target,
        )
        assertTrue(result.aligned)
        assertTrue(result.stable)
        assertEquals(1f, result.stabilityProgress, 1e-4f)
        assertEquals(0f, result.angularErrorDeg, 0.1f)
    }

    @Test
    fun outsideToleranceIsNotAligned() {
        val result = detector.evaluate(
            snapshot = OrientationSnapshot(
                relativeYawDeg = 50f,
                pitchDeg = 10f,
                stable = true,
                stableForMs = 900,
                sensorAvailable = true,
            ),
            target = target,
        )
        assertFalse(result.aligned)
        assertFalse(result.stable)
        assertTrue(result.angularErrorDeg > 9f)
    }

    @Test
    fun alignedButMovingIsNotStable() {
        val result = detector.evaluate(
            snapshot = OrientationSnapshot(
                relativeYawDeg = 40f,
                pitchDeg = 10f,
                stable = false,
                stableForMs = 200,
                sensorAvailable = true,
            ),
            target = target,
        )
        assertTrue(result.aligned)
        assertFalse(result.stable)
        assertEquals(200f / 600f, result.stabilityProgress, 1e-3f)
    }

    @Test
    fun unavailableSensorNeverAligns() {
        val result = detector.evaluate(
            snapshot = OrientationSnapshot(
                relativeYawDeg = 40f,
                pitchDeg = 10f,
                stable = true,
                sensorAvailable = false,
            ),
            target = target,
        )
        assertFalse(result.aligned)
        assertEquals(180f, result.angularErrorDeg, 0f)
    }
}
