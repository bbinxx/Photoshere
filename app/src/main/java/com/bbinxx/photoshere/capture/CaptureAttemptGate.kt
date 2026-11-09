package com.bbinxx.photoshere.capture

import com.bbinxx.photoshere.core.model.Quaternion
import com.bbinxx.photoshere.core.model.Vec3
import kotlin.math.acos

/** One automatic attempt per visit, even when validation rejects a photograph.
 * Sensor jitter, target changes and cooldown expiry cannot rearm the shutter.
 * Real movement away from the attempted viewing direction is required. Explicit
 * manual retries remain possible and are still checked for useful coverage.
 */
class CaptureAttemptGate(var movementThresholdDeg: Float = 10f) {
    private var attemptedDirection: Vec3? = null
    val waitingForMovement: Boolean get() = attemptedDirection != null

    fun recordAttempt(pose: Quaternion) {
        attemptedDirection = pose.normalized().rotate(Vec3.FORWARD)
    }

    fun observe(pose: Quaternion) {
        val previous = attemptedDirection ?: return
        val direction = pose.normalized().rotate(Vec3.FORWARD)
        val angle = Math.toDegrees(acos(previous.dot(direction).coerceIn(-1f, 1f).toDouble()))
        if (angle >= movementThresholdDeg) attemptedDirection = null
    }
}
