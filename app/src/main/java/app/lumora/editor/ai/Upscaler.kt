package app.lumora.editor.ai

import android.content.Context
import android.graphics.Bitmap
import app.lumora.editor.model.Tiles
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * On-device 4x super-resolution (ESRGAN, bundled in assets, Apache 2.0). Runs entirely on
 * the phone. The model takes 50x50 RGB tiles in 0..255 and returns 200x200 tiles.
 */
class Upscaler(context: Context) : AutoCloseable {
    private val interpreter: Interpreter

    init {
        val fd = context.assets.openFd(MODEL)
        val model = FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        fd.close()
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(threads))
    }

    /** Returns a new bitmap 4x the size of [src]. [progress] gets 0..1. */
    fun upscale4x(src: Bitmap, cancelled: () -> Boolean = { false }, progress: (Float) -> Unit = {}): Bitmap {
        val w = src.width; val h = src.height
        val inPx = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        val ow = w * SCALE; val oh = h * SCALE
        val outPx = IntArray(ow * oh)
        val input = ByteBuffer.allocateDirect(IN * IN * 3 * 4).order(ByteOrder.nativeOrder())
        val output = ByteBuffer.allocateDirect(OUT * OUT * 3 * 4).order(ByteOrder.nativeOrder())
        val inF = input.asFloatBuffer(); val outF = output.asFloatBuffer()
        val tile = FloatArray(OUT * OUT * 3)

        val plan = Tiles.plan(w, h, IN, PAD)
        plan.forEachIndexed { n, t ->
            if (cancelled()) throw java.util.concurrent.CancellationException("AI job replaced")
            inF.clear()
            for (ty in 0 until IN) {
                val sy = (t.readY + ty).coerceIn(0, h - 1)
                for (tx in 0 until IN) {
                    val p = inPx[sy * w + (t.readX + tx).coerceIn(0, w - 1)]
                    inF.put(((p shr 16) and 0xFF).toFloat())
                    inF.put(((p shr 8) and 0xFF).toFloat())
                    inF.put((p and 0xFF).toFloat())
                }
            }
            input.rewind(); output.rewind()
            interpreter.run(input, output)
            outF.rewind(); outF.get(tile)

            val offX = (t.x - t.readX) * SCALE; val offY = (t.y - t.readY) * SCALE
            for (ky in 0 until t.keepH * SCALE) {
                val row = (offY + ky) * OUT
                val dst = (t.y * SCALE + ky) * ow + t.x * SCALE
                for (kx in 0 until t.keepW * SCALE) {
                    val i = (row + offX + kx) * 3
                    outPx[dst + kx] = (0xFF shl 24) or (clamp(tile[i]) shl 16) or (clamp(tile[i + 1]) shl 8) or clamp(tile[i + 2])
                }
            }
            progress((n + 1f) / plan.size)
        }
        return Bitmap.createBitmap(outPx, ow, oh, Bitmap.Config.ARGB_8888)
    }

    override fun close() = interpreter.close()

    private fun clamp(v: Float) = v.toInt().coerceIn(0, 255)

    companion object {
        private const val MODEL = "esrgan_x4.tflite"
        private const val IN = 50
        private const val OUT = 200
        private const val PAD = 4
        const val SCALE = 4
    }
}
