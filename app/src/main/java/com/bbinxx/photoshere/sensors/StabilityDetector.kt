package com.bbinxx.photoshere.sensors

/**
 * A capture is only accepted after the device has stayed below the angular
 * velocity threshold for a continuous period. The timer is reset by any
 * movement, so a single stable instant can never trigger auto-capture.
 */
class StabilityDetector {

    var minAngularSpeedRadPerSec: Float = 0.08f
    var requiredStableMs: Long = 600L

    private var stableSinceNanos: Long = -1L
    private var lastTimestampNanos: Long = -1L
    private var lastLinearMotion: Float = 0f

    fun update(angularSpeedRadPerSec: Float, linearMotion: Float, timestampNanos: Long): StabilitySample {
        if (lastTimestampNanos > 0 && timestampNanos > lastTimestampNanos) {
            val dtSeconds = (timestampNanos - lastTimestampNanos) / 1_000_000_000f
            if (dtSeconds > 0.5f) {
                // Sensor stream was interrupted; do not treat the gap as stable.
                stableSinceNanos = -1L
            }
        }
        lastTimestampNanos = timestampNanos
        lastLinearMotion = linearMotion

        val moving = angularSpeedRadPerSec > minAngularSpeedRadPerSec || linearMotion > 0.45f
        if (moving) {
            stableSinceNanos = -1L
        } else if (stableSinceNanos < 0L) {
            stableSinceNanos = timestampNanos
        }

        val stableForMs = if (stableSinceNanos < 0L) {
            0L
        } else {
            (timestampNanos - stableSinceNanos) / 1_000_000L
        }
        val progress = (stableForMs.toFloat() / requiredStableMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
        return StabilitySample(
            stable = stableForMs >= requiredStableMs,
            stableForMs = stableForMs,
            progress = progress,
        )
    }

    fun reset() {
        stableSinceNanos = -1L
        lastTimestampNanos = -1L
        lastLinearMotion = 0f
    }
}

data class StabilitySample(
    val stable: Boolean,
    val stableForMs: Long,
    val progress: Float,
)
