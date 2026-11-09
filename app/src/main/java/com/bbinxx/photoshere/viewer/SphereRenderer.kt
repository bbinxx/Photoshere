package com.bbinxx.photoshere.viewer

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders an inside-facing UV sphere with an equirectangular texture.
 *
 * Texture coordinate convention: u=0 at longitude -180, u=1 at +180,
 * v=0 at the north pole, v=1 at the south pole. Vertex positions put
 * longitude 0 directly in front of the default camera (-Z).
 */
class SphereRenderer : GLSurfaceView.Renderer {

    @Volatile
    var targetYawDeg: Float = 0f

    @Volatile
    var targetPitchDeg: Float = 0f

    @Volatile
    var targetFovDeg: Float = 75f

    private var yawDeg = 0f
    private var pitchDeg = 0f
    private var fovDeg = 75f

    @Volatile
    private var pendingBitmap: Bitmap? = null

    private var currentBitmap: Bitmap? = null
    private var textureId = 0
    private var program = 0
    private var positionHandle = -1
    private var texCoordHandle = -1
    private var mvpHandle = -1
    private var textureHandle = -1

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texCoordBuffer: FloatBuffer
    private lateinit var indexBuffer: ByteBuffer

    private var indexCount = 0

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val mvp = FloatArray(16)

    fun setImage(bitmap: Bitmap) {
        pendingBitmap = bitmap
    }

    fun reset() {
        targetYawDeg = 0f
        targetPitchDeg = 0f
        targetFovDeg = 75f
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        textureId = 0
        if (pendingBitmap == null) pendingBitmap = currentBitmap
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        createMesh()
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionHandle = GLES30.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES30.glGetAttribLocation(program, "aTexCoord")
        mvpHandle = GLES30.glGetUniformLocation(program, "uMvp")
        textureHandle = GLES30.glGetUniformLocation(program, "uTexture")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        onViewport(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        uploadPendingTexture()

        yawDeg += com.bbinxx.photoshere.core.model.wrapDegrees(targetYawDeg - yawDeg) * SMOOTHING
        pitchDeg += (targetPitchDeg - pitchDeg) * SMOOTHING
        fovDeg += (targetFovDeg - fovDeg) * SMOOTHING
        pitchDeg = pitchDeg.coerceIn(-88f, 88f)

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        Matrix.setIdentityM(view, 0)
        Matrix.rotateM(view, 0, -pitchDeg, 1f, 0f, 0f)
        Matrix.rotateM(view, 0, -yawDeg, 0f, 1f, 0f)
        val aspect = if (lastWidth == 0) 1f else lastWidth.toFloat() / lastHeight.coerceAtLeast(1)
        Matrix.perspectiveM(projection, 0, fovDeg, aspect, 0.1f, 20f)
        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)

        if (textureId == 0) return

        GLES30.glUseProgram(program)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(textureHandle, 0)
        GLES30.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)

        vertexBuffer.position(0)
        GLES30.glEnableVertexAttribArray(positionHandle)
        GLES30.glVertexAttribPointer(positionHandle, 3, GLES30.GL_FLOAT, false, 0, vertexBuffer)

        texCoordBuffer.position(0)
        GLES30.glEnableVertexAttribArray(texCoordHandle)
        GLES30.glVertexAttribPointer(texCoordHandle, 2, GLES30.GL_FLOAT, false, 0, texCoordBuffer)

        indexBuffer.position(0)
        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES,
            indexCount,
            GLES30.GL_UNSIGNED_SHORT,
            indexBuffer,
        )

        GLES30.glDisableVertexAttribArray(positionHandle)
        GLES30.glDisableVertexAttribArray(texCoordHandle)
    }

    var lastWidth: Int = 0
        private set
    var lastHeight: Int = 0
        private set

    fun onViewport(width: Int, height: Int) {
        lastWidth = width
        lastHeight = height
        Matrix.perspectiveM(
            projection,
            0,
            75f,
            width.toFloat() / height.coerceAtLeast(1),
            0.1f,
            20f,
        )
    }

    private fun uploadPendingTexture() {
        val bitmap = pendingBitmap ?: return
        pendingBitmap = null
        if (textureId == 0) {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            textureId = ids[0]
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        if (currentBitmap !== bitmap) currentBitmap?.recycle()
        currentBitmap = bitmap
    }

    private fun createMesh() {
        val latSteps = 64
        val lonSteps = 128
        val positions = ArrayList<Float>((latSteps + 1) * (lonSteps + 1) * 3)
        val texCoords = ArrayList<Float>((latSteps + 1) * (lonSteps + 1) * 2)
        for (i in 0..latSteps) {
            val lat = -90f + i * 180f / latSteps
            val latRad = Math.toRadians(lat.toDouble())
            val cp = cos(latRad).toFloat()
            val sp = sin(latRad).toFloat()
            for (j in 0..lonSteps) {
                val lon = -180f + j * 360f / lonSteps
                val lonRad = Math.toRadians(lon.toDouble())
                val x = cp * sin(lonRad).toFloat()
                val z = -cp * cos(lonRad).toFloat()
                positions.add(x)
                positions.add(sp)
                positions.add(z)
                texCoords.add((lon + 180f) / 360f)
                texCoords.add((90f - lat) / 180f)
            }
        }
        val row = lonSteps + 1
        val indices = ArrayList<Int>(latSteps * lonSteps * 6)
        for (i in 0 until latSteps) {
            for (j in 0 until lonSteps) {
                val a = i * row + j
                val b = a + 1
                val c = a + row
                val d = c + 1
                indices.add(a); indices.add(c); indices.add(b)
                indices.add(b); indices.add(c); indices.add(d)
            }
        }
        indexCount = indices.size

        val vertexData = FloatArray(positions.size)
        positions.forEachIndexed { index, value -> vertexData[index] = value }
        vertexBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(vertexData); position(0) }

        val texData = FloatArray(texCoords.size)
        texCoords.forEachIndexed { index, value -> texData[index] = value }
        texCoordBuffer = ByteBuffer.allocateDirect(texData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(texData); position(0) }

        val indexArray = ShortArray(indices.size) { indices[it].toShort() }
        indexBuffer = ByteBuffer.allocateDirect(indexArray.size * 2)
            .order(ByteOrder.nativeOrder())
            .apply { asShortBuffer().put(indexArray); position(0) }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw IllegalStateException("Shader compilation failed: $log")
        }
        return shader
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, vertex)
        GLES30.glAttachShader(id, fragment)
        GLES30.glLinkProgram(id)
        val status = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(id)
            GLES30.glDeleteProgram(id)
            throw IllegalStateException("Program link failed: $log")
        }
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        return id
    }

    private companion object {
        const val SMOOTHING = 0.22f

        val VERTEX_SHADER = """
            #version 300 es
            in vec3 aPosition;
            in vec2 aTexCoord;
            uniform mat4 uMvp;
            out vec2 vTexCoord;
            void main() {
                gl_Position = uMvp * vec4(aPosition, 1.0);
                vTexCoord = aTexCoord;
            }
        """.trimIndent()

        val FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            in vec2 vTexCoord;
            uniform sampler2D uTexture;
            out vec4 fragColor;
            void main() {
                fragColor = texture(uTexture, vTexCoord);
            }
        """.trimIndent()
    }
}
