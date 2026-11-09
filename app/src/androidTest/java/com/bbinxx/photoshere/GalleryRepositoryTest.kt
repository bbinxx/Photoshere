package com.bbinxx.photoshere

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bbinxx.photoshere.data.repository.GalleryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GalleryRepositoryTest {
    @Test
    fun failedReplacementPreservesSphereAndConcurrentSavesKeepAllRecords() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "gallery_test_${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getFilesDir(): File = root
        }
        try {
            val source = File(root, "source.jpg")
            val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            val repository = GalleryRepository(context)
            val original = repository.addStitchedSphere(source, "session")
            assertTrue(runCatching {
                repository.addStitchedSphere(File(root, "missing.jpg"), "session")
            }.isFailure)
            assertTrue(File(original.filePath).exists())
            assertEquals(original.id, repository.records.value.single().id)
            (0..3).map { i -> async { repository.addStitchedSphere(source, "parallel_$i") } }.awaitAll()
            assertEquals(5, repository.records.value.size)
            repository.refresh()
            assertEquals(5, repository.records.value.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deletionSurvivesRefreshAndKeepsSourcesAndExportedCopies() = runBlocking<Unit> {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "delete_test_${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getFilesDir(): File = root }
        try {
            val source = File(root, "original.jpg")
            val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            val exported = source.copyTo(File(root, "exported.jpg"))
            val repository = GalleryRepository(context)
            val deleted = repository.addStitchedSphere(source, "deleted_session")
            val kept = repository.addStitchedSphere(source, "kept_session")
            repository.delete(deleted)
            assertFalse(File(deleted.filePath).exists())
            assertTrue(source.exists())
            assertTrue(exported.exists())
            val reopened = GalleryRepository(context)
            reopened.refresh()
            assertEquals(kept.id, reopened.records.value.single().id)
        } finally { root.deleteRecursively() }
    }
}
