package com.bbinxx.photoshere.capture

import com.bbinxx.photoshere.core.model.TargetKind
import com.bbinxx.photoshere.core.model.TargetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePlannerTest {

    @Test
    fun planCoversSphereWithZenithAndNadir() {
        val targets = CapturePlanner.plan(
            horizontalFovDeg = 70f,
            verticalFovDeg = 55f,
            overlapPercent = 35,
        )
        assertTrue(targets.size >= 10)
        assertTrue(targets.any { it.kind == TargetKind.ZENITH })
        assertTrue(targets.any { it.kind == TargetKind.NADIR })
        assertTrue(targets.any { it.kind == TargetKind.HORIZON })
        assertEquals(targets.size, targets.map { it.id }.distinct().size)
        assertTrue(targets.all { it.yawDeg in 0f..360f })
        assertTrue(targets.all { it.pitchDeg in -90f..90f })
    }

    @Test
    fun higherDensityProducesMoreTargets() {
        val normal = CapturePlanner.plan(45f, 40f, 30, densityMultiplier = 1f)
        val dense = CapturePlanner.plan(45f, 40f, 30, densityMultiplier = 1.5f)
        assertTrue(dense.size > normal.size)
    }

    @Test
    fun narrowerLensNeedsMoreHorizontalTargets() {
        val wide = CapturePlanner.plan(90f, 65f, 35)
        val narrow = CapturePlanner.plan(50f, 40f, 35)
        assertTrue(narrow.size > wide.size)
    }

    @Test
    fun coverageProgressTracksCapturedTargets() {
        val tracker = CoverageTracker(70f, 55f)
        val targets = CapturePlanner.plan(70f, 55f, 35).mapIndexed { index, target ->
            if (index < 3) target.copy(status = TargetStatus.CAPTURED) else target
        }
        assertEquals(3f / targets.size, tracker.progress(targets), 1e-4f)
        assertTrue(tracker.angularCoverage(targets) in 0f..1f)
    }

    @Test
    fun fullCaptureHasMeaningfulCoverage() {
        val tracker = CoverageTracker(70f, 55f)
        val captured = CapturePlanner.plan(70f, 55f, 35).map {
            it.copy(status = TargetStatus.CAPTURED)
        }
        val coverage = tracker.angularCoverage(captured)
        assertTrue("coverage was $coverage", coverage > 0.6f)
        assertEquals(1f, tracker.progress(captured), 0f)
    }
}
