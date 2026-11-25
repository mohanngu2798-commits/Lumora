package app.lumora.editor.ai

import android.content.Context
import android.graphics.*
import app.lumora.editor.model.Face
import app.lumora.editor.model.FaceMath
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max

/**
 * Remini-style face restoration, entirely on the phone:
 * 1. BlazeFace (full range) finds faces and eye/nose/mouth points.
 * 2. Each face is aligned to the FFHQ 512×512 template.
 * 3. GFPGAN v1.4 redraws the face with realistic detail.
 * 4. The restored face is blended back with a feathered edge.
 */
class FaceRestorer(private val context: Context) : AutoCloseable {
    private val detector = Interpreter(map(DETECTOR), Interpreter.Options().setNumThreads(2))
    private val gfpgan: Interpreter? = if (available(context)) Interpreter(map(GFPGAN), Interpreter.Options().setNumThreads(4)) else null

    /**
     * Finds faces; coordinates are returned in [src] pixels. Small faces (a person far away in a tall photo)
     * vanish when the whole photo is squeezed into the detector's 192 px input, so the photo is also scanned
     * in overlapping square windows and the results merged.
     */
    fun detect(src: Bitmap): List<Face> {
        val w = src.width; val h = src.height
        val windows = ArrayList<android.graphics.Rect>()
        windows += android.graphics.Rect(0, 0, w, h)
        for (side in listOf(minOf(w, h), minOf(w, h) / 2)) {
            if (side < DET) continue
            val step = side / 2
            var y = 0
            while (true) {
                var x = 0
                while (true) {
                    windows += android.graphics.Rect(x, y, minOf(x + side, w), minOf(y + side, h))
                    if (x + side >= w) break
                    x = minOf(x + step, w - side)
                }
                if (y + side >= h) break
                y = minOf(y + step, h - side)
            }
        }
        val all = windows.flatMap { detectIn(src, it) }.sortedByDescending { it.score }
        val kept = ArrayList<Face>()
        for (f in all) if (kept.none { iou(it, f) > 0.3f }) kept += f
        return kept.take(MAX_FACES)
    }

