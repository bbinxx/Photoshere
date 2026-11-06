package com.bbinxx.photoshere.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bbinxx.photoshere.PhotoSphereApplication
import com.bbinxx.photoshere.core.model.CaptureSettings
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as PhotoSphereApplication).container.settings
    val settings: StateFlow<CaptureSettings> = repository.settings

    fun update(transform: (CaptureSettings) -> CaptureSettings) {
        repository.update(transform)
    }

    fun reset() {
        repository.reset()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(application) as T
        }
    }
}
