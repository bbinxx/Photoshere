package com.bbinxx.photoshere.core.model

enum class CaptureState {
    IDLE,
    INITIALIZING_CAMERA,
    CALIBRATING,
    READY,
    SEARCHING_ALIGNMENT,
    ALIGNED,
    WAITING_FOR_STABILITY,
    CAPTURING,
    VALIDATING_IMAGE,
    CAPTURE_COMPLETED,
    MOVING_TO_NEXT_TARGET,
    COVERAGE_CHECK,
    STITCHING,
    EXPORTING,
    COMPLETED,
    PAUSED,
    CANCELLED,
    ERROR,
}

enum class TargetKind {
    HORIZON,
    UPPER,
    ZENITH,
    LOWER,
    NADIR,
}

enum class TargetStatus {
    PENDING,
    CURRENT,
    ALIGNED,
    CAPTURING,
    CAPTURED,
    FAILED,
}

enum class FlashSetting {
    OFF,
    AUTO,
    ON,
    TORCH,
}

enum class CaptureMode {
    AUTO,
    MANUAL,
}

data class CaptureTarget(
    val id: Int,
    val yawDeg: Float,
    val pitchDeg: Float,
    val kind: TargetKind,
    val status: TargetStatus = TargetStatus.PENDING,
)

data class CaptureSettings(
    val autoCapture: Boolean = true,
    val alignmentToleranceDeg: Float = 3.0f,
    val stabilityMs: Int = 600,
    val minAngularSpeedRadPerSec: Float = 0.08f,
    val captureDelayMs: Int = 250,
    val overlapPercent: Int = 35,
    val densityMultiplier: Float = 1.0f,
    val horizontalFovDeg: Float = 65f,
    val verticalFovDeg: Float = 50f,
    val outputWidth: Int = 3072,
    val outputHeight: Int = 1536,
    val jpegQuality: Int = 92,
    val processingQuality: Float = 1.0f,
    val exposureLock: Boolean = true,
    val focusLock: Boolean = true,
    val hapticFeedback: Boolean = true,
    val soundFeedback: Boolean = true,
    val saveOriginals: Boolean = true,
    val autoStitch: Boolean = true,
    val autoExport: Boolean = false,
    val useGyroscopeGuidance: Boolean = true,
) {
    val outputHeightForWidth: Int
        get() = maxOf(64, (outputWidth / 2f).toInt())
}

data class OrientationSnapshot(
    val quaternion: Quaternion = Quaternion.IDENTITY,
    val yawDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val relativeYawDeg: Float = 0f,
    val angularSpeedRadPerSec: Float = 0f,
    val stable: Boolean = false,
    val stableForMs: Long = 0L,
    val sensorAvailable: Boolean = false,
    val usingFallback: Boolean = false,
    val timestampNanos: Long = 0L,
) {
    val forwardDirection: Vec3
        get() = quaternion.rotate(Vec3.FORWARD)

    fun directionRelativeTo(reference: Quaternion): Vec3 {
        return (reference.inverse() * quaternion).rotate(Vec3.FORWARD)
    }
}

data class AlignmentResult(
    val aligned: Boolean = false,
    val stable: Boolean = false,
    val angularErrorDeg: Float = 180f,
    val yawErrorDeg: Float = 0f,
    val pitchErrorDeg: Float = 0f,
    val stabilityProgress: Float = 0f,
)

data class CapturedImageMeta(
    val targetId: Int,
    val filePath: String,
    val quaternion: List<Float>,
    val yawDeg: Float,
    val pitchDeg: Float,
    val timestampMs: Long,
    val sharpness: Float = 0f,
    val meanLuma: Float = 0f,
    val accepted: Boolean = true,
    val note: String? = null,
    val horizontalFovDeg: Float = 0f,
    val verticalFovDeg: Float = 0f,
)

data class SessionMetadata(
    val id: String,
    val createdAtMs: Long,
    val referenceQuaternion: List<Float>,
    val referenceYawDeg: Float,
    val horizontalFovDeg: Float,
    val verticalFovDeg: Float,
    val targets: List<CaptureTarget>,
    val images: List<CapturedImageMeta>,
    val completed: Boolean = false,
    val cameraId: String? = null,
)

data class CameraLens(
    val id: String,
    val label: String,
    val isFront: Boolean,
    val focalLengthMm: Float? = null,
    val horizontalFovDeg: Float = 65f,
    val verticalFovDeg: Float = 50f,
)

data class SphereMetadata(
    val width: Int,
    val height: Int,
    val fullPanoWidth: Int = width,
    val fullPanoHeight: Int = height,
    val croppedAreaLeft: Int = 0,
    val croppedAreaTop: Int = 0,
    val croppedAreaWidth: Int = width,
    val croppedAreaHeight: Int = height,
    val poseHeadingDeg: Float = 0f,
    val posePitchDeg: Float = 0f,
    val poseRollDeg: Float = 0f,
    val projectionType: String = "equirectangular",
    val usePanoramaViewer: Boolean = true,
)

data class QualityReport(
    val sharpness: Float,
    val meanLuma: Float,
    val acceptable: Boolean,
    val reason: String? = null,
)

data class ProcessingProgress(
    val fraction: Float = 0f,
    val stage: String = "",
    val cancelled: Boolean = false,
)
