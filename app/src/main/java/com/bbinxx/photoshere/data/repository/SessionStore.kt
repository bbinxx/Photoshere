package com.bbinxx.photoshere.data.repository

import android.content.Context
import com.bbinxx.photoshere.core.model.CaptureTarget
import com.bbinxx.photoshere.core.model.CapturedImageMeta
import com.bbinxx.photoshere.core.model.Quaternion
import com.bbinxx.photoshere.core.model.SessionMetadata
import com.bbinxx.photoshere.core.model.TargetKind
import com.bbinxx.photoshere.core.model.TargetStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Durable session storage. Source captures are kept in
 * filesDir/sessions/<id>/raw_*.jpg and are never deleted by stitching.
 * The metadata is a small JSON document so an interrupted session can be reopened.
 */
class SessionStore(private val context: Context) {

    private val root: File
        get() = File(context.filesDir, "sessions").apply { mkdirs() }

    fun sessionDir(sessionId: String): File = File(root, sessionId).apply { mkdirs() }

    fun newImageFile(sessionId: String, targetId: Int): File {
        val dir = sessionDir(sessionId)
        return File(dir, "raw_${targetId}_${System.currentTimeMillis()}.jpg")
    }

    fun create(
        reference: Quaternion,
        referenceYawDeg: Float,
        horizontalFovDeg: Float,
        verticalFovDeg: Float,
        targets: List<CaptureTarget>,
    ): SessionMetadata {
        val session = SessionMetadata(
            id = "session_${System.currentTimeMillis()}",
            createdAtMs = System.currentTimeMillis(),
            referenceQuaternion = reference.toFloatArray().toList(),
            referenceYawDeg = referenceYawDeg,
            horizontalFovDeg = horizontalFovDeg,
            verticalFovDeg = verticalFovDeg,
            targets = targets,
            images = emptyList(),
        )
        save(session)
        return session
    }

    fun save(session: SessionMetadata) {
        val file = File(sessionDir(session.id), "session.json")
        val temp = File(file.parentFile, "session.json.tmp")
        temp.writeText(session.toJson().toString())
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
    }

    fun load(sessionId: String): SessionMetadata? {
        val file = File(sessionDir(sessionId), "session.json")
        if (!file.exists()) return null
        return runCatching { JSONObject(file.readText()).toSession() }.getOrNull()
    }

    fun listSessions(): List<SessionMetadata> {
        return root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { load(it.name) }
            ?.sortedByDescending { it.createdAtMs }
            ?: emptyList()
    }

    fun existingImages(session: SessionMetadata): List<CapturedImageMeta> =
        session.images.filter { File(it.filePath).exists() && it.accepted }

    private fun SessionMetadata.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("createdAtMs", createdAtMs)
        put("referenceQuaternion", JSONArray(referenceQuaternion))
        put("referenceYawDeg", referenceYawDeg.toDouble())
        put("horizontalFovDeg", horizontalFovDeg.toDouble())
        put("verticalFovDeg", verticalFovDeg.toDouble())
        put("completed", completed)
        put("cameraId", cameraId)
        put("targets", JSONArray().apply {
            targets.forEach { target ->
                put(JSONObject().apply {
                    put("id", target.id)
                    put("yawDeg", target.yawDeg.toDouble())
                    put("pitchDeg", target.pitchDeg.toDouble())
                    put("kind", target.kind.name)
                    put("status", target.status.name)
                })
            }
        })
        put("images", JSONArray().apply {
            images.forEach { image ->
                put(JSONObject().apply {
                    put("targetId", image.targetId)
                    put("filePath", image.filePath)
                    put("quaternion", JSONArray(image.quaternion))
                    put("yawDeg", image.yawDeg.toDouble())
                    put("pitchDeg", image.pitchDeg.toDouble())
                    put("timestampMs", image.timestampMs)
                    put("sharpness", image.sharpness.toDouble())
                    put("meanLuma", image.meanLuma.toDouble())
                    put("accepted", image.accepted)
                    put("note", image.note)
                })
            }
        })
    }

    private fun JSONObject.toSession(): SessionMetadata {
        val targetsJson = optJSONArray("targets") ?: JSONArray()
        val imagesJson = optJSONArray("images") ?: JSONArray()
        return SessionMetadata(
            id = getString("id"),
            createdAtMs = optLong("createdAtMs", System.currentTimeMillis()),
            referenceQuaternion = optJSONArray("referenceQuaternion").toFloatList(),
            referenceYawDeg = optDouble("referenceYawDeg", 0.0).toFloat(),
            horizontalFovDeg = optDouble("horizontalFovDeg", 65.0).toFloat(),
            verticalFovDeg = optDouble("verticalFovDeg", 50.0).toFloat(),
            targets = (0 until targetsJson.length()).map { index ->
                val target = targetsJson.getJSONObject(index)
                CaptureTarget(
                    id = target.getInt("id"),
                    yawDeg = target.optDouble("yawDeg", 0.0).toFloat(),
                    pitchDeg = target.optDouble("pitchDeg", 0.0).toFloat(),
                    kind = runCatching { TargetKind.valueOf(target.optString("kind", "HORIZON")) }
                        .getOrDefault(TargetKind.HORIZON),
                    status = runCatching { TargetStatus.valueOf(target.optString("status", "PENDING")) }
                        .getOrDefault(TargetStatus.PENDING),
                )
            },
            images = (0 until imagesJson.length()).map { index ->
                val image = imagesJson.getJSONObject(index)
                CapturedImageMeta(
                    targetId = image.getInt("targetId"),
                    filePath = image.getString("filePath"),
                    quaternion = image.optJSONArray("quaternion").toFloatList(),
                    yawDeg = image.optDouble("yawDeg", 0.0).toFloat(),
                    pitchDeg = image.optDouble("pitchDeg", 0.0).toFloat(),
                    timestampMs = image.optLong("timestampMs", 0L),
                    sharpness = image.optDouble("sharpness", 0.0).toFloat(),
                    meanLuma = image.optDouble("meanLuma", 0.0).toFloat(),
                    accepted = image.optBoolean("accepted", true),
                    note = image.optString("note").takeIf { it.isNotBlank() && it != "null" },
                )
            },
            completed = optBoolean("completed", false),
            cameraId = optString("cameraId").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    private fun JSONArray?.toFloatList(): List<Float> {
        if (this == null) return emptyList()
        return (0 until length()).map { getDouble(it).toFloat() }
    }
}
