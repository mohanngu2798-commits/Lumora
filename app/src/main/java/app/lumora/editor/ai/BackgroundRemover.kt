package app.lumora.editor.ai

import android.content.Context
import android.graphics.*
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * On-device background removal with IS-Net (general use, dichotomous image segmentation).
 * The model sees the photo at 1024×1024 and returns a soft mask; the mask is scaled back to the photo
 * so hair and soft edges stay natural.
 */
class BackgroundRemover(context: Context) : AutoCloseable {
    private val net = Interpreter(ModelFiles.map(context, MODEL), Interpreter.Options().setNumThreads(4))

    /** Returns the subject of [src] on a transparent background (same size as [src]). */
    fun cutout(src: Bitmap, progress: (Float) -> Unit = {}): Bitmap {
        progress(0.05f)
        val small = Bitmap.createScaledBitmap(src, SIZE, SIZE, true)
        val px = IntArray(SIZE * SIZE).also { small.getPixels(it, 0, SIZE, 0, 0, SIZE, SIZE) }
        if (small !== src) small.recycle()
        val input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder())
        for (c in px) {
            input.putFloat(((c shr 16) and 255) / 255f - 0.5f)
            input.putFloat(((c shr 8) and 255) / 255f - 0.5f)
            input.putFloat((c and 255) / 255f - 0.5f)
        }
        input.rewind()
        val output = ByteBuffer.allocateDirect(SIZE * SIZE * 4).order(ByteOrder.nativeOrder())
        progress(0.15f)
        net.run(input, output)
        progress(0.85f)
        output.rewind()
        val m = FloatArray(SIZE * SIZE) { output.float }
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
        for (v in m) { if (v < lo) lo = v; if (v > hi) hi = v }
        val range = (hi - lo).coerceAtLeast(1e-6f)
        // Normalise, then firm up the edges a little while keeping soft hair.
        for (i in px.indices) {
            val a = ((m[i] - lo) / range - EDGE_LOW) / (1f - EDGE_LOW - EDGE_HIGH)
            px[i] = (a.coerceIn(0f, 1f) * 255f).toInt() shl 24
        }
        val maskSmall = Bitmap.createBitmap(px, SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val mask = Bitmap.createScaledBitmap(maskSmall, src.width, src.height, true)
        maskSmall.recycle()
        val n = src.width * src.height
        val alpha = IntArray(n).also { mask.getPixels(it, 0, src.width, 0, 0, src.width, src.height) }
        mask.recycle()
        val colour = IntArray(n).also { src.getPixels(it, 0, src.width, 0, 0, src.width, src.height) }
        for (i in 0 until n) colour[i] = (alpha[i] and 0xFF000000.toInt()) or (colour[i] and 0xFFFFFF)
        progress(1f)
        return Bitmap.createBitmap(colour, src.width, src.height, Bitmap.Config.ARGB_8888).also { it.setHasAlpha(true) }
    }

    override fun close() = net.close()

    companion object {
        const val MODEL = "isnet_general_fp16.tflite"
        private const val SIZE = 1024
        private const val EDGE_LOW = 0.06f
        private const val EDGE_HIGH = 0.06f

        fun available(context: Context) = ModelFiles.exists(context, MODEL)

        /** Puts [cut] over a new background. [photo] is used for [Backdrop.Photo]. */
        fun compose(original: Bitmap, cut: Bitmap, backdrop: Backdrop, photo: Bitmap? = null): Bitmap {
            if (backdrop == Backdrop.Clear) return cut.copy(Bitmap.Config.ARGB_8888, false)
            val out = Bitmap.createBitmap(cut.width, cut.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val full = Rect(0, 0, out.width, out.height)
            when (backdrop) {
                Backdrop.Blur -> {
                    // Downscale-then-upscale twice gives a smooth, cheap background blur.
                    val f = 48f / maxOf(original.width, original.height)
                    val tiny = Bitmap.createScaledBitmap(original, maxOf(1, (original.width * f).toInt()), maxOf(1, (original.height * f).toInt()), true)
                    val mid = Bitmap.createScaledBitmap(tiny, tiny.width * 4, tiny.height * 4, true)
                    c.drawBitmap(mid, null, full, paint)
                    tiny.recycle(); mid.recycle()
                    c.drawColor(Color.argb(30, 255, 255, 255))
                }
                Backdrop.Photo -> if (photo != null) {
                    // Centre-crop the chosen photo to fill the frame.
                    val s = maxOf(out.width.toFloat() / photo.width, out.height.toFloat() / photo.height)
                    val w = (out.width / s).toInt(); val h = (out.height / s).toInt()
                    val l = (photo.width - w) / 2; val t = (photo.height - h) / 2
                    c.drawBitmap(photo, Rect(l, t, l + w, t + h), full, paint)
                } else c.drawColor(Color.WHITE)
                else -> c.drawColor(backdrop.colour)
            }
            c.drawBitmap(cut, 0f, 0f, paint)
            return out
        }
    }
}

enum class Backdrop(val label: String, val colour: Int) {
    Clear("Transparent", Color.TRANSPARENT),
    White("White", Color.WHITE),
    Black("Black", Color.BLACK),
    Cream("Cream", 0xFFF4EBDD.toInt()),
    Blush("Blush", 0xFFF6D6DA.toInt()),
    Sky("Sky", 0xFFCFE3F5.toInt()),
    Mint("Mint", 0xFFD5EEDD.toInt()),
    Jasmine("Jasmine", 0xFFFFF8E7.toInt()),
    Blur("Blur", Color.TRANSPARENT),
    Photo("Photo", Color.TRANSPARENT),
}
