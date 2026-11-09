package com.bbinxx.photoshere.core.model

import kotlin.math.atan
import kotlin.math.tan

/** Rectilinear intrinsics after centred sensor cropping and JPEG rotation. */
object CameraGeometry {
    fun fieldOfView(sensorWidth: Float, sensorHeight: Float, focal: Float,
        bufferAspect: Float, rotationDegrees: Int): Pair<Float, Float> {
        require(sensorWidth > 0 && sensorHeight > 0 && focal > 0 && bufferAspect > 0)
        val width = minOf(sensorWidth, sensorHeight * bufferAspect)
        val height = minOf(sensorHeight, sensorWidth / bufferAspect)
        val h = Math.toDegrees(2 * atan(width / (2.0 * focal))).toFloat()
        val v = Math.toDegrees(2 * atan(height / (2.0 * focal))).toFloat()
        return if (rotationDegrees % 180 != 0) v to h else h to v
    }

    /** Target direction in image coordinates. Null means behind the camera. */
    fun project(local: Vec3, horizontalFov: Float, verticalFov: Float): Pair<Float, Float>? {
        if (local.z >= -0.0001f) return null
        val fx = tan(Math.toRadians(horizontalFov.toDouble()) / 2).toFloat()
        val fy = tan(Math.toRadians(verticalFov.toDouble()) / 2).toFloat()
        return (0.5f + local.x / (-local.z * fx) * 0.5f) to
            (0.5f - local.y / (-local.z * fy) * 0.5f)
    }
}
