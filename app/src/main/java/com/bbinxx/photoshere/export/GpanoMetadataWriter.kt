package com.bbinxx.photoshere.export

import com.bbinxx.photoshere.core.model.SphereMetadata
import java.io.File
import java.io.IOException

/**
 * Injects Google Photo Sphere (GPano) XMP metadata into a JPEG.
 *
 * The XMP packet is stored in an APP1 segment immediately after SOI. Standard
 * panorama viewers (Google Photos, Facebook, Ricoh THETA viewers) read this
 * namespace to recognise an equirectangular 360 image.
 */
object GpanoMetadataWriter {

    private const val XMP_NAMESPACE = "http://ns.adobe.com/xap/1.0/\u0000"
    private const val GPANO_NAMESPACE = "http://ns.google.com/photos/1.0/panorama/"

    fun write(file: File, metadata: SphereMetadata): Boolean {
        if (!file.exists() || file.length() < 4) return false
        val bytes = file.readBytes()
        if (bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return false

        // Already contains GPano XMP: leave it untouched to avoid duplicate packets.
        val probe = String(bytes, 0, minOf(bytes.size, 131_072), Charsets.ISO_8859_1)
        if (probe.contains("GPano:")) return true

        val payload = (XMP_NAMESPACE + buildXmp(metadata)).toByteArray(Charsets.UTF_8)
        if (payload.size + 2 > 0xFFFF) return false

        val output = ByteArray(2 + 4 + payload.size + bytes.size - 2)
        output[0] = 0xFF.toByte()
        output[1] = 0xD8.toByte()
        output[2] = 0xFF.toByte()
        output[3] = 0xE1.toByte()
        val length = payload.size + 2
        output[4] = ((length shr 8) and 0xFF).toByte()
        output[5] = (length and 0xFF).toByte()
        payload.copyInto(output, 6)
        bytes.copyInto(output, 6 + payload.size, 2, bytes.size)
        try {
            file.writeBytes(output)
            return true
        } catch (error: IOException) {
            return false
        }
    }

    fun buildXmp(metadata: SphereMetadata): String {
        fun bool(value: Boolean) = if (value) "True" else "False"
        return buildString {
            append("<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n")
            append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n")
            append(" <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n")
            append("  <rdf:Description rdf:about=\"\"\n")
            append("   xmlns:GPano=\"$GPANO_NAMESPACE\"\n")
            append("   GPano:ProjectionType=\"${metadata.projectionType}\"\n")
            append("   GPano:UsePanoramaViewer=\"${bool(metadata.usePanoramaViewer)}\"\n")
            append("   GPano:FullPanoWidthPixels=\"${metadata.fullPanoWidth}\"\n")
            append("   GPano:FullPanoHeightPixels=\"${metadata.fullPanoHeight}\"\n")
            append("   GPano:CroppedAreaImageWidthPixels=\"${metadata.croppedAreaWidth}\"\n")
            append("   GPano:CroppedAreaImageHeightPixels=\"${metadata.croppedAreaHeight}\"\n")
            append("   GPano:CroppedAreaLeftPixels=\"${metadata.croppedAreaLeft}\"\n")
            append("   GPano:CroppedAreaTopPixels=\"${metadata.croppedAreaTop}\"\n")
            append("   GPano:PoseHeadingDegrees=\"${format(metadata.poseHeadingDeg)}\"\n")
            append("   GPano:PosePitchDegrees=\"${format(metadata.posePitchDeg)}\"\n")
            append("   GPano:PoseRollDegrees=\"${format(metadata.poseRollDeg)}\"/>\n")
            append(" </rdf:RDF>\n")
            append("</x:xmpmeta>\n")
            append("<?xpacket end=\"w\"?>")
        }
    }

    private fun format(value: Float): String = String.format(java.util.Locale.US, "%.2f", value)
}
