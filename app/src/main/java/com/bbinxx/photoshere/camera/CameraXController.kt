package com.bbinxx.photoshere.camera

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Build
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Size
import android.util.SizeF
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.DynamicRange
import androidx.camera.core.CameraFilter
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.bbinxx.photoshere.core.model.CameraLens
import com.bbinxx.photoshere.core.model.CameraGeometry
import com.bbinxx.photoshere.core.model.FlashSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.atan

/**
 * CameraX implementation. The whole Photo Sphere session is bound to one
 * physical camera id so intrinsics and colour response stay consistent.
 */
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
class CameraXController(
    context: Context,
) : CameraController {

    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)

    var onCaptureStarted: (() -> Unit)? = null

    private val mutableState = MutableStateFlow(CameraState())
    override val state: StateFlow<CameraState> = mutableState.asStateFlow()

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var previewView: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var selectedCameraId: String? = null
    private var displayRotation: Int = 0
    private var bufferAspect = 4f / 3f

    @Volatile private var aeConverged = false
    @Volatile private var awbConverged = false
    private var locksApplied = false
    private var exposureLock = false
    private var focusLock = false
    private var whiteBalanceLock = false

    private var zoomObserver: Observer<androidx.camera.core.ZoomState>? = null
    private var cameraStateObserver: Observer<androidx.camera.core.CameraState>? = null


    fun setCaptureLocks(exposure: Boolean, focus: Boolean, whiteBalance: Boolean = false) {
        val changed = exposureLock != exposure || focusLock != focus || whiteBalanceLock != whiteBalance
        exposureLock = exposure
        focusLock = focus
        whiteBalanceLock = whiteBalance
        if (changed) {
            locksApplied = false
            camera?.let { bound ->
                Camera2CameraControl.from(bound.cameraControl).clearCaptureRequestOptions()
                if (!focus) bound.cameraControl.cancelFocusAndMetering()
            }
        }
    }

    override suspend fun bind(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        this.previewView = previewView
        this.lifecycleOwner = lifecycleOwner
        this.displayRotation = previewView.display?.rotation ?: displayRotation
        try {
            val cameraProvider = awaitProvider()
            bindInternal(cameraProvider)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Camera provider initialisation failed", error)
            mutableState.value = mutableState.value.copy(
                ready = false, busy = false,
                error = error.message ?: "Camera initialisation failed. Close capture and try again.",
            )
        }
    }

    override fun unbind() {
        runCatching {
            removeObservers()
            val owned = listOfNotNull(preview, imageCapture)
            if (owned.isNotEmpty()) provider?.unbind(*owned.toTypedArray())
        }
        camera = null
        preview = null
        imageCapture = null
        previewView = null
        lifecycleOwner = null
        mutableState.value = mutableState.value.copy(ready = false, busy = false)
    }

    override suspend fun capture(outputFile: File): File {
        check(!mutableState.value.busy) { "Camera is already capturing" }
        check(mutableState.value.ready) { "Camera is not ready" }
        val capture = imageCapture ?: throw IllegalStateException("Camera is not initialised")
        val captureStarted = onCaptureStarted
        outputFile.parentFile?.mkdirs()
        mutableState.value = mutableState.value.copy(busy = true)
        try {
            applyConvergedLocks()
            return suspendCancellableCoroutine { continuation ->
                val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                capture.takePicture(
                    options,
                    mainExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onCaptureStarted() {
                            captureStarted?.invoke()
                        }

                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            if (continuation.isActive) continuation.resume(outputFile)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(exception)
                            }
                        }
                    },
                )
            }
        } finally {
            // A per-shot metering trigger uses AF_AUTO and temporarily holds
            // focus. Release it immediately so CameraX restores its continuous
            // picture AF mode throughout the live preview.
            if (!focusLock) camera?.cameraControl?.cancelFocusAndMetering()
            mutableState.value = mutableState.value.copy(busy = false)
        }
    }

    override suspend fun selectLens(cameraId: String) {
        selectedCameraId = cameraId
        val cameraProvider = provider ?: awaitProvider()
        val owner = lifecycleOwner
        val view = previewView
        if (owner != null && view != null) {
            bindInternal(cameraProvider)
        } else {
            mutableState.value = mutableState.value.copy(selectedCameraId = cameraId)
        }
    }

    override fun setFlash(flash: FlashSetting) {
        val capture = imageCapture
        capture?.flashMode = when (flash) {
            FlashSetting.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashSetting.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashSetting.ON -> ImageCapture.FLASH_MODE_ON
            FlashSetting.TORCH -> ImageCapture.FLASH_MODE_OFF
        }
        camera?.cameraControl?.enableTorch(flash == FlashSetting.TORCH)
        mutableState.value = mutableState.value.copy(flash = flash)
    }

    override fun setZoom(ratio: Float) {
        val current = camera ?: return
        val zoomState = current.cameraInfo.zoomState.value ?: return
        val clamped = ratio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        current.cameraControl.setZoomRatio(clamped)
        mutableState.value = mutableState.value.copy(zoomRatio = clamped)
    }

    override fun setExposureCompensation(index: Int) {
        val current = camera ?: return
        val exposure = current.cameraInfo.exposureState
        val range = exposure.exposureCompensationRange
        val clamped = index.coerceIn(range.lower, range.upper)
        current.cameraControl.setExposureCompensationIndex(clamped)
        mutableState.value = mutableState.value.copy(exposureCompensation = clamped)
    }

    override fun updateRotation(rotationDegrees: Int) {
        if (rotationDegrees == displayRotation) return
        displayRotation = rotationDegrees
        preview?.targetRotation = rotationDegrees
        imageCapture?.targetRotation = rotationDegrees
        provider?.let { rebuildLensMetrics(it) }
    }

    override fun selectedLens(): CameraLens? =
        mutableState.value.lenses.firstOrNull { it.id == mutableState.value.selectedCameraId }

    override fun cameraId(): String? = mutableState.value.selectedCameraId

    private suspend fun awaitProvider(): ProcessCameraProvider {
        provider?.let { return it }
        return suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(appContext)
            future.addListener(
                {
                    try {
                        val value = future.get()
                        provider = value
                        if (continuation.isActive) continuation.resume(value)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                },
                mainExecutor,
            )
            // The provider future is shared process-wide. Cancelling a screen
            // must not cancel camera initialisation for the next screen.
        }
    }

    private suspend fun bindInternal(cameraProvider: ProcessCameraProvider) = withContext(Dispatchers.Main) {
        val owner = lifecycleOwner ?: return@withContext
        val view = previewView ?: return@withContext
        try {
            removeObservers()
            aeConverged = false
            awbConverged = false
            locksApplied = false
            val owned = listOfNotNull(preview, imageCapture)
            if (owned.isNotEmpty()) cameraProvider.unbind(*owned.toTypedArray())

            val cameraInfos = cameraProvider.availableCameraInfos
            val lenses = buildLensList(cameraInfos)
            val targetId = selectedCameraId?.takeIf { id -> lenses.any { it.id == id } }
                ?: lenses.firstOrNull { !it.isFront }?.id
                ?: lenses.firstOrNull()?.id
                ?: throw IllegalStateException("No camera available")
            selectedCameraId = targetId
            val chosenLens = lenses.first { it.id == targetId }

            val selector = CameraSelector.Builder()
                .addCameraFilter(CameraFilter { infos ->
                    infos.filter { Camera2CameraInfo.from(it).cameraId == chosenLens.logicalCameraId }
                })
                .build()

            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()

            val previewBuilder = Preview.Builder()
                .setDynamicRange(DynamicRange.SDR)
                .setTargetRotation(displayRotation)
                .setResolutionSelector(resolutionSelector)
            val imageCaptureBuilder = ImageCapture.Builder()
                .setTargetRotation(displayRotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setJpegQuality(SOURCE_JPEG_QUALITY)
                .setResolutionSelector(resolutionSelector)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)

            // Observe automatic metering without overriding vendor colour/focus
            // defaults. Locking AWB before convergence can produce tinted frames.
            Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(session: CameraCaptureSession,
                        request: CaptureRequest, result: TotalCaptureResult) {
                        aeConverged = result[CaptureResult.CONTROL_AE_STATE] in listOf(
                            CaptureResult.CONTROL_AE_STATE_CONVERGED,
                            CaptureResult.CONTROL_AE_STATE_LOCKED,
                            CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED,
                        )
                        awbConverged = result[CaptureResult.CONTROL_AWB_STATE] in listOf(
                            CaptureResult.CONTROL_AWB_STATE_CONVERGED,
                            CaptureResult.CONTROL_AWB_STATE_LOCKED,
                        )
                    }
                },
            )

            val newPreview = previewBuilder.build()
            val newImageCapture = imageCaptureBuilder.build()
            newPreview.setSurfaceProvider(view.surfaceProvider)

            val boundCamera = cameraProvider.bindToLifecycle(
                owner,
                selector,
                newPreview,
                newImageCapture,
            )

            preview = newPreview
            imageCapture = newImageCapture
            camera = boundCamera
            val resolution = newImageCapture.resolutionInfo?.resolution
            if (resolution != null) bufferAspect = resolution.width.toFloat() / resolution.height
            if (chosenLens.zoomRatio != 1f) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val future = boundCamera.cameraControl.setZoomRatio(chosenLens.zoomRatio)
                    future.addListener({
                        try {
                            future.get()
                            if (continuation.isActive) continuation.resume(Unit)
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }, mainExecutor)
                }
            }
            val measuredLenses = buildLensList(cameraInfos)

            val zoomState = boundCamera.cameraInfo.zoomState.value
            val exposure = boundCamera.cameraInfo.exposureState
            val selectedLens = lenses.firstOrNull { it.id == targetId }
            mutableState.value = CameraState(
                initialized = true,
                ready = true,
                busy = false,
                selectedCameraId = targetId,
                lenses = measuredLenses,
                flash = mutableState.value.flash,
                zoomRatio = zoomState?.zoomRatio ?: 1f,
                minZoom = zoomState?.minZoomRatio ?: 1f,
                maxZoom = zoomState?.maxZoomRatio ?: 1f,
                exposureCompensation = exposure.exposureCompensationIndex,
                minExposureCompensation = exposure.exposureCompensationRange.lower,
                maxExposureCompensation = exposure.exposureCompensationRange.upper,
                previewAspectRatio = captureAspectRatio(),
                error = null,
            )
            Log.i(TAG, "Bound camera $targetId (${selectedLens?.label}) fov=${selectedLens?.horizontalFovDeg}x${selectedLens?.verticalFovDeg}")

            setFlash(mutableState.value.flash)
            observeCamera(boundCamera, owner)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Camera bind failed", error)
            mutableState.value = mutableState.value.copy(
                ready = false,
                busy = false,
                error = error.message ?: "Camera initialisation failed",
            )
        }
    }

    private suspend fun applyConvergedLocks() {
        val bound = camera ?: return
        if (!focusLock || !locksApplied) {
            val builder = FocusMeteringAction.Builder(
                SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f),
                FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB or
                    (if (focusLock) FocusMeteringAction.FLAG_AF else 0),
            )
            // Continuous AF stays active by default. An explicit AF trigger
            // switches CameraX to AF_AUTO; request that only for focus lock.
            // Still capture's quality pipeline performs its own 3A sequence.
            builder.disableAutoCancel()
            val action = builder.build()
            if (bound.cameraInfo.isFocusMeteringSupported(action)) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val future = bound.cameraControl.startFocusAndMetering(action)
                    future.addListener({
                        runCatching { future.get() }.onFailure { Log.w(TAG, "Focus lock unavailable", it) }
                        if (continuation.isActive) continuation.resume(Unit)
                    }, mainExecutor)
                    continuation.invokeOnCancellation { future.cancel(false) }
                }
            }
        }
        if (locksApplied) return
        withTimeoutOrNull(1500) {
            while (!aeConverged || !awbConverged) delay(50)
        }
        val info = Camera2CameraInfo.from(bound.cameraInfo)
        val options = CaptureRequestOptions.Builder()
        if (exposureLock && aeConverged &&
            info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true) {
            options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
        }
        if (whiteBalanceLock && awbConverged &&
            info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE) == true) {
            options.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
        }
        val control = Camera2CameraControl.from(bound.cameraControl)
        suspendCancellableCoroutine<Unit> { continuation ->
            val future = control.setCaptureRequestOptions(options.build())
            future.addListener({
                try {
                    future.get()
                    locksApplied = aeConverged && awbConverged
                    if (continuation.isActive) continuation.resume(Unit)
                } catch (error: Exception) {
                    // An unsupported lock should leave CameraX in automatic mode.
                    control.clearCaptureRequestOptions()
                    Log.w(TAG, "Metering lock unavailable; using automatic metering", error)
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }, mainExecutor)
        }
    }

    private fun observeCamera(boundCamera: Camera, owner: LifecycleOwner) {
        val zoomObserver = Observer<androidx.camera.core.ZoomState> { zoom ->
            mutableState.value = mutableState.value.copy(
                zoomRatio = zoom.zoomRatio,
                minZoom = zoom.minZoomRatio,
                maxZoom = zoom.maxZoomRatio,
            )
        }
        val stateObserver = Observer<androidx.camera.core.CameraState> { cameraState ->
            when (cameraState.type) {
                androidx.camera.core.CameraState.Type.CLOSED,
                androidx.camera.core.CameraState.Type.CLOSING,
                androidx.camera.core.CameraState.Type.PENDING_OPEN,
                -> mutableState.value = mutableState.value.copy(ready = false)

                androidx.camera.core.CameraState.Type.OPENING,
                -> mutableState.value = mutableState.value.copy(ready = false)

                androidx.camera.core.CameraState.Type.OPEN,
                -> mutableState.value = mutableState.value.copy(ready = true, error = null)
            }
            cameraState.error?.let { error ->
                mutableState.value = mutableState.value.copy(
                    error = cameraErrorMessage(error.code),
                    ready = false,
                )
            }
        }
        boundCamera.cameraInfo.zoomState.observe(owner, zoomObserver)
        boundCamera.cameraInfo.cameraState.observe(owner, stateObserver)
        zoomObserverRef = zoomObserver
        stateObserverRef = stateObserver
    }

    private var zoomObserverRef: Observer<androidx.camera.core.ZoomState>? = null
    private var stateObserverRef: Observer<androidx.camera.core.CameraState>? = null

    private fun removeObservers() {
        val boundCamera = camera
        if (boundCamera != null) {
            zoomObserverRef?.let { boundCamera.cameraInfo.zoomState.removeObserver(it) }
            stateObserverRef?.let { boundCamera.cameraInfo.cameraState.removeObserver(it) }
        }
        zoomObserverRef = null
        stateObserverRef = null
    }

    private fun rebuildLensMetrics(cameraProvider: ProcessCameraProvider) {
        val lenses = buildLensList(cameraProvider.availableCameraInfos)
        mutableState.value = mutableState.value.copy(lenses = lenses, previewAspectRatio = captureAspectRatio())
    }

    private fun buildLensList(
        cameraInfos: List<androidx.camera.core.CameraInfo>,
    ): List<CameraLens> {
        data class Entry(
            val info: androidx.camera.core.CameraInfo,
            val id: String,
            val front: Boolean,
            val pixels: Long,
            val focal: Float?,
            val sensorSize: SizeF?,
        )

        val entries = cameraInfos.mapNotNull { info ->
            runCatching {
                val camera2Info = Camera2CameraInfo.from(info)
                val id = camera2Info.cameraId
                val focal = camera2Info
                    .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.firstOrNull()
                val sensorSize = camera2Info.getCameraCharacteristic(
                    CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE,
                )
                val pixelSize = camera2Info.getCameraCharacteristic(
                    CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE,
                )
                val pixels = pixelSize?.let { it.width.toLong() * it.height.toLong() } ?: 0L
                Entry(
                    info = info,
                    id = id,
                    front = info.lensFacing == CameraSelector.LENS_FACING_FRONT,
                    pixels = pixels,
                    focal = focal,
                    sensorSize = sensorSize,
                )
            }.getOrNull()
        }

        val mainId = runCatching {
            Camera2CameraInfo.from(CameraSelector.DEFAULT_BACK_CAMERA.filter(cameraInfos).first()).cameraId
        }.getOrNull() ?: entries.firstOrNull { !it.front }?.id
        val main = entries.firstOrNull { it.id == mainId }
        val mainScale = (main?.focal ?: 1f) / (main?.sensorSize?.width ?: 1f)
        fun metrics(entry: Entry, focal: Float? = entry.focal, size: SizeF? = entry.sensorSize): Pair<Float, Float> {
            if (focal == null || size == null || focal <= 0) return 50f to 65f
            return CameraGeometry.fieldOfView(size.width, size.height, focal, bufferAspect,
                entry.info.getSensorRotationDegrees(displayRotation))
        }
        val lenses = entries.sortedWith(compareBy<Entry> { it.front }.thenBy { it.id != mainId }).map { entry ->
            val fov = metrics(entry)
            val relative = ((entry.focal ?: 1f) / (entry.sensorSize?.width ?: 1f)) / mainScale
            CameraLens(entry.id,
                when {
                    entry.front -> "Front camera"
                    entry.id == mainId -> "Rear main"
                    relative < 0.8f -> "Ultra wide"
                    else -> "Rear lens ${entry.id}"
                }, entry.front, entry.focal, fov.first, fov.second, relativeZoom = relative)
        }.toMutableList()
        // Some vendors expose ultra-wide only through the logical camera's zoom
        // range. Prefer its physical sensor intrinsics when they are readable.
        if (main != null && lenses.none { !it.isFront && it.relativeZoom < 0.8f } && Build.VERSION.SDK_INT >= 30) {
            val minZoom = Camera2CameraInfo.from(main.info)
                .getCameraCharacteristic(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.lower ?: 1f
            if (minZoom < 0.8f) {
                val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val physical = runCatching { manager.getCameraCharacteristics(main.id).physicalCameraIds.mapNotNull { id ->
                    runCatching {
                        val chars = manager.getCameraCharacteristics(id)
                        val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
                        val size = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                        if (focal != null && size != null) focal to size else null
                    }.getOrNull()
                }.minByOrNull { it.first / it.second.width } }.getOrNull()
                val fov = if (physical != null && physical.first / physical.second.width < mainScale * 0.8f)
                    metrics(main, physical.first, physical.second)
                else metrics(main, main.focal?.times(minZoom))
                lenses.add(CameraLens("${main.id}@ultrawide", "Ultra wide", false,
                    physical?.first, fov.first, fov.second, main.id, minZoom, minZoom))
            }
        }
        return lenses
    }

    private fun captureAspectRatio(): Float {
        val rotation = imageCapture?.resolutionInfo?.rotationDegrees ?: 90
        return if (rotation % 180 == 0) bufferAspect else 1f / bufferAspect
    }

    private fun cameraErrorMessage(code: Int): String = when (code) {
        androidx.camera.core.CameraState.ERROR_CAMERA_IN_USE ->
            "Camera is already in use by another app"
        androidx.camera.core.CameraState.ERROR_MAX_CAMERAS_IN_USE ->
            "Too many cameras are active"
        androidx.camera.core.CameraState.ERROR_CAMERA_DISABLED ->
            "Camera is disabled by policy"
        else -> "Camera error ($code)"
    }

    private companion object {
        const val TAG = "CameraXController"
        const val SOURCE_JPEG_QUALITY = 96
    }
}
