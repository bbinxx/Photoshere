package com.bbinxx.photoshere.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.bbinxx.photoshere.core.model.PhotoSphereRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream

class GalleryRepository(private val context: Context) {

    private val root: File
        get() = File(context.filesDir, "spheres").apply { mkdirs() }

    private val indexFile: File
        get() = File(context.filesDir, "gallery_index.json")

    private val mutableRecords = MutableStateFlow<List<PhotoSphereRecord>>(emptyList())
    val records: StateFlow<List<PhotoSphereRecord>> = mutableRecords.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val stored = readIndex().filter { File(it.filePath).exists() }
        val knownPaths = stored.map { it.filePath }.toSet()
        val discovered = root.listFiles()
            ?.filter { it.isFile && it.extension.equals("jpg", true) }
            ?.filterNot { it.absolutePath in knownPaths }
            ?.mapNotNull { file -> recordForFile(file, sessionId = null, imported = false) }
            ?: emptyList()
        val merged = (stored + discovered).sortedByDescending { it.createdAtMs }
        mutableRecords.value = merged
        writeIndex(merged)
    }

    suspend fun addStitchedSphere(source: File, sessionId: String?, title: String? = null): PhotoSphereRecord =
        withContext(Dispatchers.IO) {
            val id = "sphere_${System.currentTimeMillis()}"
            val destination = File(root, "$id.jpg")
            if (source.absolutePath != destination.absolutePath) {
                source.copyTo(destination, overwrite = true)
            }
            val record = recordForFile(
                file = destination,
                sessionId = sessionId,
                imported = false,
                forcedId = id,
                forcedTitle = title ?: defaultTitle(),
            ) ?: error("Unable to read stitched image")
            val updated = (mutableRecords.value + record).sortedByDescending { it.createdAtMs }
            mutableRecords.value = updated
            writeIndex(updated)
            record
        }

    suspend fun importFromUri(uri: Uri): PhotoSphereRecord? = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            val id = "sphere_${System.currentTimeMillis()}"
            val destination = File(root, "$id.jpg")
            val input: InputStream = context.contentResolver.openInputStream(uri)
                ?: return@runCatching null
            input.use { source -> destination.outputStream().use { target -> source.copyTo(target) } }
            val record = recordForFile(
                file = destination,
                sessionId = null,
                imported = true,
                forcedId = id,
                forcedTitle = "Imported ${defaultTitle()}",
            ) ?: return@runCatching null
            val updated = (mutableRecords.value + record).sortedByDescending { it.createdAtMs }
            mutableRecords.value = updated
            writeIndex(updated)
            record
        }.getOrNull()
    }

    suspend fun delete(record: PhotoSphereRecord) = withContext(Dispatchers.IO) {
        File(record.filePath).delete()
        val updated = mutableRecords.value.filterNot { it.id == record.id }
        mutableRecords.value = updated
        writeIndex(updated)
    }

    suspend fun rename(record: PhotoSphereRecord, title: String) = withContext(Dispatchers.IO) {
        val updated = mutableRecords.value.map {
            if (it.id == record.id) it.copy(title = title.trim().ifBlank { it.title }) else it
        }
        mutableRecords.value = updated
        writeIndex(updated)
    }

    suspend fun exportToUri(record: PhotoSphereRecord, uri: Uri) = withContext(Dispatchers.IO) {
        val input = File(record.filePath).inputStream()
        val output = context.contentResolver.openOutputStream(uri)
            ?: error("Unable to open export destination")
        input.use { source -> output.use { target -> source.copyTo(target) } }
        val updated = mutableRecords.value.map {
            if (it.id == record.id) it.copy(exportedUri = uri.toString()) else it
        }
        mutableRecords.value = updated
        writeIndex(updated)
    }

    fun shareUri(record: PhotoSphereRecord): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        File(record.filePath),
    )

    fun recordById(id: String): PhotoSphereRecord? = mutableRecords.value.firstOrNull { it.id == id }

    fun fileFor(record: PhotoSphereRecord): File = File(record.filePath)

    private fun recordForFile(
        file: File,
        sessionId: String?,
        imported: Boolean,
        forcedId: String? = null,
        forcedTitle: String? = null,
    ): PhotoSphereRecord? {
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        val id = forcedId ?: file.nameWithoutExtension
        val title = forcedTitle ?: file.nameWithoutExtension
        return PhotoSphereRecord(
            id = id,
            title = title,
            filePath = file.absolutePath,
            width = options.outWidth,
            height = options.outHeight,
            createdAtMs = file.lastModified(),
            sizeBytes = file.length(),
            sessionId = sessionId,
            imported = imported,
        )
    }

    private fun defaultTitle(): String {
        val stamp = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
        return "Photo Sphere $stamp"
    }

    private fun readIndex(): List<PhotoSphereRecord> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(indexFile.readText())
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                PhotoSphereRecord(
                    id = obj.getString("id"),
                    title = obj.optString("title", "Photo Sphere"),
                    filePath = obj.getString("filePath"),
                    width = obj.optInt("width", 0),
                    height = obj.optInt("height", 0),
                    createdAtMs = obj.optLong("createdAtMs", 0L),
                    sizeBytes = obj.optLong("sizeBytes", 0L),
                    sessionId = obj.optString("sessionId").takeIf { it.isNotBlank() && it != "null" },
                    imported = obj.optBoolean("imported", false),
                    exportedUri = obj.optString("exportedUri").takeIf { it.isNotBlank() && it != "null" },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(records: List<PhotoSphereRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(JSONObject().apply {
                put("id", record.id)
                put("title", record.title)
                put("filePath", record.filePath)
                put("width", record.width)
                put("height", record.height)
                put("createdAtMs", record.createdAtMs)
                put("sizeBytes", record.sizeBytes)
                put("sessionId", record.sessionId)
                put("imported", record.imported)
                put("exportedUri", record.exportedUri)
            })
        }
        val temp = File(indexFile.parentFile, "gallery_index.json.tmp")
        temp.writeText(array.toString())
        if (!temp.renameTo(indexFile)) {
            indexFile.writeText(temp.readText())
            temp.delete()
        }
    }
}
