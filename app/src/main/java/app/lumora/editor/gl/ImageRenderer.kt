package app.lumora.editor.gl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import app.lumora.editor.model.Curves
import app.lumora.editor.model.EditParams
import app.lumora.editor.model.LeakStyles
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Draws a source photo through the edit pipeline. Must be created and used on a thread
 * with a current OpenGL ES 3 context. Shared by the live preview and the full-size export.
 */
class ImageRenderer {
    private val program = buildProgram(Shaders.VERTEX, Shaders.FRAGMENT)
    private val imageTex: Int
    private val curveTex: Int
    private val blurMTex: Int
    private val blurLTex: Int
    private var blurMSize = 1 to 1
    private var blurLSize = 1 to 1
    private val vbo: Int
    private val uniforms = HashMap<String, Int>()
    private var lastCurves: Curves? = null

    var sourceWidth = 0; private set
    var sourceHeight = 0; private set
    val hasImage get() = sourceWidth > 0

    init {
        val ids = IntArray(4)
        GLES30.glGenTextures(4, ids, 0)
        imageTex = ids[0]; curveTex = ids[1]; blurMTex = ids[2]; blurLTex = ids[3]
        for (t in ids) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        }
        uploadCurves(Curves())

        val quad = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val buf = ByteBuffer.allocateDirect(quad.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(quad).position(0)
        val b = IntArray(1)
        GLES30.glGenBuffers(1, b, 0)
        vbo = b[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, buf, GLES30.GL_STATIC_DRAW)
    }

    fun setImage(bitmap: Bitmap) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, imageTex)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        sourceWidth = bitmap.width
        sourceHeight = bitmap.height
        // Blur sizes are relative to the photo, so the preview and the full-size export match.
        val m = Blur.downscale(bitmap, BLUR_M_EDGE)
        val l = Blur.downscale(m, BLUR_L_EDGE)
        upload(blurMTex, m); blurMSize = m.width to m.height
        upload(blurLTex, l); blurLSize = l.width to l.height
        m.recycle(); l.recycle()
    }

    private fun upload(tex: Int, bmp: Bitmap) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
    }

    private fun uploadCurves(curves: Curves) {
        val lut = ByteBuffer.allocateDirect(256 * 4).put(curves.toLut()).position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, curveTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 256, 1, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, lut)
        lastCurves = curves
    }

    /** Renders into the currently bound framebuffer, using the given viewport. */
    fun draw(p: EditParams, vx: Int, vy: Int, vw: Int, vh: Int, forReadback: Boolean = false) {
        if (!hasImage) return
        if (p.curves != lastCurves) uploadCurves(p.curves)

        GLES30.glViewport(vx, vy, vw, vh)
        GLES30.glUseProgram(program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, imageTex)
        GLES30.glUniform1i(u("uImage"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, curveTex)
        GLES30.glUniform1i(u("uCurve"), 1)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, blurMTex)
        GLES30.glUniform1i(u("uBlurM"), 2)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, blurLTex)
        GLES30.glUniform1i(u("uBlurL"), 3)
        GLES30.glUniform2f(u("uBlurMTexel"), 1f / blurMSize.first, 1f / blurMSize.second)
        GLES30.glUniform2f(u("uBlurLTexel"), 1f / blurLSize.first, 1f / blurLSize.second)

        val crop = p.crop.sourceRect(sourceWidth, sourceHeight)
        GLES30.glUniform4f(u("uCrop"), crop[0], crop[1], crop[2], crop[3])
        GLES30.glUniform1i(u("uQuarterTurns"), ((p.crop.quarterTurns % 4) + 4) % 4)
        GLES30.glUniform1f(u("uFlipH"), if (p.crop.flipHorizontal) 1f else 0f)
        GLES30.glUniform1f(u("uFlipOutputY"), if (forReadback) 1f else 0f)
        GLES30.glUniform2f(u("uTexel"), 1f / sourceWidth, 1f / sourceHeight)
        GLES30.glUniform2f(u("uOutSize"), vw.toFloat(), vh.toFloat())

        GLES30.glUniform1f(u("uExposure"), p.exposure)
        GLES30.glUniform1f(u("uContrast"), p.contrast)
        GLES30.glUniform1f(u("uHighlights"), p.highlights)
        GLES30.glUniform1f(u("uShadows"), p.shadows)
        GLES30.glUniform1f(u("uSaturation"), p.saturation)
        GLES30.glUniform1f(u("uVibrance"), p.vibrance)
        GLES30.glUniform1f(u("uWarmth"), p.warmth)
        GLES30.glUniform1f(u("uTint"), p.tint)
        GLES30.glUniform1f(u("uFade"), p.fade)
        GLES30.glUniform1f(u("uSharpen"), p.sharpen)
        GLES30.glUniform1f(u("uVignette"), p.vignette)
        GLES30.glUniform1f(u("uGrain"), p.grain)
        GLES30.glUniform1f(u("uClarity"), p.clarity)
        GLES30.glUniform1f(u("uDehaze"), p.dehaze)
        GLES30.glUniform1f(u("uHdr"), p.hdr)
        GLES30.glUniform1f(u("uGlow"), p.glow)
        GLES30.glUniform1f(u("uLensBlur"), p.lensBlur)
        GLES30.glUniform1i(u("uBlurShape"), p.blurShape.ordinal)
        GLES30.glUniform1f(u("uLeak"), p.lightLeak)
        val (ca, cb) = LeakStyles.all[p.leakStyle.coerceIn(0, LeakStyles.all.lastIndex)].second
        GLES30.glUniform3f(u("uLeakA"), ((ca shr 16) and 0xFF) / 255f, ((ca shr 8) and 0xFF) / 255f, (ca and 0xFF) / 255f)
        GLES30.glUniform3f(u("uLeakB"), ((cb shr 16) and 0xFF) / 255f, ((cb shr 8) and 0xFF) / 255f, (cb and 0xFF) / 255f)
        GLES30.glUniform1f(u("uOutAspect"), vw.toFloat() / vh)

        val pos = GLES30.glGetAttribLocation(program, "aPos")
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glEnableVertexAttribArray(pos)
        GLES30.glVertexAttribPointer(pos, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(pos)
    }

    fun release() {
        GLES30.glDeleteTextures(4, intArrayOf(imageTex, curveTex, blurMTex, blurLTex), 0)
        GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
        GLES30.glDeleteProgram(program)
    }

    private fun u(name: String) = uniforms.getOrPut(name) { GLES30.glGetUniformLocation(program, name) }

    private companion object {
        const val BLUR_M_EDGE = 160
        const val BLUR_L_EDGE = 40
        fun compile(type: Int, src: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, src)
            GLES30.glCompileShader(s)
            val ok = IntArray(1)
            GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "Shader compile failed: " + GLES30.glGetShaderInfoLog(s) }
            return s
        }

        fun buildProgram(vs: String, fs: String): Int {
            val p = GLES30.glCreateProgram()
            GLES30.glAttachShader(p, compile(GLES30.GL_VERTEX_SHADER, vs))
            GLES30.glAttachShader(p, compile(GLES30.GL_FRAGMENT_SHADER, fs))
            GLES30.glLinkProgram(p)
            val ok = IntArray(1)
            GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
            check(ok[0] != 0) { "Program link failed: " + GLES30.glGetProgramInfoLog(p) }
            return p
        }
    }
}
