package com.bbinxx.photoshere

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bbinxx.photoshere.core.model.*
import com.bbinxx.photoshere.ui.home.HomeScreen
import com.bbinxx.photoshere.ui.theme.PhotoshereTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class DeleteCaptureDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun deleteContinueCaptureRequiresConfirmationAndRemovesOnlyThatSession() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PhotoSphereApplication
        val store = app.container.sessionStore
        val id = "test_capture_${UUID.randomUUID()}"
        val original = store.newImageFile(id, 0)
        val bitmap = Bitmap.createBitmap(128, 64, Bitmap.Config.ARGB_8888)
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        // Even a rejected-only unfinished capture must be visible and deletable.
        val image = CapturedImageMeta(0, original.path, Quaternion.IDENTITY.toFloatArray().toList(),
            0f, 0f, 0L, accepted = false)
        val session = SessionMetadata(id, System.currentTimeMillis() + 3_600_000,
            Quaternion.IDENTITY.toFloatArray().toList(), 0f, 65f, 50f,
            listOf(CaptureTarget(0, 0f, 0f, TargetKind.HORIZON)), listOf(image))
        store.save(session)
        val other = session.copy(id = "test_keep_${UUID.randomUUID()}", images = emptyList())
        store.save(other)
        val export = File(app.cacheDir, "export_keep_${UUID.randomUUID()}.jpg")
        original.copyTo(export)
        val resumed = AtomicBoolean(false)
        try {
            compose.runOnUiThread {
                compose.activity.setContent {
                    PhotoshereTheme {
                        HomeScreen({}, { resumed.set(true) }, {}, {}, {}, {})
                    }
                }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("delete-capture-$id").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("delete-capture-$id").performScrollTo().performClick()
            compose.onNodeWithText("Delete capture?").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            assertTrue(original.exists())
            assertNotNull(store.load(id))
            assertFalse(resumed.get())
            compose.onNodeWithTag("delete-capture-$id").performClick()
            compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
            compose.waitUntil(10_000) { !original.parentFile!!.exists() }
            assertNull(store.load(id))
            assertFalse(original.parentFile!!.exists())
            assertNotNull(store.load(other.id))
            assertTrue(export.exists())
            compose.onNodeWithTag("delete-capture-$id").assertDoesNotExist()
        } finally {
            store.delete(id)
            store.delete(other.id)
            export.delete()
        }
    }
}
