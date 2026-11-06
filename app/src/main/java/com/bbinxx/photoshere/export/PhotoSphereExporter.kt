package com.bbinxx.photoshere.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.bbinxx.photoshere.core.model.SphereMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PhotoSphereExporter(private val context: Context) {

    suspend fun exportToGallery(
        source: File,
        metadata: SphereMetadata,
        displayName: String,
    ): Uri? = withContext(Dispatchers.IO) {
        if (!source.exists()) return@withContext null
        GpanoMetadataWriter.write(source, metadata)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, sanitizeName(displayName))
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/PhotoSphere",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = runCatching {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return@withContext null
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: run {
                resolver.delete(uri, null, null)
                return@withContext null
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    suspend fun writeToUri(source: File, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    private fun sanitizeName(name: String): String {
        val base = name.trim().ifBlank { "PhotoSphere" }.replace(Regex("[^A-Za-z0-9 _.-]"), "_")
        return if (base.endsWith(".jpg", true)) base else "$base.jpg"
    }
}