    private fun iou(a: Face, b: Face): Float {
        val ix = maxOf(0f, minOf(a.right, b.right) - maxOf(a.left, b.left))
        val iy = maxOf(0f, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))
        val inter = ix * iy
        val union = (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun detectIn(src: Bitmap, r: android.graphics.Rect): List<Face> {
        val side = max(r.width(), r.height())
        val ox = (side - r.width()) / 2f - r.left; val oy = (side - r.height()) / 2f - r.top
        val sq = Bitmap.createBitmap(DET, DET, Bitmap.Config.ARGB_8888)
        Canvas(sq).apply {
            val s = DET.toFloat() / side
            clipRect((r.left + ox) * s, (r.top + oy) * s, (r.right + ox) * s, (r.bottom + oy) * s)
            drawBitmap(src, Matrix().apply { setScale(s, s); postTranslate(ox * s, oy * s) }, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val px = IntArray(DET * DET).also { sq.getPixels(it, 0, DET, 0, 0, DET, DET) }; sq.recycle()
        val input = ByteBuffer.allocateDirect(DET * DET * 3 * 4).order(ByteOrder.nativeOrder())
        for (p in px) {
            input.putFloat(((p shr 16) and 0xFF) / 127.5f - 1f)
            input.putFloat(((p shr 8) and 0xFF) / 127.5f - 1f)
            input.putFloat((p and 0xFF) / 127.5f - 1f)
        }
        input.rewind()
        val reg = Array(1) { Array(2304) { FloatArray(16) } }
        val cls = Array(1) { Array(2304) { FloatArray(1) } }
        detector.runForMultipleInputsOutputs(arrayOf(input), mapOf(0 to reg, 1 to cls))
        val flatReg = FloatArray(2304 * 16).also { a -> for (i in 0 until 2304) System.arraycopy(reg[0][i], 0, a, i * 16, 16) }
        val flatCls = FloatArray(2304) { cls[0][it][0] }
        fun tx(x: Float) = x * side - ox
        fun ty(y: Float) = y * side - oy
        return FaceMath.decode(flatReg, flatCls, minScore = 0.55f).map { f ->
            Face(tx(f.left), ty(f.top), tx(f.right), ty(f.bottom), f.keypoints.map { tx(it.first) to ty(it.second) }, f.score)
        }
    }

    /**
     * Restores every face of [original] and blends them onto [canvasBitmap] (same size, mutable),
     * e.g. an AI-enhanced copy of the photo. Returns how many faces were restored.
     */
    /** Faces found by the last [restoreFaces] call, in photo pixels. */
    var lastFaces: List<Face> = emptyList()
        private set

    fun restoreFaces(original: Bitmap, canvasBitmap: Bitmap, progress: (Float) -> Unit = {}): Int {
        val model = gfpgan ?: return 0
        val faces = detect(original).filter { (it.right - it.left) > 24f }.take(MAX_FACES)
        lastFaces = faces
        val canvas = Canvas(canvasBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val mask = featherMask()
        faces.forEachIndexed { n, f ->
            val m = FaceMath.similarity(f.keypoints, FaceMath.FFHQ_512)
            val toCrop = Matrix().apply { setValues(floatArrayOf(m[0], -m[1], m[2], m[1], m[0], m[3], 0f, 0f, 1f)) }
            val crop = Bitmap.createBitmap(FACE, FACE, Bitmap.Config.ARGB_8888)
            Canvas(crop).drawBitmap(original, toCrop, paint)
            val restored = runGfpgan(model, crop); crop.recycle()
            // Feather the square so only the face blends in, then map it back.
            Canvas(restored).drawBitmap(mask, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
            val back = Matrix().also { toCrop.invert(it) }
            canvas.drawBitmap(restored, back, paint)
            restored.recycle()
            progress((n + 1f) / faces.size)
        }
        mask.recycle()
        return faces.size
    }

    private fun runGfpgan(model: Interpreter, crop: Bitmap): Bitmap {
        val px = IntArray(FACE * FACE).also { crop.getPixels(it, 0, FACE, 0, 0, FACE, FACE) }
        val input = ByteBuffer.allocateDirect(FACE * FACE * 3 * 4).order(ByteOrder.nativeOrder())
        for (p in px) {
            input.putFloat(((p shr 16) and 0xFF) / 127.5f - 1f)
            input.putFloat(((p shr 8) and 0xFF) / 127.5f - 1f)
            input.putFloat((p and 0xFF) / 127.5f - 1f)
        }
        input.rewind()
        val output = ByteBuffer.allocateDirect(FACE * FACE * 3 * 4).order(ByteOrder.nativeOrder())
        model.run(input, output)
        output.rewind()
        val f = output.asFloatBuffer()
        for (i in px.indices) {
            val r = ((f.get() + 1f) * 127.5f).toInt().coerceIn(0, 255)
            val g = ((f.get() + 1f) * 127.5f).toInt().coerceIn(0, 255)
            val b = ((f.get() + 1f) * 127.5f).toInt().coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(px, FACE, FACE, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true)
    }

    /** Opaque centre fading to transparent edges, so pasted faces have no visible seam. */
    private fun featherMask(): Bitmap {
        val b = Bitmap.createBitmap(FACE, FACE, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            maskFilter = BlurMaskFilter(FACE * 0.07f, BlurMaskFilter.Blur.NORMAL)
        }
        val inset = FACE * 0.1f
        c.drawRoundRect(RectF(inset, inset, FACE - inset, FACE - inset), FACE * 0.18f, FACE * 0.18f, p)
        return b
    }

    private fun map(name: String): ByteBuffer {
        val fd = context.assets.openFd(name)
        return FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            .also { fd.close() }
    }

    override fun close() { detector.close(); gfpgan?.close() }

    companion object {
        private const val DETECTOR = "face_detector.tflite"
        private const val GFPGAN = "gfpgan_v1_4.tflite"
        private const val DET = 192
        private const val FACE = 512
        private const val MAX_FACES = 8

        fun available(context: Context) = runCatching { context.assets.openFd(GFPGAN).close(); true }.getOrDefault(false)
    }
}
