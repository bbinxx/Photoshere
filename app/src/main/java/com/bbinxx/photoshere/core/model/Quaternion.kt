package com.bbinxx.photoshere.core.model

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Unit quaternion used for all orientation maths.
 *
 * Quaternions avoid the gimbal-lock and wrap-around problems that Euler angles
 * suffer from. Angular differences are computed from the absolute dot product,
 * which correctly treats q and -q as the same rotation.
 */
data class Quaternion(val w: Float, val x: Float, val y: Float, val z: Float) {

    fun norm(): Float = sqrt(w * w + x * x + y * y + z * z)

    fun normalized(): Quaternion {
        val n = norm()
        if (n < 1e-8f) return IDENTITY
        return Quaternion(w / n, x / n, y / n, z / n)
    }

    fun conjugate() = Quaternion(w, -x, -y, -z)

    fun inverse(): Quaternion {
        val n2 = w * w + x * x + y * y + z * z
        if (n2 < 1e-8f) return IDENTITY
        return Quaternion(w / n2, -x / n2, -y / n2, -z / n2)
    }

    /** Hamilton product. this * other means: apply [other] first, then [this]. */
    operator fun times(other: Quaternion): Quaternion {
        val aw = w
        val ax = x
        val ay = y
        val az = z
        val bw = other.w
        val bx = other.x
        val by = other.y
        val bz = other.z
        return Quaternion(
            aw * bw - ax * bx - ay * by - az * bz,
            aw * bx + ax * bw + ay * bz - az * by,
            aw * by - ax * bz + ay * bw + az * bx,
            aw * bz + ax * by - ay * bx + az * bw,
        )
    }

    fun dot(other: Quaternion): Float = w * other.w + x * other.x + y * other.y + z * other.z

    /** Rotates a vector from the local frame into the frame described by this quaternion. */
    fun rotate(v: Vec3): Vec3 {
        val qv = Quaternion(0f, v.x, v.y, v.z)
        val rotated = this * qv * conjugate()
        return Vec3(rotated.x, rotated.y, rotated.z)
    }

    /** Shortest angular distance in degrees between two orientations. */
    fun angularDistanceDegrees(other: Quaternion): Float {
        val d = abs(normalized().dot(other.normalized())).coerceIn(0f, 1f)
        return (2.0 * acos(d.toDouble()) * 180.0 / Math.PI).toFloat()
    }

    fun slerp(other: Quaternion, t: Float): Quaternion {
        var target = other
        var cosHalfTheta = dot(target)
        if (cosHalfTheta < 0f) {
            target = Quaternion(-target.w, -target.x, -target.y, -target.z)
            cosHalfTheta = -cosHalfTheta
        }
        if (cosHalfTheta > 0.9995f) {
            return Quaternion(
                w + t * (target.w - w),
                x + t * (target.x - x),
                y + t * (target.y - y),
                z + t * (target.z - z),
            ).normalized()
        }
        val halfTheta = acos(cosHalfTheta.coerceIn(-1f, 1f))
        val sinHalfTheta = sin(halfTheta)
        if (abs(sinHalfTheta) < 1e-6f) return this
        val ratioA = sin((1f - t) * halfTheta) / sinHalfTheta
        val ratioB = sin(t * halfTheta) / sinHalfTheta
        return Quaternion(
            w * ratioA + target.w * ratioB,
            x * ratioA + target.x * ratioB,
            y * ratioA + target.y * ratioB,
            z * ratioA + target.z * ratioB,
        ).normalized()
    }

    fun toEulerDegrees(): EulerAngles {
        val forward = rotate(Vec3.FORWARD)
        val (yaw, pitch) = yawPitchFromDirection(forward)
        val right = rotate(Vec3(1f, 0f, 0f))
        val levelRight = directionFromYawPitch(yaw + 90f, 0f)
        val levelUp = levelRight.cross(forward).normalized()
        val roll = Math.toDegrees(atan2(-right.dot(levelUp), right.dot(levelRight)).toDouble()).toFloat()
        return EulerAngles(yaw, pitch, roll)
    }

    fun toFloatArray(): FloatArray = floatArrayOf(w, x, y, z)

