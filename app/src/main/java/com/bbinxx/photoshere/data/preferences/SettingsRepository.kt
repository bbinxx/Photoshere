package com.bbinxx.photoshere.data.preferences

import android.content.Context
import com.bbinxx.photoshere.core.model.CaptureSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("photo_sphere_settings", Context.MODE_PRIVATE)
    private val mutableSettings = MutableStateFlow(load())
    val settings: StateFlow<CaptureSettings> = mutableSettings.asStateFlow()

    val current: CaptureSettings
        get() = mutableSettings.value

    fun update(transform: (CaptureSettings) -> CaptureSettings) {
        val updated = transform(mutableSettings.value)
        mutableSettings.value = updated
        persist(updated)
    }

    fun reset() {
        update { CaptureSettings() }
    }

    private fun load(): CaptureSettings {
        val default = CaptureSettings()
        return default.copy(
            autoCapture = prefs.getBoolean(KEY_AUTO_CAPTURE, default.autoCapture),
            alignmentToleranceDeg = prefs.getFloat(KEY_TOLERANCE, default.alignmentToleranceDeg),
            stabilityMs = prefs.getInt(KEY_STABILITY, default.stabilityMs),
            minAngularSpeedRadPerSec = prefs.getFloat(KEY_MIN_SPEED, default.minAngularSpeedRadPerSec),
            captureDelayMs = prefs.getInt(KEY_DELAY, default.captureDelayMs),
            overlapPercent = prefs.getInt(KEY_OVERLAP, default.overlapPercent),
            densityMultiplier = prefs.getFloat(KEY_DENSITY, default.densityMultiplier),
            outputWidth = prefs.getInt(KEY_OUTPUT_WIDTH, default.outputWidth),
            outputHeight = prefs.getInt(KEY_OUTPUT_HEIGHT, default.outputHeight),
            jpegQuality = prefs.getInt(KEY_JPEG_QUALITY, default.jpegQuality),
            processingQuality = prefs.getFloat(KEY_PROCESSING_QUALITY, default.processingQuality),
            exposureLock = prefs.getBoolean(KEY_EXPOSURE_LOCK, default.exposureLock),
            focusLock = prefs.getBoolean(KEY_FOCUS_LOCK, default.focusLock),
            hapticFeedback = prefs.getBoolean(KEY_HAPTIC, default.hapticFeedback),
            soundFeedback = prefs.getBoolean(KEY_SOUND, default.soundFeedback),
            saveOriginals = prefs.getBoolean(KEY_SAVE_ORIGINALS, default.saveOriginals),
            autoStitch = prefs.getBoolean(KEY_AUTO_STITCH, default.autoStitch),
            autoExport = prefs.getBoolean(KEY_AUTO_EXPORT, default.autoExport),
            useGyroscopeGuidance = prefs.getBoolean(KEY_GYRO_GUIDANCE, default.useGyroscopeGuidance),
        )
    }

    private fun persist(settings: CaptureSettings) {
        prefs.edit()
            .putBoolean(KEY_AUTO_CAPTURE, settings.autoCapture)
            .putFloat(KEY_TOLERANCE, settings.alignmentToleranceDeg)
            .putInt(KEY_STABILITY, settings.stabilityMs)
            .putFloat(KEY_MIN_SPEED, settings.minAngularSpeedRadPerSec)
            .putInt(KEY_DELAY, settings.captureDelayMs)
            .putInt(KEY_OVERLAP, settings.overlapPercent)
            .putFloat(KEY_DENSITY, settings.densityMultiplier)
            .putInt(KEY_OUTPUT_WIDTH, settings.outputWidth)
            .putInt(KEY_OUTPUT_HEIGHT, settings.outputHeight)
            .putInt(KEY_JPEG_QUALITY, settings.jpegQuality)
            .putFloat(KEY_PROCESSING_QUALITY, settings.processingQuality)
            .putBoolean(KEY_EXPOSURE_LOCK, settings.exposureLock)
            .putBoolean(KEY_FOCUS_LOCK, settings.focusLock)
            .putBoolean(KEY_HAPTIC, settings.hapticFeedback)
            .putBoolean(KEY_SOUND, settings.soundFeedback)
            .putBoolean(KEY_SAVE_ORIGINALS, settings.saveOriginals)
            .putBoolean(KEY_AUTO_STITCH, settings.autoStitch)
            .putBoolean(KEY_AUTO_EXPORT, settings.autoExport)
            .putBoolean(KEY_GYRO_GUIDANCE, settings.useGyroscopeGuidance)
            .apply()
    }

    private companion object {
        const val KEY_AUTO_CAPTURE = "auto_capture"
        const val KEY_TOLERANCE = "tolerance"
        const val KEY_STABILITY = "stability"
        const val KEY_MIN_SPEED = "min_speed"
        const val KEY_DELAY = "delay"
        const val KEY_OVERLAP = "overlap"
        const val KEY_DENSITY = "density"
        const val KEY_OUTPUT_WIDTH = "output_width"
        const val KEY_OUTPUT_HEIGHT = "output_height"
        const val KEY_JPEG_QUALITY = "jpeg_quality"
        const val KEY_PROCESSING_QUALITY = "processing_quality"
        const val KEY_EXPOSURE_LOCK = "exposure_lock"
        const val KEY_FOCUS_LOCK = "focus_lock"
        const val KEY_HAPTIC = "haptic"
        const val KEY_SOUND = "sound"
        const val KEY_SAVE_ORIGINALS = "save_originals"
        const val KEY_AUTO_STITCH = "auto_stitch"
        const val KEY_AUTO_EXPORT = "auto_export"
        const val KEY_GYRO_GUIDANCE = "gyro_guidance"
    }
}
