package com.bbinxx.photoshere.capture

import com.bbinxx.photoshere.core.model.CaptureTarget
import com.bbinxx.photoshere.core.model.TargetStatus
import com.bbinxx.photoshere.core.model.Vec3
import com.bbinxx.photoshere.core.model.angularDistanceDegrees
import com.bbinxx.photoshere.core.model.directionFromYawPitch

/**
 * Approximates spherical coverage by sampling a lat/long grid and checking
 * whether each sample lies inside the angular footprint of a captured target.
 * This is intentionally image-independent: final coverage is re-validated
 * against the stitched equirectangular result.
 */
class CoverageTracker(
    private val horizontalFovDeg: Float,
    private val verticalFovDeg: Float,
) {

    private val captureRadiusDeg: Float =
        (minOf(horizontalFovDeg, verticalFovDeg) * 0.5f).coerceIn(15f, 45f)

    fun progress(targets: List<CaptureTarget>): Float {
        if (targets.isEmpty()) return 0f
        return targets.count { it.status == TargetStatus.CAPTURED }.toFloat() / targets.size
    }

    fun angularCoverage(targets: List<CaptureTarget>): Float {
        val captured = targets.filter { it.status == TargetStatus.CAPTURED }
        if (captured.isEmpty()) return 0f
        var covered = 0
        var total = 0
        var pitch = -88f
        while (pitch <= 88f) {
            val step = if (kotlin.math.abs(pitch) > 70f) 8f else 5f
            var yaw = -180f
            while (yaw < 180f) {
                total++
                if (isCovered(yaw, pitch, captured)) covered++
                yaw += step
            }
            pitch += step
        }
        return if (total == 0) 0f else covered.toFloat() / total
    }

    fun isComplete(targets: List<CaptureTarget>): Boolean {
        val allCaptured = targets.all { it.status == TargetStatus.CAPTURED }
        return allCaptured && angularCoverage(targets) >= 0.97f
    }

    fun missingSamples(targets: List<CaptureTarget>, limit: Int = 12): List<Vec3> {
        val captured = targets.filter { it.status == TargetStatus.CAPTURED }
        val missing = mutableListOf<Vec3>()
        var pitch = -85f
        while (pitch <= 85f && missing.size < limit) {
            var yaw = -180f
            while (yaw < 180f && missing.size < limit) {
                if (!isCovered(yaw, pitch, captured)) {
                    missing.add(directionFromYawPitch(yaw, pitch))
                }
                yaw += 10f
            }
            pitch += 10f
        }
        return missing
    }

    private fun isCovered(yawDeg: Float, pitchDeg: Float, captured: List<CaptureTarget>): Boolean {
        return captured.any {
            angularDistanceDegrees(yawDeg, pitchDeg, it.yawDeg, it.pitchDeg) <= captureRadiusDeg
        }
    }
}
