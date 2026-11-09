package com.bbinxx.photoshere.capture

import com.bbinxx.photoshere.core.model.QualityReport

/** Explicit shutter presses override quality/stability advice, never a missing
 * or unreadable image. Automatic capture retains all validation requirements.
 */
object CaptureAcceptance {
    fun accepts(manual: Boolean, quality: QualityReport, stable: Boolean): Boolean =
        quality.readable && (manual || (quality.acceptable && stable))
}
