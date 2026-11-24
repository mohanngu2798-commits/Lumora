package app.lumora.editor.gl

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import app.lumora.editor.model.EditParams
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Live, GPU-rendered preview. Re-renders only when the image or the edit changes. */
class PreviewView(context: Context) : GLSurfaceView(context) {
    private val renderer = PreviewRenderer()
    private var shownBitmap: Bitmap? = null

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun show(bitmap: Bitmap?, params: EditParams) {
        if (bitmap !== shownBitmap) {
            shownBitmap = bitmap
            queueEvent { renderer.pendingBitmap = bitmap }
        }
        renderer.params = params
        requestRender()
    }

    private class PreviewRenderer : Renderer {
        @Volatile var params = EditParams()
        var pendingBitmap: Bitmap? = null
        private var image: ImageRenderer? = null
        private var viewW = 0
        private var viewH = 0

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            image = ImageRenderer()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            viewW = width; viewH = height
        }

        override fun onDrawFrame(gl: GL10?) {
            val r = image ?: return
            pendingBitmap?.let { if (!it.isRecycled) r.setImage(it) }
            pendingBitmap = null

            GLES30.glClearColor(0.059f, 0.067f, 0.082f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            if (!r.hasImage) return

            // Letterbox the output into the view, keeping its aspect ratio.
            val p = params
            val (ow, oh) = p.crop.outputSize(r.sourceWidth, r.sourceHeight)
            val scale = minOf(viewW.toFloat() / ow, viewH.toFloat() / oh)
            val w = (ow * scale).toInt(); val h = (oh * scale).toInt()
            r.draw(p, (viewW - w) / 2, (viewH - h) / 2, w, h)
        }
    }
}
