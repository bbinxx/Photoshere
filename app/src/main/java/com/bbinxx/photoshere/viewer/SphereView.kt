package com.bbinxx.photoshere.viewer

import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.bbinxx.photoshere.core.model.Quaternion
import com.bbinxx.photoshere.core.model.Vec3
import kotlin.math.asin
import kotlin.math.atan2

/**
 * Interactive equirectangular sphere viewer with drag, pinch, double-tap reset
 * and an optional gyroscope mode driven by the rotation-vector sensor.
 */
class SphereView(context: Context) : GLSurfaceView(context), SensorEventListener {

    private val renderer = SphereRenderer()
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    private var gyroEnabled = false
    private var sensorRegistered = false

    @Volatile
    private var baseQuaternion: Quaternion? = null

    @Volatile
    private var latestQuaternion: Quaternion? = null

    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next = renderer.targetFovDeg / detector.scaleFactor
                renderer.targetFovDeg = next.coerceIn(35f, 100f)
                return true
            }
        },
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                resetView()
                return true
            }

            override fun onDown(e: MotionEvent): Boolean = true
        },
    )

    init {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        isClickable = true
        isFocusable = true
    }

    fun load(bitmap: Bitmap) {
        renderer.setImage(bitmap)
    }

    fun resetView() {
        renderer.reset()
        baseQuaternion = latestQuaternion
    }

    fun hasGyroscope(): Boolean = rotationSensor != null

    fun setGyroscopeEnabled(enabled: Boolean): Boolean {
        if (enabled && rotationSensor == null) return false
        gyroEnabled = enabled
        if (enabled) {
            baseQuaternion = latestQuaternion
            registerSensor()
        } else {
            unregisterSensor()
        }
        return true
    }

    fun isGyroscopeEnabled(): Boolean = gyroEnabled

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!gyroEnabled) {
                        val dx = event.x - lastX
                        val dy = event.y - lastY
                        renderer.targetYawDeg -= dx * DRAG_SENSITIVITY
                        renderer.targetPitchDeg =
                            (renderer.targetPitchDeg + dy * DRAG_SENSITIVITY).coerceIn(-88f, 88f)
                        lastX = event.x
                        lastY = event.y
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    performClick()
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!gyroEnabled) return
        val q = Quaternion.fromRotationVector(event.values[0], event.values[1], event.values[2])
            .normalized()
        latestQuaternion = q
        val base = baseQuaternion ?: run {
            baseQuaternion = q
            return
        }
        val relative = (base.inverse() * q).normalized()
        val forward = relative.rotate(Vec3(0f, 0f, -1f))
        val yaw = Math.toDegrees(
            atan2(forward.x.toDouble(), -forward.z.toDouble()),
        ).toFloat()
        val pitch = Math.toDegrees(asin(forward.y.coerceIn(-1f, 1f).toDouble())).toFloat()
        renderer.targetYawDeg = yaw
        renderer.targetPitchDeg = pitch.coerceIn(-88f, 88f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onResume() {
        super.onResume()
        if (gyroEnabled) registerSensor()
    }

    override fun onPause() {
        unregisterSensor()
        super.onPause()
    }

    private fun registerSensor() {
        if (sensorRegistered) return
        val sensor = rotationSensor ?: return
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        sensorRegistered = true
    }

    private fun unregisterSensor() {
        if (!sensorRegistered) return
        sensorManager.unregisterListener(this)
        sensorRegistered = false
    }

    private companion object {
        const val DRAG_SENSITIVITY = 0.14f
    }
}
