package com.bbinxx.photoshere.capture

import com.bbinxx.photoshere.core.model.CaptureTarget
import com.bbinxx.photoshere.core.model.TargetKind
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos

/**
 * Builds a full-sphere capture plan from the camera's actual field of view.
 *
 * Geometry:
 *  - Vertical row spacing = verticalFov * (1 - overlap).
 *  - At latitude p, one image spans roughly horizontalFov / cos(p) degrees of
 *    longitude, so the number of images in that ring is
 *    ceil(360 * cos(p) / (horizontalFov * (1 - overlap))).
 *  - Dedicated zenith and nadir targets are always added.
 *
 * The plan therefore scales with the lens instead of hardcoding a count.
 */
object CapturePlanner {

    fun plan(
        horizontalFovDeg: Float,
        verticalFovDeg: Float,
        overlapPercent: Int,
        densityMultiplier: Float = 1f,
    ): List<CaptureTarget> {
        val hFov = horizontalFovDeg.coerceIn(25f, 120f)
        val vFov = verticalFovDeg.coerceIn(20f, 100f)
        val overlap = (overlapPercent.coerceIn(10, 70) / 100f)
        val density = densityMultiplier.coerceIn(0.6f, 2.0f)

        val verticalStep = (vFov * (1f - overlap)).coerceIn(12f, 55f)
        val horizontalStep = ((hFov * (1f - overlap)) / density).coerceAtLeast(12f)

        val pitches = mutableListOf<Float>()
        pitches.add(0f)

        var up = verticalStep
        while (up < 80f) {
            pitches.add(up)
            up += verticalStep
        }
        pitches.add(90f)

        var down = -verticalStep
        while (down > -80f) {
            pitches.add(down)
            down -= verticalStep
        }
        pitches.add(-90f)

        val targets = mutableListOf<CaptureTarget>()
        var id = 0
        pitches.forEach { pitch ->
            val kind = when {
                pitch >= 85f -> TargetKind.ZENITH
                pitch <= -85f -> TargetKind.NADIR
                pitch > 1f -> TargetKind.UPPER
                pitch < -1f -> TargetKind.LOWER
                else -> TargetKind.HORIZON
            }
            if (kind == TargetKind.ZENITH || kind == TargetKind.NADIR) {
                targets.add(
                    CaptureTarget(
                        id = id++,
                        yawDeg = 0f,
                        pitchDeg = if (kind == TargetKind.ZENITH) 88f else -88f,
                        kind = kind,
                    ),
                )
            } else {
                val count = ringCount(pitch, horizontalStep)
                val offset = if (abs(pitch) < 1f) 0f else (360f / count) / 2f
                for (i in 0 until count) {
                    targets.add(
                        CaptureTarget(
                            id = id++,
                            yawDeg = normalizeYaw(offset + i * 360f / count),
                            pitchDeg = pitch,
                            kind = kind,
                        ),
                    )
                }
            }
        }
        return targets
    }

    private fun ringCount(pitchDeg: Float, horizontalStepDeg: Float): Int {
        val latitudeScale = cos(Math.toRadians(pitchDeg.toDouble())).toFloat().coerceAtLeast(0.12f)
        val raw = ceil(360.0 * latitudeScale / horizontalStepDeg).toInt()
        return raw.coerceIn(2, 36)
    }

    private fun normalizeYaw(yaw: Float): Float {
        var value = yaw % 360f
        if (value < 0f) value += 360f
        if (value >= 360f) value -= 360f
        return value
    }
}
