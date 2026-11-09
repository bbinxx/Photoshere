package com.bbinxx.photoshere.capture

import com.bbinxx.photoshere.core.model.*
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.acos
import kotlin.math.tan

/** Equal-area sphere samples, plus explicit poles. Coverage comes from accepted
 * camera frusta, including each JPEG's orientation and field of view. Target
 * order and photo count never substitute for observed spherical coverage.
 */
class SphericalCoverage(private val referenceYawDeg: Float) {
    private val samples = buildList {
        for (row in 0 until 36) {
            val pitch = Math.toDegrees(asin(-1.0 + (row + 0.5) * 2 / 36)).toFloat()
            for (column in 0 until 72) add((column * 5f) to pitch)
        }
        add(0f to 90f)
        add(0f to -90f)
    }
    private val rays = samples.map { directionFromYawPitch(it.first + referenceYawDeg, it.second) }
    private val covered = BooleanArray(rays.size)
    private val frames = mutableListOf<Frame>()
    private data class Frame(val inverse: Quaternion, val halfWidth: Float, val halfHeight: Float,
        val forward: Vec3, val minimumSpacingDeg: Float)

    val fraction: Float get() {
        val area = covered.take(covered.size - 2).count { it }.toFloat() / (covered.size - 2)
        return if (complete) 1f else area.coerceAtMost(0.99f)
    }
    val complete: Boolean get() = covered.all { it }

    fun record(image: CapturedImageMeta) {
        if (!image.accepted || image.quaternion.size != 4) return
        val frame = frame(Quaternion.fromFloatArray(image.quaternion.toFloatArray()),
            image.horizontalFovDeg, image.verticalFovDeg)
        frames += frame
        for (i in rays.indices) if (!covered[i] && contains(frame, rays[i])) covered[i] = true
    }

    fun addsCoverage(pose: Quaternion, horizontalFov: Float, verticalFov: Float): Boolean {
        val frame = frame(pose, horizontalFov, verticalFov)
        var footprint = 0
        var added = 0
        for (i in rays.indices) {
            if (contains(frame, rays[i])) {
                footprint++
                if (!covered[i]) added++
            }
        }
        if (added == 0) return false
        // A new viewing direction may fill even a tiny final gap. Near-identical
        // directions must add substantial area, so edge jitter cannot retrigger.
        val nearlyDuplicate = frames.any {
            val angle = Math.toDegrees(acos(it.forward.dot(frame.forward).coerceIn(-1f, 1f).toDouble()))
            angle < minOf(it.minimumSpacingDeg, frame.minimumSpacingDeg)
        }
        return !nearlyDuplicate || added >= footprint * 0.15f
    }

    fun contains(yaw: Float, pitch: Float): Boolean {
        val ray = directionFromYawPitch(yaw + referenceYawDeg, pitch)
        return frames.any { contains(it, ray) }
    }

    fun nearestGap(yaw: Float, pitch: Float): Pair<Float, Float>? = samples.indices
        .asSequence().filter { !covered[it] }
        .minByOrNull { angularDistanceDegrees(yaw, pitch, samples[it].first, samples[it].second) }
        ?.let { samples[it] }

    private fun frame(pose: Quaternion, h: Float, v: Float): Frame = Frame(
        pose.inverse(),
        tan(Math.toRadians(h.coerceIn(20f, 150f).toDouble()) / 2).toFloat() * 0.96f,
        tan(Math.toRadians(v.coerceIn(20f, 150f).toDouble()) / 2).toFloat() * 0.96f,
        pose.normalized().rotate(Vec3.FORWARD),
        (minOf(h, v) * 0.2f).coerceIn(6f, 18f),
    )

    private fun contains(frame: Frame, ray: Vec3): Boolean {
        val local = frame.inverse.rotate(ray)
        return local.z < 0 && abs(local.x) < -local.z * frame.halfWidth &&
            abs(local.y) < -local.z * frame.halfHeight
    }
}
