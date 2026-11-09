package com.bbinxx.photoshere

import android.app.Application
import android.util.Log
import com.bbinxx.photoshere.core.common.AppContainer
import org.opencv.android.OpenCVLoader

class PhotoSphereApplication : Application() {

    lateinit var container: AppContainer
        private set

    // Native vision is optional for opening the app and camera. Load it only
    // when a computer-vision feature requests it, rather than during startup.
    val openCvAvailable: Boolean by lazy {
        runCatching { OpenCVLoader.initLocal() }.getOrDefault(false).also {
            Log.i(TAG, "OpenCV initialised: $it")
        }
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    private companion object {
        const val TAG = "PhotoSphereApplication"
    }
}
