package com.bbinxx.photoshere.core.common

import android.content.Context
import com.bbinxx.photoshere.data.preferences.SettingsRepository
import com.bbinxx.photoshere.data.repository.GalleryRepository
import com.bbinxx.photoshere.data.repository.SessionStore
import com.bbinxx.photoshere.export.PhotoSphereExporter
import com.bbinxx.photoshere.stitching.StitchingEngine
import com.bbinxx.photoshere.stitching.SensorPoseStitchingEngine

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settings = SettingsRepository(appContext)
    val sessionStore = SessionStore(appContext)
    val gallery = GalleryRepository(appContext)
    val exporter = PhotoSphereExporter(appContext)
    val stitchingEngine: StitchingEngine = SensorPoseStitchingEngine(appContext)
}
