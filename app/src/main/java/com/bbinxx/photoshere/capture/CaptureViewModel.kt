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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
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
    private var displayRotation = 0
    private var resumeLoading = resumeSessionId != null
    private var captureJob: Job? = null
    private var previewAttached = false
    private var captureInProgress = false
    private var lastCaptureAtMs = 0L
    private var alignedSinceMs = 0L
    private var sessionCreated = false
    private var orientationJob: Job? = null
    private var cameraJob: Job? = null

    private val alignmentDetector = AlignmentDetector()
    private var coverageModel = SphericalCoverage(0f)

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
                coverageModel = SphericalCoverage(loaded.referenceYawDeg)
                loaded.images.filter { java.io.File(it.filePath).exists() }.forEach { image ->
                    coverageModel.record(image.copy(
                        horizontalFovDeg = image.horizontalFovDeg.takeIf { it > 0 } ?: loaded.horizontalFovDeg,
                        verticalFovDeg = image.verticalFovDeg.takeIf { it > 0 } ?: loaded.verticalFovDeg,
                    ))
                }
                targets = targets.map {
                    it.copy(status = if (coverageModel.contains(it.yawDeg, it.pitchDeg))
                        TargetStatus.CAPTURED else TargetStatus.PENDING)
                }
                orientationTracker.setReference(
                    Quaternion.fromFloatArray(loaded.referenceQuaternion.toFloatArray()),
                )
                loaded.cameraId?.let { cameraController.selectLens(it) }
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
        previewAttached = true
        orientationTracker.start()
        cameraJob?.cancel()
        cameraJob = viewModelScope.launch {
            try {
                cameraController.bind(previewView, lifecycleOwner)
                if (!cameraController.state.value.initialized) return@launch
                session?.cameraId?.let { cameraController.selectLens(it) }
                refreshRotation(previewView.display?.rotation ?: 0)
                maybeCreateSession()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    state = CaptureState.ERROR,
                    error = error.message ?: "Unable to start capture. Close and try again.",
                )
            }
        }
    }

    fun pauseSensors() {
        orientationTracker.stop()
        alignedSinceMs = 0L
    }

    fun resumeSensors() {
        orientationTracker.resetStability()
        orientationTracker.start()
    }

    fun detachPreview() {
        previewAttached = false
        cameraJob?.cancel()
        captureJob?.cancel()
        alignedSinceMs = 0L
        orientationTracker.stop()
        cameraController.unbind()
    }

    fun refreshRotation(rotationDegrees: Int) {
        displayRotation = rotationDegrees
        cameraController.updateRotation(rotationDegrees)
        orientationTracker.setCameraFrame(rotationDegrees, cameraController.selectedLens()?.isFront == true)
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
            if (active.images.isEmpty()) sessionStore.delete(active.id)
        }
        maybeCreateSession()
    }

    fun toggleAutoCapture(enabled: Boolean) {
        settingsRepository.update { it.copy(autoCapture = enabled) }
        mutableState.value = mutableState.value.copy(autoCapture = enabled)
    }

    fun setFlash(setting: FlashSetting) = cameraController.setFlash(setting)

    fun selectLens(cameraId: String) {
        if (captureInProgress || cameraController.state.value.busy) return
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
            if (discarded != null && discarded.images.isEmpty()) {
                sessionStore.delete(discarded.id)
            }
            maybeCreateSession()
        }
    }

    fun requestManualCapture() {
        tryAutoCapture(manual = true)
    }

    fun requestAutoCaptureCheck() {
        tryAutoCapture(manual = false)
    }

    fun finishCapture() {
        if (captureInProgress || mutableState.value.state == CaptureState.CANCELLED) return
        val active = session ?: return
        val accepted = active.images.count { it.accepted }
        if (accepted < 3) {
            mutableState.value = mutableState.value.copy(
                error = "Capture at least 3 images before stitching",
            )
            return
        }
        if (!coverageModel.complete) {
            mutableState.value = mutableState.value.copy(
                notice = "${(coverageModel.fraction * 100).toInt()}% covered. Capture the remaining gaps, including sky and ground, before stitching.",
            )
            return
        }
        val coverage = coverageModel.fraction
        val completed = active.copy(completed = coverageModel.complete)
        session = completed
        sessionStore.save(completed)
        mutableState.value = mutableState.value.copy(
            state = CaptureState.COMPLETED,
            completedSessionId = completed.id,
            coverage = coverage,
            instruction = if (completed.completed) "Photo Sphere completed" else "Partial capture saved",
        )
        viewModelScope.launch { mutableEvents.emit(CaptureEvent.Completed) }
    }

    fun cancelCapture() {
        captureJob?.cancel()
        val active = session
        mutableState.value = mutableState.value.copy(state = CaptureState.CANCELLED)
        if (active != null) {
            sessionStore.save(active.copy(completed = false, targets = targets.map {
                if (it.status == TargetStatus.CAPTURING) it.copy(status = TargetStatus.PENDING) else it
            }))
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

    @OptIn(FlowPreview::class)
    private fun observeOrientation() {
        orientationJob = viewModelScope.launch {
            orientationTracker.state.sample(50).collect { snapshot ->
                handleOrientation(snapshot)
            }
        }
    }

    private fun observeCamera() {
        viewModelScope.launch {
            cameraController.state.collect { cameraState ->
                orientationTracker.setCameraFrame(displayRotation, cameraController.selectedLens()?.isFront == true)
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
        if (resumeLoading || sessionCreated || !previewAttached || !cameraController.state.value.ready) return
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
        ).copy(cameraId = cameraController.cameraId())
        sessionStore.save(created)
        session = created
        targets = planned
        currentTargetIndex = 0
        sessionCreated = true
        coverageModel = SphericalCoverage(created.referenceYawDeg)
        publishTargets()
        setState(CaptureState.READY)
        mutableState.value = mutableState.value.copy(
            instruction = "Point anywhere and tap the shutter to start",
            sessionId = created.id,
        )
    }

    private fun handleOrientation(snapshot: OrientationSnapshot) {
        if (!sessionCreated) {
            maybeCreateSession()
            return
        }
        if (!captureInProgress && mutableState.value.state in CAPTURE_ALLOWED_STATES) {
            selectNearestTarget(snapshot)
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
                capturedCount = session?.images?.count { it.accepted } ?: 0,
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

        val settings = settingsRepository.current
        val delayPassed = alignedSinceMs > 0L && now - alignedSinceMs >= settings.captureDelayMs
        if (settings.autoCapture && alignment.aligned && snapshot.stable && delayPassed) {
            tryAutoCapture(manual = false)
        }
    }

    private fun tryAutoCapture(manual: Boolean) {
        if (captureInProgress || !sessionCreated || !previewAttached ||
            !cameraController.state.value.ready || cameraController.state.value.busy ||
            mutableState.value.state !in CAPTURE_ALLOWED_STATES
        ) return
        if (!manual && session?.images?.none { it.accepted } != false) return
        val now = System.currentTimeMillis()
        if (now - lastCaptureAtMs < CAPTURE_COOLDOWN_MS) return
        val snapshot = orientationTracker.state.value
        if (!snapshot.sensorAvailable) {
            mutableState.value = mutableState.value.copy(
                error = "Motion sensor is unavailable. Spherical capture needs a working rotation sensor.",
            )
            return
        }
        val lens = cameraController.selectedLens() ?: return
        if (!coverageModel.addsCoverage(snapshot.quaternion, lens.horizontalFovDeg, lens.verticalFovDeg)) {
            if (manual) mutableState.value = mutableState.value.copy(notice = "This area is already covered. Point toward an uncaptured area.")
            return
        }
        val target = if (manual) {
            CaptureTarget(
                id = (targets.maxOfOrNull { it.id } ?: -1) + 1,
                yawDeg = snapshot.relativeYawDeg, pitchDeg = snapshot.pitchDeg,
                kind = kindForPitch(snapshot.pitchDeg), status = TargetStatus.CURRENT,
            ).also { target ->
                targets = targets.map { if (it.status == TargetStatus.CURRENT) it.copy(status = TargetStatus.PENDING) else it } + target
                currentTargetIndex = targets.lastIndex
            }
        } else {
            val suggestion = targets.getOrNull(currentTargetIndex) ?: return
            val alignment = alignmentDetector.evaluate(snapshot, suggestion)
            if (!alignment.aligned || !snapshot.stable) return
            suggestion
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
        captureJob = viewModelScope.launch {
            try {
                updateTargetStatus(target.id, TargetStatus.CAPTURING)
                mutableState.value = mutableState.value.copy(
                    state = CaptureState.CAPTURING,
                    instruction = "Capturing…",
                )
                viewModelScope.launch { mutableEvents.emit(CaptureEvent.Shutter) }
                var shutterPose = snapshot
                cameraController.onCaptureStarted = { shutterPose = orientationTracker.state.value }
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
                    quaternion = shutterPose.quaternion.toFloatArray().toList(),
                    yawDeg = shutterPose.relativeYawDeg,
                    pitchDeg = shutterPose.pitchDeg,
                    timestampMs = System.currentTimeMillis(),
                    sharpness = quality.sharpness,
                    meanLuma = quality.meanLuma,
                    accepted = quality.acceptable && (manual || shutterPose.stable),
                    note = quality.reason ?: if (!manual && !shutterPose.stable) "Camera moved during capture. Retake this area." else null,
                    horizontalFovDeg = lens?.horizontalFovDeg ?: active.horizontalFovDeg,
                    verticalFovDeg = lens?.verticalFovDeg ?: active.verticalFovDeg,
                )
                val updatedImages = active.images + meta
                if (!meta.accepted) {
                    val kept = active.copy(images = updatedImages)
                    session = kept
                    sessionStore.save(kept)
                    updateTargetStatus(target.id, TargetStatus.FAILED)
                    mutableState.value = mutableState.value.copy(
                        state = CaptureState.SEARCHING_ALIGNMENT,
                        error = meta.note ?: "Image rejected, please retry",
                        instruction = "Retake this position",
                    )
                    mutableEvents.emit(CaptureEvent.Failure)
                    return@launch
                }

                coverageModel.record(meta)
                targets = targets.map {
                    if (coverageModel.contains(it.yawDeg, it.pitchDeg)) it.copy(status = TargetStatus.CAPTURED)
                    else it.copy(status = TargetStatus.PENDING)
                }
                selectNearestTarget(orientationTracker.state.value)
                val nextIndex = if (coverageModel.complete) -1 else currentTargetIndex
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
                        capturedCount = session?.images?.count { it.accepted } ?: 0,
                        totalCount = targets.size,
                        instruction = instructionFor(
                            targets[nextIndex],
                            AlignmentResult(),
                            orientationTracker.state.value,
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                updateTargetStatus(target.id, TargetStatus.PENDING)
                if (mutableState.value.state != CaptureState.CANCELLED) {
                    setState(CaptureState.READY)
                }
                throw cancelled
            } catch (error: Exception) {
                updateTargetStatus(target.id, TargetStatus.PENDING)
                mutableState.value = mutableState.value.copy(
                    state = CaptureState.SEARCHING_ALIGNMENT,
                    error = error.message ?: "Capture failed. Move the camera and try again.",
                    instruction = "Capture failed — retry",
                )
                mutableEvents.emit(CaptureEvent.Failure)
            } finally {
                cameraController.onCaptureStarted = null
                captureInProgress = false
            }
        }
    }

    private fun completeSession(active: SessionMetadata) {
        val coverage = coverageModel.fraction
        val completed = active.copy(completed = true)
        session = completed
        sessionStore.save(completed)
        publishTargets()
        val settings = settingsRepository.current
        mutableState.value = mutableState.value.copy(
            state = CaptureState.COVERAGE_CHECK,
            coverage = coverage,
            instruction = "Coverage check",
            capturedCount = active.images.count { it.accepted },
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

    private fun kindForPitch(pitch: Float): TargetKind = when {
        pitch > 80 -> TargetKind.ZENITH
        pitch < -80 -> TargetKind.NADIR
        pitch > 10 -> TargetKind.UPPER
        pitch < -10 -> TargetKind.LOWER
        else -> TargetKind.HORIZON
    }

    private fun selectNearestTarget(snapshot: OrientationSnapshot) {
        var index = CapturePlanner.nearestUncaptured(targets, snapshot.relativeYawDeg, snapshot.pitchDeg)
        if (index == null && !coverageModel.complete) {
            coverageModel.nearestGap(snapshot.relativeYawDeg, snapshot.pitchDeg)?.let { (yaw, pitch) ->
                targets = targets + CaptureTarget((targets.maxOfOrNull { it.id } ?: -1) + 1,
                    yaw, pitch, kindForPitch(pitch))
                index = targets.lastIndex
            }
        }
        val next = index ?: return
        if (next != currentTargetIndex) alignedSinceMs = 0L
        currentTargetIndex = next
        targets = targets.mapIndexed { i, target ->
            when {
                i == next -> target.copy(status = TargetStatus.CURRENT)
                target.status == TargetStatus.CURRENT -> target.copy(status = TargetStatus.PENDING)
                else -> target
            }
        }
        publishTargets()
    }

    private fun updateTargetStatus(targetId: Int, status: TargetStatus) {
        targets = targets.map { if (it.id == targetId) it.copy(status = status) else it }
        publishTargets()
    }

    private fun publishTargets() {
        mutableState.value = mutableState.value.copy(
            targets = targets,
            currentTarget = targets.getOrNull(currentTargetIndex),
            capturedCount = session?.images?.count { it.accepted } ?: 0,
            totalCount = targets.size,
            coverage = coverageModel.fraction,
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
        if (session?.images?.none { it.accepted } != false) return "Point anywhere and tap the shutter to start"
        if (target == null) return "Capture complete"
        if (!snapshot.sensorAvailable) return "Motion sensor unavailable"
        return when {
            mutableState.value.state == CaptureState.VALIDATING_IMAGE -> "Checking image…"
            mutableState.value.state == CaptureState.CAPTURING -> "Capturing…"
            alignment.aligned && !snapshot.stable -> "Hold steady"
            alignment.aligned -> if (mutableState.value.autoCapture) "Hold steady" else "Tap to capture this area"
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
        val CAPTURE_ALLOWED_STATES = setOf(
            CaptureState.READY, CaptureState.SEARCHING_ALIGNMENT, CaptureState.ALIGNED,
            CaptureState.WAITING_FOR_STABILITY, CaptureState.MOVING_TO_NEXT_TARGET,
        )
        const val CAPTURE_COOLDOWN_MS = 1_200L
    }
}
