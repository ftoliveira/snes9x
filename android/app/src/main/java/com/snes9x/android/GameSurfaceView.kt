package com.snes9x.android

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceView that renders SNES frames.
 *
 * The emulation loop runs inside [onDrawFrame]: one SNES frame per GL frame.
 * At 60 Hz vsync this matches NTSC speed naturally.
 */
class GameSurfaceView(context: Context) : GLSurfaceView(context) {

    private val renderer = Snes9xRenderer()

    // Large enough for one frame of stereo audio at 32 kHz
    private val audioScratch = ShortArray(4096)
    private var audioOutput: AudioOutput? = null

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setAudioOutput(ao: AudioOutput) {
        audioOutput = ao
    }

    fun onGamePause() {
        onPause()
        audioOutput?.pause()
    }

    fun onGameResume() {
        onResume()
        audioOutput?.start()
    }

    // ── Inner renderer ────────────────────────────────────────────────────────

    private inner class Snes9xRenderer : Renderer {

        private var program    = 0
        private var texId      = 0
        private var texWidth   = 0
        private var texHeight  = 0

        private var viewW = 1
        private var viewH = 1

        private var quadBuffer: FloatBuffer? = null
        private val aPos = 0
        private val aTex = 1

        // Vertex data: pos(x,y) + texcoord(u,v), 4 vertices, triangle-strip
        private val QUAD_VERTS = 4
        private val FLOATS_PER_VERT = 4  // x, y, u, v

        private val VERT_SRC = """
            attribute vec2 aPos;
            attribute vec2 aTex;
            varying   vec2 vTex;
            void main() {
                gl_Position = vec4(aPos, 0.0, 1.0);
                vTex = aTex;
            }
        """.trimIndent()

        private val FRAG_SRC = """
            precision mediump float;
            uniform sampler2D uTex;
            varying vec2 vTex;
            void main() {
                gl_FragColor = texture2D(uTex, vTex);
            }
        """.trimIndent()

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            program = buildProgram(VERT_SRC, FRAG_SRC)

            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            texId = ids[0]

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLES20.glClearColor(0f, 0f, 0f, 1f)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            viewW = width
            viewH = height
            GLES20.glViewport(0, 0, width, height)
            rebuildQuad(Snes9xLib.nativeGetFrameWidth(), Snes9xLib.nativeGetFrameHeight())
        }

        override fun onDrawFrame(gl: GL10?) {
            // Run one SNES frame
            Snes9xLib.nativeRunFrame()

            // Feed audio
            val ao = audioOutput
            if (ao != null) {
                val n = Snes9xLib.nativeGetAudioSamples(audioScratch, audioScratch.size)
                if (n > 0) ao.write(audioScratch, n)
            }

            // Update texture if frame dimensions changed
            val fw = Snes9xLib.nativeGetFrameWidth()
            val fh = Snes9xLib.nativeGetFrameHeight()
            if (fw != texWidth || fh != texHeight) {
                rebuildQuad(fw, fh)
                texWidth  = fw
                texHeight = fh
            }

            // Upload frame buffer as RGB565 texture
            val pixels: ByteBuffer = Snes9xLib.nativeGetFrameBuffer()
            pixels.limit(fw * fh * 2)
            pixels.position(0)

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0,
                GLES20.GL_RGB,
                fw, fh, 0,
                GLES20.GL_RGB,
                GLES20.GL_UNSIGNED_SHORT_5_6_5,
                pixels
            )

            // Draw
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)

            val buf = quadBuffer ?: return
            buf.position(0)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, FLOATS_PER_VERT * 4, buf)
            GLES20.glEnableVertexAttribArray(aPos)

            buf.position(2)
            GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, FLOATS_PER_VERT * 4, buf)
            GLES20.glEnableVertexAttribArray(aTex)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTex"), 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, QUAD_VERTS)
        }

        /**
         * Build a letterboxed quad that preserves the SNES 4:3 aspect ratio
         * within the current viewport.
         */
        private fun rebuildQuad(fw: Int, fh: Int) {
            if (fw == 0 || fh == 0) return

            val frameAspect = 4f / 3f  // SNES nominal aspect ratio
            val viewAspect  = viewW.toFloat() / viewH.toFloat()

            // NDC extents of the quad
            val (x0, x1, y0, y1) = if (viewAspect > frameAspect) {
                // Viewport wider → pillarbox (black bars on sides)
                val scale = frameAspect / viewAspect
                floatArrayOf(-scale, scale, -1f, 1f)
            } else {
                // Viewport taller → letterbox (black bars on top/bottom)
                val scale = viewAspect / frameAspect
                floatArrayOf(-1f, 1f, -scale, scale)
            }

            // Triangle-strip order: TL, BL, TR, BR
            val verts = floatArrayOf(
                x0, y1,   0f, 0f,   // top-left
                x0, y0,   0f, 1f,   // bottom-left
                x1, y1,   1f, 0f,   // top-right
                x1, y0,   1f, 1f,   // bottom-right
            )
            quadBuffer = ByteBuffer
                .allocateDirect(verts.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(verts); position(0) }
        }

        // ── GL helpers ────────────────────────────────────────────────────────

        private fun buildProgram(vertSrc: String, fragSrc: String): Int {
            val vs = compileShader(GLES20.GL_VERTEX_SHADER,   vertSrc)
            val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc)
            return GLES20.glCreateProgram().also { p ->
                GLES20.glAttachShader(p, vs)
                GLES20.glAttachShader(p, fs)
                GLES20.glBindAttribLocation(p, aPos, "aPos")
                GLES20.glBindAttribLocation(p, aTex, "aTex")
                GLES20.glLinkProgram(p)
            }
        }

        private fun compileShader(type: Int, src: String): Int =
            GLES20.glCreateShader(type).also { s ->
                GLES20.glShaderSource(s, src)
                GLES20.glCompileShader(s)
            }
    }
}
