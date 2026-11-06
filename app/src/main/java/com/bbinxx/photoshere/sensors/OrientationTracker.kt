package com.bbinxx.photoshere.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.bbinxx.photoshere.core.model.EulerAngles
import com.bbinxx.photoshere.core.model.OrientationSnapshot
import com.bbinxx.photoshere.core.model.Quaternion
import com.bbinxx.photoshere.core.model.Vec3
import com.bbinxx.photoshere.core.model.wrapDegrees
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Fuses the Android rotation vector, gyroscope and accelerometer.
 *
 * The rotation vector is already a sensor-fusion output (accelerometer,
 * gyroscope and magnetometer) expressed as a rotation from the device frame to
 * the Earth frame. We smooth it with quaternion slerp, derive yaw/pitch from the
 * rear-camera forward direction (device -Z), and use raw gyroscope magnitude for
 * stability detection. Euler angles are never used internally for tracking.
 */
class OrientationTracker(context: Context) {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val stabilityDetector = StabilityDetector()
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var running = false

    private val mutableState = MutableStateFlow(OrientationSnapshot())
    val state: StateFlow<OrientationSnapshot> = mutableState.asStateFlow()

    @Volatile
    private var referenceQuaternion: Quaternion? = null

    @Volatile
    private var referenceYawDeg: Float? = null

    private var smoothedQuaternion = Quaternion.IDENTITY
    private var gyroMagnitude = 0f
    private var accelerometerMagnitude = 0f
    private var lastAccelerometerMagnitude = 0f
    private var linearMotion = 0f
    private var hasRotationVector = false
    private var fallbackReady = false
    private val gravityValues = FloatArray(3)
    private val magneticValues = FloatArray(3)
    private val rotationMatrix = FloatArray(9)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR,
                Sensor.TYPE_GAME_ROTATION_VECTOR,
                -> handleRotationVector(event)

                Sensor.TYPE_GYROSCOPE -> {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    gyroMagnitude = sqrt(x * x + y * y + z * z)
                }

                Sensor.TYPE_ACCELEROMETER -> {
                    gravityValues[0] = event.values[0]
                    gravityValues[1] = event.values[1]
                    gravityValues[2] = event.values[2]
                    accelerometerMagnitude = sqrt(
                        gravityValues[0] * gravityValues[0] +
                            gravityValues[1] * gravityValues[1] +
                            gravityValues[2] * gravityValues[2],
                    )
                    linearMotion = kotlin.math.abs(accelerometerMagnitude - lastAccelerometerMagnitude)
                    lastAccelerometerMagnitude = accelerometerMagnitude
                }

