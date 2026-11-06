package com.bbinxx.photoshere.capture

import android.app.Application
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bbinxx.photoshere.PhotoSphereApplication
import com.bbinxx.photoshere.camera.CameraState
import com.bbinxx.photoshere.camera.CameraXController
import com.bbinxx.photoshere.core.model.AlignmentResult
import com.bbinxx.photoshere.core.model.CaptureSettings
import com.bbinxx.photoshere.core.model.CaptureState
import com.bbinxx.photoshere.core.model.CaptureTarget
import com.bbinxx.photoshere.core.model.CapturedImageMeta
import com.bbinxx.photoshere.core.model.FlashSetting
import com.bbinxx.photoshere.core.model.OrientationSnapshot
import com.bbinxx.photoshere.core.model.Quaternion
import com.bbinxx.photoshere.core.model.SessionMetadata
import com.bbinxx.photoshere.core.model.TargetKind
import com.bbinxx.photoshere.core.model.TargetStatus
import com.bbinxx.photoshere.sensors.OrientationTracker
import com.bbinxx.photoshere.stitching.ImageQualityValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class CaptureUiState(
    val state: CaptureState = CaptureState.IDLE,
    val targets: List<CaptureTarget> = emptyList(),
    val currentTarget: CaptureTarget? = null,
    val capturedCount: Int = 0,
    val totalCount: Int = 0,
    val orientation: OrientationSnapshot = OrientationSnapshot(),
    val alignment: AlignmentResult = AlignmentResult(),
    val camera: CameraState = CameraState(),
    val sessionId: String? = null,
    val instruction: String = "Starting camera…",
    val autoCapture: Boolean = true,
    val sensorDegraded: Boolean = false,
    val canSwitchLens: Boolean = true,
    val error: String? = null,
    val notice: String? = null,
    val completedSessionId: String? = null,
    val coverage: Float = 0f,
    val hapticFeedback: Boolean = true,
    val soundFeedback: Boolean = true,
)

sealed interface CaptureEvent {
    data object Shutter : CaptureEvent
    data object Success : CaptureEvent
    data object Failure : CaptureEvent
    data object Completed : CaptureEvent
}

