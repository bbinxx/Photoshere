package com.bbinxx.photoshere.capture

import com.bbinxx.photoshere.core.model.Quaternion
import org.junit.Assert.*
import org.junit.Test

class CaptureAttemptGateTest {
    @Test fun holdingOrJitteringAtTheDotDoesNotRearmAfterAnyAttempt() {
        val gate = CaptureAttemptGate(10f)
        gate.recordAttempt(Quaternion.fromYawPitchRoll(40f, 20f))
        repeat(1000) {
            gate.observe(Quaternion.fromYawPitchRoll(40f + (it % 3 - 1) * 0.3f, 20f))
            assertTrue(gate.waitingForMovement)
        }
    }

    @Test fun movingAwayThenRealigningAllowsOneRetry() {
        val gate = CaptureAttemptGate(10f)
        gate.recordAttempt(Quaternion.fromYawPitchRoll(0f, 0f))
        gate.observe(Quaternion.fromYawPitchRoll(15f, 0f))
        assertFalse(gate.waitingForMovement)
        gate.observe(Quaternion.fromYawPitchRoll(0f, 0f))
        assertFalse(gate.waitingForMovement)
        gate.recordAttempt(Quaternion.fromYawPitchRoll(0f, 0f))
        assertTrue(gate.waitingForMovement)
    }

    @Test fun rollOrPoleYawChangesDoNotPretendToCaptureANewDirection() {
        val gate = CaptureAttemptGate(10f)
        gate.recordAttempt(Quaternion.fromYawPitchRoll(0f, 0f))
        gate.observe(Quaternion.fromYawPitchRoll(0f, 0f, 90f))
        assertTrue(gate.waitingForMovement)
        gate.recordAttempt(Quaternion.fromYawPitchRoll(0f, 90f))
        gate.observe(Quaternion.fromYawPitchRoll(170f, 90f))
        assertTrue(gate.waitingForMovement)
    }

    @Test fun shutterPoseRatherThanOldTargetAnchorsTheMovementRequirement() {
        val gate = CaptureAttemptGate(10f)
        gate.recordAttempt(Quaternion.fromYawPitchRoll(0f, 0f))
        gate.recordAttempt(Quaternion.fromYawPitchRoll(30f, 0f))
        gate.observe(Quaternion.fromYawPitchRoll(30f, 0f))
        assertTrue(gate.waitingForMovement)
        gate.observe(Quaternion.fromYawPitchRoll(45f, 0f))
        assertFalse(gate.waitingForMovement)
    }
}
