package com.bbinxx.photoshere.ui.gallery

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bbinxx.photoshere.PhotoSphereApplication
import com.bbinxx.photoshere.core.model.PhotoSphereRecord
import com.bbinxx.photoshere.core.model.SphereMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class GalleryUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PhotoSphereApplication
    private val gallery = app.container.gallery
    private val exporter = app.container.exporter

    val records: StateFlow<List<PhotoSphereRecord>> = gallery.records
    private val mutableUi = MutableStateFlow(GalleryUiState())
    val ui: StateFlow<GalleryUiState> = mutableUi.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { gallery.refresh() }
                .onFailure { error ->
                    mutableUi.update { it.copy(error = error.message ?: "Could not load gallery") }
                }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            mutableUi.update { it.copy(busy = true, error = null, message = null) }
            val record = gallery.importFromUri(uri)
            mutableUi.update {
                it.copy(
                    busy = false,
                    message = if (record != null) "Imported photo sphere" else null,
                    error = if (record == null) {
                        "Unable to import this image. Choose a 2:1 equirectangular JPEG."
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun delete(record: PhotoSphereRecord) {
        viewModelScope.launch {
            runCatching { gallery.delete(record) }
                .onSuccess {
                    mutableUi.update { it.copy(message = "Deleted ${record.title}") }
                }
                .onFailure { error ->
                    mutableUi.update { it.copy(error = error.message ?: "Delete failed") }
                }
        }
    }

    fun rename(record: PhotoSphereRecord, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching { gallery.rename(record, title) }
                .onSuccess {
                    mutableUi.update { it.copy(message = "Renamed sphere") }
                }
                .onFailure { error ->
                    mutableUi.update { it.copy(error = error.message ?: "Rename failed") }
                }
        }
    }

    fun exportToPhotos(record: PhotoSphereRecord) {
        viewModelScope.launch {
            mutableUi.update { it.copy(busy = true, error = null) }
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    exporter.exportToGallery(
                        source = File(record.filePath),
                        metadata = SphereMetadata(
                            width = record.width,
                            height = record.height,
                        ),
                        displayName = record.title,
                    )
                }.getOrNull()
            }
            mutableUi.update {
                it.copy(
                    busy = false,
                    message = if (uri != null) "Saved to Pictures/PhotoSphere" else null,
                    error = if (uri == null) "Export to Photos failed" else null,
                )
            }
        }
    }

    fun exportToUri(record: PhotoSphereRecord, uri: Uri) {
        viewModelScope.launch {
            mutableUi.update { it.copy(busy = true, error = null) }
            runCatching { gallery.exportToUri(record, uri) }
                .onSuccess {
                    mutableUi.update { it.copy(busy = false, message = "Exported sphere") }
                }
                .onFailure { error ->
                    mutableUi.update {
                        it.copy(
                            busy = false,
                            error = error.message ?: "Export failed",
                        )
                    }
                }
        }
    }

    fun shareUri(record: PhotoSphereRecord): Uri = gallery.shareUri(record)

    fun consumeFeedback() {
        mutableUi.update { it.copy(message = null, error = null) }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GalleryViewModel(application) as T
        }
    }
}
