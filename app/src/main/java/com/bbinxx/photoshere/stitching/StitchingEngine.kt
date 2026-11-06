package com.bbinxx.photoshere.stitching

import com.bbinxx.photoshere.core.model.CaptureSettings
import com.bbinxx.photoshere.core.model.ProcessingProgress
import com.bbinxx.photoshere.core.model.SessionMetadata
import java.io.File

data class StitchingResult(
    val outputFile: File,
    val width: Int,
    val height: Int,
    val coverage: Float,
    val usedImages: Int,
    val warnings: List<String>,
    val elapsedMs: Long,
)

interface StitchingEngine {
    suspend fun stitch(
        session: SessionMetadata,
        settings: CaptureSettings,
        onProgress: (ProcessingProgress) -> Unit = {},
    ): StitchingResult
}

class StitchingException(message: String, cause: Throwable? = null) : Exception(message, cause)
