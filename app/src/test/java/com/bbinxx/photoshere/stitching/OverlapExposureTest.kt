package com.bbinxx.photoshere.stitching

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlapExposureTest {
    @Test fun compensatesOnlyMatchingOverlapSamples() {
        assertEquals(1.25f, OverlapExposure.gain(List(100) { 0.5f to 0.4f }), 0.001f)
    }
    @Test fun ignoresClippedSamplesAndRequiresEnoughOverlap() {
        assertEquals(1f, OverlapExposure.gain(List(10) { 0.5f to 0.2f }), 0f)
        assertEquals(1f, OverlapExposure.gain(List(100) { 1f to 0f }), 0f)
    }
    @Test fun medianRejectsMovingObjectOutliersAndBoundsGain() {
        assertEquals(1f, OverlapExposure.gain(List(80) { 0.4f to 0.4f } + List(20) { 0.8f to 0.1f }), 0f)
        assertEquals(1.4f, OverlapExposure.gain(List(100) { 0.8f to 0.1f }), 0f)
    }
}
