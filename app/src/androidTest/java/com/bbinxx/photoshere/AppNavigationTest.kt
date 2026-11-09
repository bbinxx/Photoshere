package com.bbinxx.photoshere

import android.Manifest
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeCameraSettingsAndReturnSurviveRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PhotoSphereApplication
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.CAMERA)
        val previous = app.container.settings.current
        compose.runOnUiThread { app.container.settings.update { it.copy(autoCapture = false) } }
        try {
            compose.onNodeWithText("New photo sphere").assertIsDisplayed()
            // Sensor-driven animation does not become permanently idle. Drive
            // Compose frames explicitly while CameraX continues in real time.
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText("New photo sphere").performClick()
            compose.mainClock.advanceTimeBy(500)
            waitForPreview()
            val startedAt = System.currentTimeMillis()
            compose.onNodeWithContentDescription("Take photo").performClick()
            val deadline = SystemClock.uptimeMillis() + 20_000
            var saved = false
            while (!saved && SystemClock.uptimeMillis() < deadline) {
                compose.mainClock.advanceTimeByFrame()
                saved = app.container.sessionStore.listSessions().any { session ->
                    session.images.any { it.timestampMs >= startedAt }
                }
                SystemClock.sleep(100)
            }
            org.junit.Assert.assertTrue("Manual shutter must capture the area the camera faces", saved)
            val captured = app.container.sessionStore.listSessions().flatMap { it.images }
                .first { it.timestampMs >= startedAt }
            org.junit.Assert.assertTrue("A readable manual photo must be accepted and advance capture", captured.accepted)
            compose.onNodeWithContentDescription("Capture settings").performClick()
            compose.mainClock.advanceTimeBy(500)
            compose.activityRule.scenario.recreate()
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithContentDescription("Back").performClick()
            compose.mainClock.advanceTimeBy(500)
            waitForPreview()
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.mainClock.advanceTimeBy(500)
            waitForPreview()
            compose.onNodeWithContentDescription("Close capture").performClick()
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithText("New photo sphere").assertIsDisplayed()
        } finally {
            compose.runOnUiThread { app.container.settings.update { previous } }
            compose.mainClock.autoAdvance = true
        }
    }

    private fun waitForPreview() {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            compose.mainClock.advanceTimeByFrame()
            var streaming = false
            compose.activityRule.scenario.onActivity { activity ->
                streaming = previewIn(activity.window.decorView)?.previewStreamState?.value ==
                    PreviewView.StreamState.STREAMING
            }
            if (streaming) return
            SystemClock.sleep(100)
        }
        fail("Timed out waiting for live CameraX preview")
    }

    private fun previewIn(view: View): PreviewView? {
        if (view is PreviewView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            previewIn(view.getChildAt(i))?.let { return it }
        }
        return null
    }
}
