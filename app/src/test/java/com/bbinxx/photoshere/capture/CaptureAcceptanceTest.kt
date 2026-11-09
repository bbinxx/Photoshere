package com.bbinxx.photoshere.capture

import com.bbinxx.photoshere.core.model.QualityReport
import org.junit.Assert.*
import org.junit.Test

class CaptureAcceptanceTest {
    @Test fun manualShutterAcceptsDarkBlurryOrMovingPhotos() {
        for (reason in listOf("Too dark", "Overexposed", "Image is blurry")) {
            val report = QualityReport(0f, 0f, false, reason)
            assertTrue(CaptureAcceptance.accepts(true, report, false))
            assertTrue(CaptureAcceptance.accepts(true, report, true))
        }
    }
    @Test fun automaticCaptureStillRequiresQualityAndStability() {
        assertFalse(CaptureAcceptance.accepts(false, QualityReport(0f, 0f, false), true))
        assertFalse(CaptureAcceptance.accepts(false, QualityReport(100f, 100f, true), false))
        assertTrue(CaptureAcceptance.accepts(false, QualityReport(100f, 100f, true), true))
    }
    @Test fun manualShutterCannotAcceptAnUnreadableFile() {
        val unreadable = QualityReport(0f, 0f, false, readable = false)
        assertFalse(CaptureAcceptance.accepts(true, unreadable, true))
        assertFalse(CaptureAcceptance.accepts(false, unreadable, true))
    }
}