class CaptureViewModel(
    application: Application,
    private val resumeSessionId: String? = null,
) : AndroidViewModel(application) {

    private val app = application as PhotoSphereApplication
    private val container = app.container
    private val settingsRepository = container.settings
    private val sessionStore = container.sessionStore

    val cameraController: CameraXController = CameraXController(application)
    private val orientationTracker = OrientationTracker(application)
    private val qualityValidator = ImageQualityValidator()

    private val mutableState = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<CaptureEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<CaptureEvent> = mutableEvents.asSharedFlow()

    private var session: SessionMetadata? = null
    private var targets: List<CaptureTarget> = emptyList()
    private var currentTargetIndex = 0
    private var resumeLoading = resumeSessionId != null
    private var captureInProgress = false
    private var lastCaptureAtMs = 0L
    private var alignedSinceMs = 0L
    private var sessionCreated = false
    private var manualCaptureRequested = false
    private var orientationJob: Job? = null
    private var cameraJob: Job? = null

    private val alignmentDetector = AlignmentDetector()
    private var coverageTracker = CoverageTracker(65f, 50f)

    init {
        val settings = settingsRepository.current
        applySettings(settings)
        cameraController.setCaptureLocks(settings.exposureLock, settings.focusLock)
        orientationTracker.start()
        orientationTracker.updateStabilityConfiguration(
            settings.minAngularSpeedRadPerSec,
            settings.stabilityMs,
        )
        observeOrientation()
        observeCamera()

        viewModelScope.launch {
            settingsRepository.settings.collect { latest ->
                applySettings(latest)
            }
        }

        if (resumeSessionId != null) {
            viewModelScope.launch {
                val loaded = withContext(Dispatchers.IO) { sessionStore.load(resumeSessionId) }
                if (loaded == null) {
                    resumeLoading = false
                    mutableState.value = mutableState.value.copy(
                        state = CaptureState.ERROR,
                        error = "Saved capture session could not be found",
                    )
                    return@launch
                }
                session = loaded
                targets = loaded.targets
                sessionCreated = true
                currentTargetIndex = targets.indexOfFirst { it.status != TargetStatus.CAPTURED }
                    .takeIf { it >= 0 } ?: targets.lastIndex
                coverageTracker = CoverageTracker(loaded.horizontalFovDeg, loaded.verticalFovDeg)
                orientationTracker.setReference(
                    Quaternion.fromFloatArray(loaded.referenceQuaternion.toFloatArray()),
                )
                resumeLoading = false
                mutableState.value = mutableState.value.copy(sessionId = loaded.id)
                publishTargets()
                val accepted = loaded.images.count { it.accepted }
                setState(
                    if (accepted > 0) CaptureState.MOVING_TO_NEXT_TARGET else CaptureState.CALIBRATING,
                )
            }
        }
    }

    fun attachPreview(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        cameraJob?.cancel()
        cameraJob = viewModelScope.launch {
            cameraController.bind(previewView, lifecycleOwner)
            cameraController.updateRotation(previewView.display?.rotation ?: 0)
            maybeCreateSession()
        }
    }

    fun detachPreview() {
        cameraController.unbind()
    }

    fun refreshRotation(rotationDegrees: Int) {
        cameraController.updateRotation(rotationDegrees)
        val active = session
        val lens = cameraController.selectedLens()
        if (active != null &&
            active.images.none { it.accepted } &&
            lens != null &&
            (abs(lens.horizontalFovDeg - active.horizontalFovDeg) > 0.5f ||
                abs(lens.verticalFovDeg - active.verticalFovDeg) > 0.5f)
        ) {
            session = null
            targets = emptyList()
            sessionCreated = false
            sessionStore.delete(active.id)
        }
        maybeCreateSession()
    }

    fun toggleAutoCapture(enabled: Boolean) {
        settingsRepository.update { it.copy(autoCapture = enabled) }
        mutableState.value = mutableState.value.copy(autoCapture = enabled)
    }

    fun setFlash(setting: FlashSetting) = cameraController.setFlash(setting)

    fun setZoom(ratio: Float) = cameraController.setZoom(ratio)

    fun selectLens(cameraId: String) {
        if (session?.images?.any { it.accepted } == true) {
            mutableState.value = mutableState.value.copy(
                notice = "Camera cannot be changed after the first capture",
            )
            return
        }
        viewModelScope.launch {
            val discarded = session
            cameraController.selectLens(cameraId)
            session = null
            targets = emptyList()
            sessionCreated = false
            if (discarded != null && discarded.images.none { it.accepted }) {
                sessionStore.delete(discarded.id)
            }
            maybeCreateSession()
        }
    }

    fun requestManualCapture() {
        manualCaptureRequested = true
        tryAutoCapture(manual = true)
    }

    fun requestAutoCaptureCheck() {
        tryAutoCapture(manual = false)
    }

    fun finishCapture() {
        val active = session ?: return
        val accepted = active.images.count { it.accepted }
        if (accepted < 3) {
            mutableState.value = mutableState.value.copy(
                error = "Capture at least 3 images before stitching",
            )
            return
        }
        val coverage = coverageTracker.angularCoverage(targets)
        val completed = active.copy(completed = true)
        session = completed
        sessionStore.save(completed)
        mutableState.value = mutableState.value.copy(
            state = CaptureState.COMPLETED,
            completedSessionId = completed.id,
            coverage = coverage,
            instruction = "Photo Sphere completed",
        )
        viewModelScope.launch { mutableEvents.emit(CaptureEvent.Completed) }
    }

    fun cancelCapture() {
        val active = session
        mutableState.value = mutableState.value.copy(state = CaptureState.CANCELLED)
        if (active != null) {
            if (active.images.none { it.accepted }) {
                sessionStore.delete(active.id)
            } else {
                sessionStore.save(active.copy(completed = false))
            }
        }
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(error = null, notice = null)
    }

    fun consumeCompletion() {
        mutableState.value = mutableState.value.copy(completedSessionId = null)
    }

    override fun onCleared() {
        super.onCleared()
        orientationJob?.cancel()
        cameraJob?.cancel()
        orientationTracker.stop()
        cameraController.unbind()
    }

    private fun applySettings(settings: CaptureSettings) {
        cameraController.setCaptureLocks(settings.exposureLock, settings.focusLock)
        alignmentDetector.toleranceDegrees = settings.alignmentToleranceDeg
        alignmentDetector.requiredStableMs = settings.stabilityMs
        orientationTracker.updateStabilityConfiguration(
            settings.minAngularSpeedRadPerSec,
            settings.stabilityMs,
        )
        mutableState.value = mutableState.value.copy(
            autoCapture = settings.autoCapture,
            hapticFeedback = settings.hapticFeedback,
            soundFeedback = settings.soundFeedback,
        )
    }

    private fun observeOrientation() {
        orientationJob = viewModelScope.launch {
            orientationTracker.state.collect { snapshot ->
                handleOrientation(snapshot)
            }
        }
    }

    private fun observeCamera() {
        viewModelScope.launch {
            cameraController.state.collect { cameraState ->
                mutableState.value = mutableState.value.copy(
                    camera = cameraState,
                    canSwitchLens = session?.images?.none { it.accepted } ?: true,
                    error = cameraState.error ?: mutableState.value.error,
                )
                if (cameraState.ready) maybeCreateSession()
            }
        }
    }

    private fun maybeCreateSession() {
        if (resumeLoading || sessionCreated) return
        val lens = cameraController.selectedLens() ?: return
        val snapshot = orientationTracker.state.value
        if (!snapshot.sensorAvailable) {
            mutableState.value = mutableState.value.copy(
                state = CaptureState.CALIBRATING,
                instruction = "Waiting for motion sensors…",
                sensorDegraded = true,
            )
            return
        }
        val settings = settingsRepository.current
        val hFov = lens.horizontalFovDeg
        val vFov = lens.verticalFovDeg
        val planned = CapturePlanner.plan(
            horizontalFovDeg = hFov,
            verticalFovDeg = vFov,
            overlapPercent = settings.overlapPercent,
            densityMultiplier = settings.densityMultiplier,
        ).toMutableList()
        if (planned.isNotEmpty()) {
            planned[0] = planned[0].copy(status = TargetStatus.CURRENT)
        }
        orientationTracker.setReference(snapshot.quaternion)
        orientationTracker.resetStability()
        val created = sessionStore.create(
            reference = snapshot.quaternion,
            referenceYawDeg = snapshot.yawDeg,
            horizontalFovDeg = hFov,
            verticalFovDeg = vFov,
            targets = planned,
        )
        session = created
        targets = planned
        currentTargetIndex = 0
        sessionCreated = true
        coverageTracker = CoverageTracker(hFov, vFov)
        publishTargets()
        setState(CaptureState.READY)
        mutableState.value = mutableState.value.copy(
            instruction = "Align the dot with the target",
            sessionId = created.id,
        )
    }

    private fun handleOrientation(snapshot: OrientationSnapshot) {
        if (!sessionCreated) {
            maybeCreateSession()
            return
        }
        val target = targets.getOrNull(currentTargetIndex)
        val alignment = if (target != null) {
            alignmentDetector.evaluate(snapshot, target)
        } else {
            AlignmentResult()
        }
        val now = System.currentTimeMillis()
        if (alignment.aligned && alignment.stable) {
            if (alignedSinceMs == 0L) alignedSinceMs = now
        } else {
            alignedSinceMs = 0L
        }

        val transientStates = setOf(
            CaptureState.SEARCHING_ALIGNMENT,
            CaptureState.ALIGNED,
            CaptureState.WAITING_FOR_STABILITY,
            CaptureState.READY,
            CaptureState.MOVING_TO_NEXT_TARGET,
            CaptureState.CALIBRATING,
        )
        if (mutableState.value.state in transientStates) {
            val nextState = when {
                target == null -> CaptureState.READY
                alignment.aligned && snapshot.stable -> CaptureState.ALIGNED
                alignment.aligned -> CaptureState.WAITING_FOR_STABILITY
                else -> CaptureState.SEARCHING_ALIGNMENT
            }
            val instruction = instructionFor(target, alignment, snapshot)
            mutableState.value = mutableState.value.copy(
                orientation = snapshot,
                alignment = alignment,
                instruction = instruction,
                sensorDegraded = snapshot.usingFallback || !snapshot.sensorAvailable,
                totalCount = targets.size,
                capturedCount = targets.count { it.status == TargetStatus.CAPTURED },
            )
            if (mutableState.value.state != nextState) {
                mutableState.value = mutableState.value.copy(state = nextState)
            }
        } else {
            mutableState.value = mutableState.value.copy(
                orientation = snapshot,
                alignment = alignment,
            )
        }

        if (manualCaptureRequested) {
            manualCaptureRequested = false
            tryAutoCapture(manual = true)
            return
        }
        val settings = settingsRepository.current
        val delayPassed = alignedSinceMs > 0L && now - alignedSinceMs >= settings.captureDelayMs
        if (settings.autoCapture && alignment.aligned && snapshot.stable && delayPassed) {
            tryAutoCapture(manual = false)
        }
    }

    private fun tryAutoCapture(manual: Boolean) {
        if (captureInProgress || !sessionCreated) return
        val target = targets.getOrNull(currentTargetIndex) ?: return
        if (target.status == TargetStatus.CAPTURED) return
        val now = System.currentTimeMillis()
        if (now - lastCaptureAtMs < CAPTURE_COOLDOWN_MS) return
        val snapshot = orientationTracker.state.value
        if (!snapshot.sensorAvailable) {
            mutableState.value = mutableState.value.copy(
                error = "Motion sensor is unavailable. Spherical capture needs a working rotation sensor.",
            )
            return
        }
        if (!manual) {
            val alignment = alignmentDetector.evaluate(snapshot, target)
            if (!alignment.aligned || !snapshot.stable) return
        }
        lastCaptureAtMs = now
        captureInternal(target, snapshot, manual)
    }

    private fun captureInternal(
        target: CaptureTarget,
        snapshot: OrientationSnapshot,
        manual: Boolean,
    ) {
        val active = session ?: return
        captureInProgress = true
        viewModelScope.launch {
            try {
                updateTargetStatus(target.id, TargetStatus.CAPTURING)
                mutableState.value = mutableState.value.copy(
                    state = CaptureState.CAPTURING,
                    instruction = "Capturing…",
                )
                viewModelScope.launch { mutableEvents.emit(CaptureEvent.Shutter) }
                val file = cameraController.capture(sessionStore.newImageFile(active.id, target.id))
                mutableState.value = mutableState.value.copy(
                    state = CaptureState.VALIDATING_IMAGE,
                    instruction = "Checking image…",
                )
                val quality = withContext(Dispatchers.IO) { qualityValidator.validate(file) }
                val lens = cameraController.selectedLens()
                val meta = CapturedImageMeta(
                    targetId = target.id,
                    filePath = file.absolutePath,
                    quaternion = snapshot.quaternion.toFloatArray().toList(),
                    yawDeg = snapshot.relativeYawDeg,
                    pitchDeg = snapshot.pitchDeg,
                    timestampMs = System.currentTimeMillis(),
                    sharpness = quality.sharpness,
                    meanLuma = quality.meanLuma,
                    accepted = quality.acceptable,
                    note = quality.reason,
                    horizontalFovDeg = lens?.horizontalFovDeg ?: active.horizontalFovDeg,
                    verticalFovDeg = lens?.verticalFovDeg ?: active.verticalFovDeg,
                )
                val updatedImages = active.images + meta
                if (!quality.acceptable) {
                    val kept = active.copy(images = updatedImages)
                    session = kept
                    sessionStore.save(kept)
                    updateTargetStatus(target.id, TargetStatus.FAILED)
                    mutableState.value = mutableState.value.copy(
                        state = CaptureState.SEARCHING_ALIGNMENT,
                        error = quality.reason ?: "Image rejected, please retry",
                        instruction = "Retake this position",
                    )
                    mutableEvents.emit(CaptureEvent.Failure)
                    return@launch
                }

                val updatedTargets = targets.map {
                    if (it.id == target.id) it.copy(status = TargetStatus.CAPTURED) else it
                }
                targets = updatedTargets
                val nextIndex = targets.indexOfFirst { it.status != TargetStatus.CAPTURED }
                if (nextIndex >= 0) {
                    targets = targets.mapIndexed { index, item ->
                        when {
                            index == nextIndex -> item.copy(status = TargetStatus.CURRENT)
                            item.status == TargetStatus.FAILED -> item.copy(status = TargetStatus.PENDING)
                            else -> item
                        }
                    }
                    currentTargetIndex = nextIndex
                } else {
                    currentTargetIndex = targets.lastIndex
                }
                val updatedSession = active.copy(images = updatedImages, targets = targets)
                session = updatedSession
                sessionStore.save(updatedSession)
                publishTargets()
                mutableEvents.emit(CaptureEvent.Success)

                if (nextIndex < 0) {
                    completeSession(updatedSession)
                } else {
                    mutableState.value = mutableState.value.copy(
                        state = CaptureState.MOVING_TO_NEXT_TARGET,
                        capturedCount = targets.count { it.status == TargetStatus.CAPTURED },
                        totalCount = targets.size,
                        instruction = instructionFor(
                            targets[nextIndex],
                            AlignmentResult(),
                            orientationTracker.state.value,
                        ),
                    )
                }
            } catch (error: Exception) {
                updateTargetStatus(target.id, TargetStatus.PENDING)
                mutableState.value = mutableState.value.copy(
                    state = CaptureState.SEARCHING_ALIGNMENT,
                    error = error.message ?: "Capture failed. Move the camera and try again.",
                    instruction = "Capture failed — retry",
                )
                mutableEvents.emit(CaptureEvent.Failure)
            } finally {
                captureInProgress = false
            }
        }
    }

    private fun completeSession(active: SessionMetadata) {
        val coverage = coverageTracker.angularCoverage(targets)
        val completed = active.copy(completed = true)
        session = completed
        sessionStore.save(completed)
        targets = targets.map { it.copy(status = TargetStatus.CAPTURED) }
        publishTargets()
        val settings = settingsRepository.current
        mutableState.value = mutableState.value.copy(
            state = CaptureState.COVERAGE_CHECK,
            coverage = coverage,
            instruction = "Coverage check",
            capturedCount = targets.size,
            totalCount = targets.size,
        )
        if (coverage < 0.90f) {
            mutableState.value = mutableState.value.copy(
                notice = "Some areas have low coverage. You can add more or finish.",
            )
        }
        viewModelScope.launch {
            mutableEvents.emit(CaptureEvent.Completed)
            if (settings.autoStitch) {
                mutableState.value = mutableState.value.copy(
                    state = CaptureState.COMPLETED,
                    completedSessionId = completed.id,
                    instruction = "Photo Sphere completed",
                )
            } else {
                mutableState.value = mutableState.value.copy(state = CaptureState.CAPTURE_COMPLETED)
            }
        }
    }

    private fun updateTargetStatus(targetId: Int, status: TargetStatus) {
        targets = targets.map { if (it.id == targetId) it.copy(status = status) else it }
        publishTargets()
    }

    private fun publishTargets() {
        mutableState.value = mutableState.value.copy(
            targets = targets,
            currentTarget = targets.getOrNull(currentTargetIndex),
            capturedCount = targets.count { it.status == TargetStatus.CAPTURED },
            totalCount = targets.size,
        )
    }

    private fun setState(state: CaptureState) {
        mutableState.value = mutableState.value.copy(state = state)
    }

    private fun instructionFor(
        target: CaptureTarget?,
        alignment: AlignmentResult,
        snapshot: OrientationSnapshot,
    ): String {
        if (target == null) return "Capture complete"
        if (!snapshot.sensorAvailable) return "Motion sensor unavailable"
        return when {
            mutableState.value.state == CaptureState.VALIDATING_IMAGE -> "Checking image…"
            mutableState.value.state == CaptureState.CAPTURING -> "Capturing…"
            alignment.aligned && !snapshot.stable -> "Hold steady"
            alignment.aligned -> "Captured automatically"
            alignment.angularErrorDeg < 25f -> "Align the dot"
            target.kind == TargetKind.ZENITH -> "Tilt up to capture the sky"
            target.kind == TargetKind.NADIR -> "Tilt down to capture the ground"
            target.kind == TargetKind.UPPER -> "Move upward"
            target.kind == TargetKind.LOWER -> "Move downward"
            alignment.yawErrorDeg > 0 -> "Turn right slowly"
            else -> "Turn left slowly"
        }
    }

    class Factory(
        private val application: Application,
        private val resumeSessionId: String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CaptureViewModel(application, resumeSessionId) as T
        }
    }

    private companion object {
        const val CAPTURE_COOLDOWN_MS = 1_200L
    }
}
