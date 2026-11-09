package com.bbinxx.photoshere

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bbinxx.photoshere.ui.theme.PhotoshereTheme
import com.bbinxx.photoshere.ui.viewer.ViewerScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class ViewerDeleteDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun viewerConfirmsDeletionAndReturnsAfterRemovingSphere() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PhotoSphereApplication
        val gallery = app.container.gallery
        val source = File(app.cacheDir, "viewer_delete_${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(128, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(100, 150, 200))
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        val record = runBlocking { gallery.addStitchedSphere(source, "test_${UUID.randomUUID()}", "Delete regression test") }
        val returned = AtomicBoolean(false)
        try {
            compose.runOnUiThread {
                compose.activity.setContent {
                    PhotoshereTheme { ViewerScreen(record.id) { returned.set(true) } }
                }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("Delete sphere").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Delete sphere").performClick()
            compose.onNodeWithText("Delete sphere?").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            assertTrue(File(record.filePath).exists())
            assertFalse(returned.get())
            compose.onNodeWithContentDescription("Delete sphere").performClick()
            compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
            compose.waitUntil(10_000) { returned.get() }
            assertFalse(File(record.filePath).exists())
            runBlocking { gallery.refresh() }
            assertNull(gallery.recordById(record.id))
            assertTrue(source.exists())
        } finally {
            runBlocking { if (gallery.recordById(record.id) != null) gallery.delete(record) }
            source.delete()
        }
    }
}
