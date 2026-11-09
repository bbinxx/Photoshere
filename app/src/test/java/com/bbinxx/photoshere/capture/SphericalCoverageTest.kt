package com.bbinxx.photoshere.capture

import com.bbinxx.photoshere.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SphericalCoverageTest {
    private fun image(yaw: Float, pitch: Float, fov: Float = 100f) = CapturedImageMeta(
        0, "test.jpg", Quaternion.fromYawPitchRoll(yaw, pitch).toFloatArray().toList(),
        yaw, pitch, 0, horizontalFovDeg = fov, verticalFovDeg = fov,
    )

    @Test fun firstPhotoCanFaceTheSkyAndDuplicateDoesNotAddCoverage() {
        val model = SphericalCoverage(0f)
        val sky = image(130f, 80f)
        assertTrue(model.addsCoverage(Quaternion.fromFloatArray(sky.quaternion.toFloatArray()), 100f, 100f))
        model.record(sky)
        assertTrue(model.contains(130f, 80f))
        assertFalse(model.contains(130f, -80f))
        assertFalse(model.complete)
        assertFalse(model.addsCoverage(Quaternion.fromFloatArray(sky.quaternion.toFloatArray()), 100f, 100f))
    }

    @Test fun allDirectionsCoverFullSphereRegardlessOfPhotoOrder() {
        val images = listOf(image(0f, -90f), image(180f, 0f), image(0f, 90f),
            image(90f, 0f), image(-90f, 0f), image(0f, 0f))
        val a = SphericalCoverage(0f)
        val b = SphericalCoverage(0f)
        images.forEach(a::record)
        images.reversed().forEach(b::record)
        assertTrue(a.complete)
        assertTrue(b.complete)
        assertEquals(1f, a.fraction, 0f)
        assertNull(a.nearestGap(0f, 0f))
    }

    @Test fun horizontalRingDoesNotCountAsFullSphere() {
        val model = SphericalCoverage(0f)
        (0..7).forEach { model.record(image(it * 45f, 0f, 60f)) }
        assertFalse(model.complete)
        assertFalse(model.contains(0f, 90f))
        assertFalse(model.contains(0f, -90f))
        assertTrue(model.fraction < 0.6f)
    }

    @Test fun nearestSuggestionFollowsWhereTheCameraPoints() {
        val targets = CapturePlanner.plan(65f, 50f, 35)
        val chosen = targets.first { it.pitchDeg < -20f }
        val index = CapturePlanner.nearestUncaptured(targets, chosen.yawDeg, chosen.pitchDeg)!!
        assertEquals(chosen.id, targets[index].id)
        val marked = targets.map { if (it.id == chosen.id) it.copy(status = TargetStatus.CAPTURED) else it }
        assertNotEquals(index, CapturePlanner.nearestUncaptured(marked, chosen.yawDeg, chosen.pitchDeg))
    }

    @Test fun coverageUsesAbsoluteSensorHeadingAndIgnoresRejectedPhotos() {
        val model = SphericalCoverage(90f)
        model.record(image(90f, 0f, 60f).copy(accepted = false))
        assertEquals(0f, model.fraction, 0f)
        model.record(image(90f, 0f, 60f))
        assertTrue(model.contains(0f, 0f))
        assertFalse(model.contains(180f, 0f))
    }

    @Test fun slightDriftDoesNotCountAsAnotherUsefulPhoto() {
        val model = SphericalCoverage(0f)
        model.record(image(0f, 0f, 60f))
        for (yaw in listOf(-1f, -0.2f, 0f, 0.2f, 1f)) {
            assertFalse(model.addsCoverage(Quaternion.fromYawPitchRoll(yaw, 0f), 60f, 60f))
        }
        assertTrue(model.addsCoverage(Quaternion.fromYawPitchRoll(30f, 0f), 60f, 60f))
    }

    @Test fun duplicateFilteringStillAllowsFullCoverageAndFinalGapFilling() {
        val model = SphericalCoverage(0f)
        val planned = CapturePlanner.plan(65f, 50f, 35)
        for (target in planned) {
            val pose = Quaternion.fromYawPitchRoll(target.yawDeg, target.pitchDeg)
            if (model.addsCoverage(pose, 65f, 50f)) model.record(image(target.yawDeg, target.pitchDeg)
                .copy(horizontalFovDeg = 65f, verticalFovDeg = 50f))
        }
        repeat(100) {
            val gap = model.nearestGap(0f, 0f) ?: return@repeat
            val pose = Quaternion.fromYawPitchRoll(gap.first, gap.second)
            assertTrue("A genuine gap must remain capturable", model.addsCoverage(pose, 65f, 50f))
            model.record(image(gap.first, gap.second).copy(horizontalFovDeg = 65f, verticalFovDeg = 50f))
        }
        assertTrue(model.complete)
    }
}
