package com.bbinxx.photoshere.camera

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.bbinxx.photoshere.core.model.CameraLens
import com.bbinxx.photoshere.core.model.FlashSetting
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class CameraState(
    val initialized: Boolean = false,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val selectedCameraId: String? = null,
    val lenses: List<CameraLens> = emptyList(),
    val flash: FlashSetting = FlashSetting.OFF,
    val zoomRatio: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val exposureCompensation: Int = 0,
    val minExposureCompensation: Int = 0,
    val maxExposureCompensation: Int = 0,
    val previewAspectRatio: Float = 3f / 4f,
    val error: String? = null,
)

interface CameraController {
    val state: StateFlow<CameraState>

    suspend fun bind(previewView: PreviewView, lifecycleOwner: LifecycleOwner)

    fun unbind()

    suspend fun capture(outputFile: File): File

    suspend fun selectLens(cameraId: String)

    fun setFlash(flash: FlashSetting)

    fun setZoom(ratio: Float)

    fun setExposureCompensation(index: Int)

    fun updateRotation(rotationDegrees: Int)

    fun selectedLens(): CameraLens?

    fun cameraId(): String?
}
