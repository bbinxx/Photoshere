package com.bbinxx.photoshere.export

import com.bbinxx.photoshere.core.model.SphereMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GpanoMetadataWriterTest {

    private val metadata = SphereMetadata(width = 2048, height = 1024)

    @Test
    fun xmpContainsRequiredGpanoFields() {
        val xmp = GpanoMetadataWriter.buildXmp(metadata)
        assertTrue(xmp.contains("GPano:ProjectionType=\"equirectangular\""))
        assertTrue(xmp.contains("GPano:UsePanoramaViewer=\"True\""))
        assertTrue(xmp.contains("GPano:FullPanoWidthPixels=\"2048\""))
        assertTrue(xmp.contains("GPano:FullPanoHeightPixels=\"1024\""))
        assertTrue(xmp.contains("GPano:CroppedAreaLeftPixels=\"0\""))
        assertTrue(xmp.contains("<?xpacket end=\"w\"?>"))
    }

    @Test
    fun writesApp1SegmentAfterSoi() {
        val file = File.createTempFile("gpano", ".jpg")
        try {
            file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
            assertTrue(GpanoMetadataWriter.write(file, metadata))

            val bytes = file.readBytes()
            assertEquals(0xFF, bytes[0].toInt() and 0xFF)
            assertEquals(0xD8, bytes[1].toInt() and 0xFF)
            assertEquals(0xFF, bytes[2].toInt() and 0xFF)
            assertEquals(0xE1, bytes[3].toInt() and 0xFF)

            val length = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)
            val payload = String(bytes, 6, length - 2, Charsets.ISO_8859_1)
            assertTrue(payload.startsWith("http://ns.adobe.com/xap/1.0/\u0000"))
            assertTrue(payload.contains("GPano:ProjectionType=\"equirectangular\""))
            assertEquals(0xFF, bytes[6 + length - 2].toInt() and 0xFF)
            assertEquals(0xD9, bytes[7 + length - 2].toInt() and 0xFF)
        } finally {
            file.delete()
        }
    }

    @Test
    fun doesNotDuplicateExistingGpanoPacket() {
        val file = File.createTempFile("gpano-dup", ".jpg")
        try {
            file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
            assertTrue(GpanoMetadataWriter.write(file, metadata))
            val once = file.length()
            assertTrue(GpanoMetadataWriter.write(file, metadata))
            assertEquals(once, file.length())
        } finally {
            file.delete()
        }
    }
}
