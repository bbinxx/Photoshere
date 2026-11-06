package com.bbinxx.photoshere.core.model

import kotlin.math.sqrt

/**
 * Immutable 3D vector in a right-handed coordinate system.
 *
 * World frame convention used by PhotoSphere: X = east, Y = north, Z = up.
 * Android device frame: X = right, Y = up (top of screen), Z = out of the screen.
 * The rear camera looks down device -Z.
 */
data class Vec3(val x: Float, val y: Float, val z: Float) {

    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)

    operator fun times(scale: Float) = Vec3(x * scale, y * scale, z * scale)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3) = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    fun lengthSquared(): Float = x * x + y * y + z * z

    fun length(): Float = sqrt(lengthSquared())

    fun normalized(): Vec3 {
        val length = length()
        if (length < 1e-8f) return ZERO
        return Vec3(x / length, y / length, z / length)
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val UP = Vec3(0f, 0f, 1f)
        val FORWARD = Vec3(0f, 0f, -1f)
    }
}
