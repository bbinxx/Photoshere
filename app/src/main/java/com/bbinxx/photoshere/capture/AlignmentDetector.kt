package com.bbinxx.photoshere.capture

import com.bbinxx.photoshere.core.model.AlignmentResult
import com.bbinxx.photoshere.core.model.CaptureTarget
import com.bbinxx.photoshere.core.model.OrientationSnapshot
import com.bbinxx.photoshere.core.model.angularDistanceDegrees
import com.bbinxx.photoshere.core.model.shortestAngleDifferenceDegrees

/**
 * Compares the real camera forward direction against the current target.
 * The primary metric is the great-circle angular error, which remains valid at
 * the zenith and nadir where yaw is undefined. Yaw/pitch errors are exposed for
 * the visual guide only.
 */
class AlignmentDetector(
    var toleranceDegrees: Float = 3f,
    var requiredStableMs: Int = 600,
) {

    fun evaluate(snapshot: OrientationSnapshot, target: CaptureTarget): AlignmentResult {
        if (!snapshot.sensorAvailable) {
            return AlignmentResult(aligned = false, stable = false, angularErrorDeg = 180f)
        }
        val angularError = angularDistanceDegrees(
            snapshot.relativeYawDeg,
            snapshot.pitchDeg,
            target.yawDeg,
            target.pitchDeg,
        )
        val yawError = shortestAngleDifferenceDegrees(snapshot.relativeYawDeg, target.yawDeg)
        val pitchError = target.pitchDeg - snapshot.pitchDeg
        val aligned = angularError <= toleranceDegrees
        return AlignmentResult(
            aligned = aligned,
            stable = aligned && snapshot.stable,
            angularErrorDeg = angularError,
            yawErrorDeg = yawError,
            pitchErrorDeg = pitchError,
            stabilityProgress = (snapshot.stableForMs.toFloat() /
                requiredStableMs.coerceAtLeast(1)).coerceIn(0f, 1f),
        )
    }
}
