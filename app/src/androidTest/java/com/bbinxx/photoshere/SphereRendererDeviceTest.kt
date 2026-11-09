package com.bbinxx.photoshere

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.EGL14
import android.opengl.GLES30
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bbinxx.photoshere.viewer.SphereRenderer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class SphereRendererDeviceTest {
    @Test
    fun imageRendersAfterGlContextIsRecreated() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val versions = IntArray(2)
        assertTrue(EGL14.eglInitialize(display, versions, 0, versions, 1))
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        assertTrue(EGL14.eglChooseConfig(display, intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, 0x40, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_NONE,
        ), 0, configs, 0, 1, count, 0))
        assertTrue(count[0] > 0)
        val renderer = SphereRenderer()
        val image = Bitmap.createBitmap(128, 64, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.rgb(100, 150, 200))
        renderer.setImage(image)
        try {
            repeat(2) {
                val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
                val surface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(
                    EGL14.EGL_WIDTH, 64, EGL14.EGL_HEIGHT, 64, EGL14.EGL_NONE), 0)
                try {
                    assertTrue(EGL14.eglMakeCurrent(display, surface, surface, context))
                    renderer.onSurfaceCreated(null, null)
                    renderer.onSurfaceChanged(null, 64, 64)
                    renderer.onDrawFrame(null)
                    val pixel = ByteBuffer.allocateDirect(4)
                    GLES30.glReadPixels(32, 32, 1, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixel)
                    assertEquals(GLES30.GL_NO_ERROR, GLES30.glGetError())
                    assertEquals(100, pixel.get(0).toInt() and 255)
                    assertEquals(150, pixel.get(1).toInt() and 255)
                    assertEquals(200, pixel.get(2).toInt() and 255)
                } finally {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    EGL14.eglDestroySurface(display, surface)
                    EGL14.eglDestroyContext(display, context)
                }
            }
        } finally {
            EGL14.eglTerminate(display)
            image.recycle()
        }
    }
}
