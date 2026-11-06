package com.bbinxx.photoshere

import android.app.Application
import android.util.Log
import com.bbinxx.photoshere.core.common.AppContainer
import org.opencv.android.OpenCVLoader

class PhotoSphereApplication : Application() {

    lateinit var container: AppContainer
        private set

    var openCvAvailable: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        openCvAvailable = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
        Log.i(TAG, "OpenCV initialised: $openCvAvailable")
    }

    private companion object {
        const val TAG = "PhotoSphereApplication"
    }
}
