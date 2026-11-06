package com.bbinxx.photoshere.ui.processing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bbinxx.photoshere.PhotoSphereApplication
import com.bbinxx.photoshere.core.model.ProcessingProgress
import com.bbinxx.photoshere.core.model.PhotoSphereRecord
import com.bbinxx.photoshere.core.model.SphereMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ProcessingResult(
    val record: PhotoSphereRecord,
    val coverage: Float,
    val usedImages: Int,
    val warnings: List<String>,
    val elapsedMs: Long,
    val exportedUri: String? = null,
)

data class ProcessingUiState(
    val running: Boolean = true,
    val fraction: Float = 0f,
    val stage: String = "Preparing",
    val error: String? = null,
    val cancelled: Boolean = false,
    val result: ProcessingResult? = null,
)

class ProcessingViewModel(
    application: Application,
    private val sessionId: String,
) : AndroidViewModel(application) {

    private val app = application as PhotoSphereApplication
    private val container = app.container
    private val mutableState = MutableStateFlow(ProcessingUiState())
    val state: StateFlow<ProcessingUiState> = mutableState.asStateFlow()

    private var job: Job? = null

    init {
        start()
    }

    fun start() {
        if (job?.isActive == true) return
        mutableState.value = ProcessingUiState(running = true)
        job = viewModelScope.launch { process() }
    }

    fun cancel() {
        if (job?.isActive != true) return
        job?.cancel()
        mutableState.update {
            it.copy(running = false, cancelled = true, stage = "Cancelled")
        }
    }

    private suspend fun process() {
        try {
            val session = withContext(Dispatchers.IO) { container.sessionStore.load(sessionId) }
                ?: throw IllegalStateException("Capture session no longer exists")

            val images = session.images.filter {
                it.accepted && it.quaternion.size >= 4 && File(it.filePath).exists()
            }
            if (images.size < 3) {
                throw IllegalStateException(
                    "Only ${images.size} usable photos were captured. At least 3 are required.",
                )
            }

            mutableState.update {
                it.copy(
                    stage = "Stitching ${images.size} photos",
                    fraction = 0.02f,
                )
            }
            val settings = container.settings.current
            val result = container.stitchingEngine.stitch(session, settings) { progress ->
                onProgress(progress)
            }

            mutableState.update {
                it.copy(stage = "Saving to gallery", fraction = 0.9f)
            }
            val record = withContext(Dispatchers.IO) {
                container.gallery.addStitchedSphere(result.outputFile, session.id)
            }

            var exportedUri: String? = null
            val warnings = result.warnings.toMutableList()
            if (settings.autoExport) {
                mutableState.update {
                    it.copy(stage = "Exporting to Photos", fraction = 0.96f)
                }
                exportedUri = withContext(Dispatchers.IO) {
                    container.exporter.exportToGallery(
                        source = File(record.filePath),
                        metadata = SphereMetadata(
                            width = result.width,
                            height = result.height,
                            poseHeadingDeg = ((session.referenceYawDeg % 360f) + 360f) % 360f,
                        ),
                        displayName = record.title,
                    )
                }?.toString()
                if (exportedUri == null) {
                    warnings.add("The sphere was saved in the app but could not be exported to Photos")
                }
            }

            if (!settings.saveOriginals && (exportedUri != null || !settings.autoExport)) {
                withContext(Dispatchers.IO) {
                    session.images.forEach { image ->
                        File(image.filePath).delete()
                    }
                }
            }

            mutableState.update {
                it.copy(
                    running = false,
                    fraction = 1f,
                    stage = "Ready",
                    result = ProcessingResult(
                        record = record,
                        coverage = result.coverage,
                        usedImages = result.usedImages,
                        warnings = warnings,
                        elapsedMs = result.elapsedMs,
                        exportedUri = exportedUri,
                    ),
                )
            }
        } catch (cancelled: CancellationException) {
            mutableState.update {
                it.copy(running = false, cancelled = true, stage = "Cancelled")
            }
        } catch (error: Exception) {
            mutableState.update {
                it.copy(
                    running = false,
                    error = error.message ?: "Stitching failed",
                    stage = "Failed",
                )
            }
        }
    }

    private fun onProgress(progress: ProcessingProgress) {
        mutableState.update {
            it.copy(
                fraction = 0.02f + progress.fraction * 0.86f,
                stage = when {
                    progress.stage.isNotBlank() -> progress.stage
                    else -> "Stitching photos"
                },
            )
        }
    }

    class Factory(
        private val application: Application,
        private val sessionId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProcessingViewModel(application, sessionId) as T
        }
    }
}
