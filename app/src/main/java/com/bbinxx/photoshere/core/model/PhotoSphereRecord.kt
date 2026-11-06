package com.bbinxx.photoshere.core.model

data class PhotoSphereRecord(
    val id: String,
    val title: String,
    val filePath: String,
    val width: Int,
    val height: Int,
    val createdAtMs: Long,
    val sizeBytes: Long,
    val sessionId: String? = null,
    val imported: Boolean = false,
    val exportedUri: String? = null,
)
