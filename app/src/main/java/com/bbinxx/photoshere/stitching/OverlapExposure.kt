package com.bbinxx.photoshere.stitching

/** Robust gain from observations of the SAME scene points in overlapping views.
 * Dark/clipped samples and sparse overlaps cannot provide reliable exposure.
 */
object OverlapExposure {
    fun gain(samples: List<Pair<Float, Float>>): Float {
        val ratios = samples.filter { (reference, source) -> reference in 0.04f..0.94f && source in 0.04f..0.94f }
            .map { (reference, source) -> reference / source }.sorted()
        if (ratios.size < 32) return 1f
        return ratios[ratios.size / 2].coerceIn(0.7f, 1.4f)
    }
}
