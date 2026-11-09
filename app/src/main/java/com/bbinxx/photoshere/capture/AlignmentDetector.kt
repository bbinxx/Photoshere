package com.bbinxx.photoshere.capture

import com.bbinxx.photoshere.core.model.AlignmentResult
import com.bbinxx.photoshere.core.model.CaptureTarget
import com.bbinxx.photoshere.core.model.OrientationSnapshot
import com.bbinxx.photoshere.core.model.angularDistanceDegrees
import com.bbinxx.photoshere.core.model.directionFromYawPitch
import kotlin.math.atan2
import kotlin.math.sqrt

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
        // Project the world target into the actual image frame. Euler yaw/pitch
        // subtraction points the dot in the wrong screen direction when rolled
        // into landscape, and loses direction near the poles.
        val referenceYaw = snapshot.yawDeg - snapshot.relativeYawDeg
        val worldTarget = directionFromYawPitch(referenceYaw + target.yawDeg, target.pitchDeg)
        val local = snapshot.quaternion.inverse().rotate(worldTarget)
        val yawError = Math.toDegrees(atan2(local.x.toDouble(), -local.z.toDouble())).toFloat()
        val pitchError = Math.toDegrees(atan2(local.y.toDouble(),
            sqrt((local.x * local.x + local.z * local.z).toDouble()))).toFloat()
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
