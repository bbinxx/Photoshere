package com.bbinxx.photoshere.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Size
import android.util.SizeF
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraFilter
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
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
import com.bbinxx.photoshere.core.model.FlashSetting
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
class CameraXController(
    context: Context,
) : CameraController {

    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)

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

    private var exposureLock = true
    private var focusLock = true
    private var whiteBalanceLock = true

    private var zoomObserver: Observer<androidx.camera.core.ZoomState>? = null
    private var cameraStateObserver: Observer<androidx.camera.core.CameraState>? = null


    fun setCaptureLocks(exposure: Boolean, focus: Boolean, whiteBalance: Boolean = true) {
        exposureLock = exposure
        focusLock = focus
        whiteBalanceLock = whiteBalance
    }

    override suspend fun bind(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        this.previewView = previewView
        this.lifecycleOwner = lifecycleOwner
        this.displayRotation = previewView.display?.rotation ?: displayRotation
        val cameraProvider = awaitProvider()
        bindInternal(cameraProvider)
    }

    override fun unbind() {
        runCatching {
            removeObservers()
            provider?.unbindAll()
        }
        camera = null
        preview = null
        imageCapture = null
        previewView = null
        lifecycleOwner = null
        mutableState.value = mutableState.value.copy(ready = false, busy = false)
    }

    override suspend fun capture(outputFile: File): File {
        val capture = imageCapture ?: throw IllegalStateException("Camera is not initialised")
        outputFile.parentFile?.mkdirs()
        mutableState.value = mutableState.value.copy(busy = true)
        try {
            return suspendCancellableCoroutine { continuation ->
                val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                capture.takePicture(
                    options,
                    mainExecutor,
                    object : ImageCapture.OnImageSavedCallback {
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
            continuation.invokeOnCancellation { future.cancel(true) }
        }
    }

    private suspend fun bindInternal(cameraProvider: ProcessCameraProvider) = withContext(Dispatchers.Main) {
        val owner = lifecycleOwner ?: return@withContext
        val view = previewView ?: return@withContext
        try {
            removeObservers()
            cameraProvider.unbindAll()

            val cameraInfos = cameraProvider.availableCameraInfos
            val lenses = buildLensList(cameraInfos)
            val targetId = selectedCameraId?.takeIf { id -> lenses.any { it.id == id } }
                ?: lenses.firstOrNull { !it.isFront }?.id
                ?: lenses.firstOrNull()?.id
                ?: throw IllegalStateException("No camera available")
            selectedCameraId = targetId

            val selector = CameraSelector.Builder()
                .addCameraFilter(CameraFilter { infos ->
                    infos.filter { Camera2CameraInfo.from(it).cameraId == targetId }
                })
                .build()

            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()

            val previewBuilder = Preview.Builder()
                .setTargetRotation(displayRotation)
                .setResolutionSelector(resolutionSelector)
            val imageCaptureBuilder = ImageCapture.Builder()
                .setTargetRotation(displayRotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setJpegQuality(SOURCE_JPEG_QUALITY)
                .setResolutionSelector(resolutionSelector)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)

            applyCaptureConsistency(previewBuilder, imageCaptureBuilder, cameraInfos, targetId)

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

            val zoomState = boundCamera.cameraInfo.zoomState.value
            val exposure = boundCamera.cameraInfo.exposureState
            val selectedLens = lenses.firstOrNull { it.id == targetId }
            mutableState.value = CameraState(
                initialized = true,
                ready = true,
                busy = false,
                selectedCameraId = targetId,
                lenses = lenses,
                flash = mutableState.value.flash,
                zoomRatio = zoomState?.zoomRatio ?: 1f,
                minZoom = zoomState?.minZoomRatio ?: 1f,
                maxZoom = zoomState?.maxZoomRatio ?: 1f,
                exposureCompensation = exposure.exposureCompensationIndex,
                minExposureCompensation = exposure.exposureCompensationRange.lower,
                maxExposureCompensation = exposure.exposureCompensationRange.upper,
                error = null,
            )
            Log.i(TAG, "Bound camera $targetId (${selectedLens?.label}) fov=${selectedLens?.horizontalFovDeg}x${selectedLens?.verticalFovDeg}")

            observeCamera(boundCamera, owner)
        } catch (error: Exception) {
            Log.e(TAG, "Camera bind failed", error)
            mutableState.value = mutableState.value.copy(
                ready = false,
                busy = false,
                error = error.message ?: "Camera initialisation failed",
            )
        }
    }

    private fun applyCaptureConsistency(
        previewBuilder: Preview.Builder,
        imageCaptureBuilder: ImageCapture.Builder,
        cameraInfos: List<androidx.camera.core.CameraInfo>,
        targetId: String,
    ) {
        val info = cameraInfos.firstOrNull { Camera2CameraInfo.from(it).cameraId == targetId } ?: return
        val camera2Info = Camera2CameraInfo.from(info)
        val afModes = camera2Info.getCameraCharacteristic(
            CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES,
        )
        val previewInterop = Camera2Interop.Extender(previewBuilder)
        val captureInterop = Camera2Interop.Extender(imageCaptureBuilder)

        if (exposureLock) {
            previewInterop.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
            captureInterop.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
        }
        if (whiteBalanceLock) {
            previewInterop.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
            captureInterop.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
        }
        if (focusLock && afModes?.contains(CaptureRequest.CONTROL_AF_MODE_OFF) == true) {
            // Fixed infinity focus is appropriate for landscape spheres and keeps
            // consecutive frames consistent. Users can disable it in settings.
            captureInterop.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_OFF,
            )
            captureInterop.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, 0f)
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

                androidx.camera.core.CameraState.Type.OPEN,
                androidx.camera.core.CameraState.Type.OPENING,
                -> mutableState.value = mutableState.value.copy(ready = true)

                else -> Unit
            }
            cameraState.error?.let { error ->
                mutableState.value = mutableState.value.copy(
                    error = error.code.toString(),
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
        val boundCamera = camera ?: return
        zoomObserverRef?.let { boundCamera.cameraInfo.zoomState.removeObserver(it) }
        stateObserverRef?.let { boundCamera.cameraInfo.cameraState.removeObserver(it) }
        zoomObserverRef = null
        stateObserverRef = null
    }

    private fun rebuildLensMetrics(cameraProvider: ProcessCameraProvider) {
        val lenses = buildLensList(cameraProvider.availableCameraInfos)
        mutableState.value = mutableState.value.copy(lenses = lenses)
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

        val sorted = entries.sortedWith(
            compareBy<Entry> { if (it.front) 1 else 0 }.thenByDescending { it.pixels },
        )
        var rearIndex = 0
        return sorted.map { entry ->
            val metrics = lensMetrics(entry.focal, entry.sensorSize)
            val label = if (entry.front) {
                "Front camera"
            } else {
                rearIndex++
                if (rearIndex == 1) "Rear main" else "Rear lens $rearIndex"
            }
            CameraLens(
                id = entry.id,
                label = label,
                isFront = entry.front,
                focalLengthMm = entry.focal,
                horizontalFovDeg = metrics.first,
                verticalFovDeg = metrics.second,
            )
        }
    }

    private fun lensMetrics(focal: Float?, sensorSize: SizeF?): Pair<Float, Float> {
        if (focal == null || sensorSize == null || focal <= 0f) {
            return 65f to 50f
        }
        val sensorWidthFov = 2.0 * atan(sensorSize.width / (2.0 * focal)) * 180.0 / Math.PI
        val sensorHeightFov = 2.0 * atan(sensorSize.height / (2.0 * focal)) * 180.0 / Math.PI
        return if (displayRotation == 0 || displayRotation == 180) {
            // Sensor is landscape; portrait display swaps the axes.
            sensorHeightFov.toFloat() to sensorWidthFov.toFloat()
        } else {
            sensorWidthFov.toFloat() to sensorHeightFov.toFloat()
        }
    }

    private companion object {
        const val TAG = "CameraXController"
        const val SOURCE_JPEG_QUALITY = 96
    }
}