    companion object {
        val IDENTITY = Quaternion(1f, 0f, 0f, 0f)

        fun fromAxisAngle(axis: Vec3, angleRadians: Float): Quaternion {
            val n = axis.normalized()
            val half = angleRadians / 2f
            val s = sin(half)
            return Quaternion(cos(half), n.x * s, n.y * s, n.z * s)
        }

        /** Android reports (axis * sin(angle/2), cos(angle/2)), not axis-angle. */
        fun fromRotationVector(rx: Float, ry: Float, rz: Float, scalar: Float? = null): Quaternion {
            val w = scalar ?: sqrt((1f - rx * rx - ry * ry - rz * rz).coerceAtLeast(0f))
            return Quaternion(w, rx, ry, rz).normalized()
        }

        fun fromFloatArray(values: FloatArray): Quaternion {
            if (values.size < 4) return IDENTITY
            return Quaternion(values[0], values[1], values[2], values[3]).normalized()
        }

        /**
         * Builds an orientation whose camera forward vector points at the supplied
         * yaw/pitch. Roll is applied around the forward axis. Used for target maths
         * and tests, not for device tracking.
         */
        fun fromYawPitchRoll(yawDeg: Float, pitchDeg: Float, rollDeg: Float = 0f): Quaternion {
            val direction = directionFromYawPitch(yawDeg, pitchDeg)
            val right = directionFromYawPitch(yawDeg + 90f, 0f)
            val up = right.cross(direction).normalized()
            val roll = Math.toRadians(rollDeg.toDouble()).toFloat()
            val rolledUp = up * cos(roll) + right * sin(roll)
            val rolledRight = right * cos(roll) - up * sin(roll)
            return fromBasis(rolledRight, rolledUp, direction * -1f)
        }

        /** Creates a rotation from orthonormal basis vectors (device axes in world frame). */
        fun fromBasis(deviceX: Vec3, deviceY: Vec3, deviceZ: Vec3): Quaternion {
            val m00 = deviceX.x
            val m01 = deviceY.x
            val m02 = deviceZ.x
            val m10 = deviceX.y
            val m11 = deviceY.y
            val m12 = deviceZ.y
            val m20 = deviceX.z
            val m21 = deviceY.z
            val m22 = deviceZ.z
            val trace = m00 + m11 + m22
            return when {
                trace > 0f -> {
                    val s = sqrt(trace + 1f) * 2f
                    Quaternion(0.25f * s, (m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s)
                }
                m00 > m11 && m00 > m22 -> {
                    val s = sqrt(1f + m00 - m11 - m22) * 2f
                    Quaternion((m21 - m12) / s, 0.25f * s, (m01 + m10) / s, (m02 + m20) / s)
                }
                m11 > m22 -> {
                    val s = sqrt(1f + m11 - m00 - m22) * 2f
                    Quaternion((m02 - m20) / s, (m01 + m10) / s, 0.25f * s, (m12 + m21) / s)
                }
                else -> {
                    val s = sqrt(1f + m22 - m00 - m11) * 2f
                    Quaternion((m10 - m01) / s, (m02 + m20) / s, (m12 + m21) / s, 0.25f * s)
                }
            }.normalized()
        }
    }
}

data class EulerAngles(val yawDeg: Float, val pitchDeg: Float, val rollDeg: Float)

/** Direction on the unit sphere. Yaw 0 points north, +90 points east. */
fun directionFromYawPitch(yawDeg: Float, pitchDeg: Float): Vec3 {
    val yaw = Math.toRadians(yawDeg.toDouble())
    val pitch = Math.toRadians(pitchDeg.toDouble())
    val cp = cos(pitch)
    return Vec3(
        (cp * sin(yaw)).toFloat(),
        (cp * cos(yaw)).toFloat(),
        sin(pitch).toFloat(),
    ).normalized()
}

fun yawPitchFromDirection(direction: Vec3): Pair<Float, Float> {
    val v = direction.normalized()
    val yaw = Math.toDegrees(atan2(v.x.toDouble(), v.y.toDouble())).toFloat()
    val pitch = Math.toDegrees(asin(v.z.coerceIn(-1f, 1f).toDouble())).toFloat()
    return yaw to pitch
}

fun angularDistanceDegrees(aYaw: Float, aPitch: Float, bYaw: Float, bPitch: Float): Float {
    val a = directionFromYawPitch(aYaw, aPitch)
    val b = directionFromYawPitch(bYaw, bPitch)
    val dot = a.dot(b).coerceIn(-1f, 1f)
    return Math.toDegrees(acos(dot.toDouble())).toFloat()
}

/** Wraps an angle to (-180, 180]. */
fun wrapDegrees(degrees: Float): Float {
    var value = degrees % 360f
    if (value <= -180f) value += 360f
    if (value > 180f) value -= 360f
    return value
}

fun shortestAngleDifferenceDegrees(from: Float, to: Float): Float = wrapDegrees(to - from)