                Sensor.TYPE_MAGNETIC_FIELD -> {
                    magneticValues[0] = event.values[0]
                    magneticValues[1] = event.values[1]
                    magneticValues[2] = event.values[2]
                }
            }
            publish(event.timestamp)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        if (running) return
        running = true
        handlerThread = HandlerThread("photo-sphere-orientation").also { it.start() }
        handler = Handler(handlerThread!!.looper)
        hasRotationVector = false

        val rotationRegistered = rotationSensor?.let {
            sensorManager.registerListener(listener, it, SENSOR_INTERVAL_US, handler)
        } ?: false
        if (!rotationRegistered) {
            Log.w(TAG, "No rotation vector sensor; orientation guidance will be degraded")
        }
        gyroscope?.let { sensorManager.registerListener(listener, it, SENSOR_INTERVAL_US, handler) }
        accelerometer?.let { sensorManager.registerListener(listener, it, SENSOR_INTERVAL_US, handler) }
        magnetometer?.let { sensorManager.registerListener(listener, it, SENSOR_INTERVAL_US, handler) }

        if (rotationSensor == null && accelerometer != null) {
            // Degraded fallback: derive pitch/roll only. Yaw is intentionally not
            // invented from accelerometer data; the user is warned in the UI.
            Log.w(TAG, "Falling back to accelerometer-only pitch/roll")
        }
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager.unregisterListener(listener)
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
    }

    fun setReference(quaternion: Quaternion) {
        val euler = quaternion.normalized().toEulerDegrees()
        referenceQuaternion = quaternion.normalized()
        referenceYawDeg = euler.yawDeg
        publish(System.nanoTime())
        Log.d(TAG, "Reference set: yaw=${euler.yawDeg}")
    }

    fun clearReference() {
        referenceQuaternion = null
        referenceYawDeg = null
    }

    fun latestQuaternion(): Quaternion = smoothedQuaternion

    fun reference(): Quaternion? = referenceQuaternion

    fun referenceYawDeg(): Float? = referenceYawDeg

    fun updateStabilityConfiguration(minAngularSpeedRadPerSec: Float, requiredStableMs: Int) {
        stabilityDetector.minAngularSpeedRadPerSec = minAngularSpeedRadPerSec
        stabilityDetector.requiredStableMs = requiredStableMs.toLong()
    }

    fun resetStability() {
        stabilityDetector.reset()
    }

    private fun handleRotationVector(event: SensorEvent) {
        val q = if (event.values.size >= 4 && event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            // Some devices provide a 4th heading-accuracy element; it is not used.
            Quaternion.fromRotationVector(event.values[0], event.values[1], event.values[2])
        } else {
            Quaternion.fromRotationVector(event.values[0], event.values[1], event.values[2])
        }
        hasRotationVector = true
        smoothedQuaternion = if (smoothedQuaternion == Quaternion.IDENTITY) {
            q
        } else {
            smoothedQuaternion.slerp(q, SMOOTHING_FACTOR)
        }
    }

    private fun publish(timestampNanos: Long) {
        val quaternion = when {
            hasRotationVector -> smoothedQuaternion
            else -> accelerometerFallbackQuaternion()
        }
        val euler: EulerAngles = quaternion.toEulerDegrees()
        val referenceYaw = referenceYawDeg
        val relativeYaw = if (referenceYaw == null) 0f else wrapDegrees(euler.yawDeg - referenceYaw)
        val stability = stabilityDetector.update(
            angularSpeedRadPerSec = gyroMagnitude,
            linearMotion = linearMotion,
            timestampNanos = timestampNanos,
        )
        mutableState.value = OrientationSnapshot(
            quaternion = quaternion,
            yawDeg = euler.yawDeg,
            pitchDeg = euler.pitchDeg,
            rollDeg = euler.rollDeg,
            relativeYawDeg = relativeYaw,
            angularSpeedRadPerSec = gyroMagnitude,
            stable = stability.stable,
            stableForMs = stability.stableForMs,
            sensorAvailable = hasRotationVector || fallbackReady,
            usingFallback = !hasRotationVector && fallbackReady,
            timestampNanos = timestampNanos,
        )
    }

    /**
     * Degraded fallback using accelerometer + magnetometer. It is only used when
     * no rotation-vector sensor is available. Yaw from the magnetometer is less
     * stable, so the UI marks the session as degraded.
     */
    private fun accelerometerFallbackQuaternion(): Quaternion {
        if (rotationSensor != null) return smoothedQuaternion
        if (accelerometerMagnitude < 1e-3f || magneticValues.all { it == 0f }) {
            return smoothedQuaternion
        }
        val hasRotation = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            gravityValues,
            magneticValues,
        )
        if (!hasRotation) return smoothedQuaternion
        val deviceX = Vec3(rotationMatrix[0], rotationMatrix[3], rotationMatrix[6])
        val deviceY = Vec3(rotationMatrix[1], rotationMatrix[4], rotationMatrix[7])
        val deviceZ = Vec3(rotationMatrix[2], rotationMatrix[5], rotationMatrix[8])
        val quaternion = Quaternion.fromBasis(deviceX, deviceY, deviceZ)
        fallbackReady = true
        smoothedQuaternion = quaternion
        return quaternion
    }

    private companion object {
        const val TAG = "OrientationTracker"

        // ~120 Hz request; Android may deliver less depending on power state.
        const val SENSOR_INTERVAL_US = 8_000

        // 0.28 keeps latency low (< ~50 ms) while suppressing sensor jitter.
        const val SMOOTHING_FACTOR = 0.28f
    }
}
