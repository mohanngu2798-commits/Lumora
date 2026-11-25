package app.lumora.editor.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlin.math.max

/** Loads photos from the system picker and saves edits to the gallery. No permissions needed. */
class PhotoStore(private val context: Context) {

    /** Decodes [uri] (EXIF rotation applied) so its long edge is at most [maxEdge] pixels. */
    fun decode(uri: Uri, maxEdge: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // GLUtils can't upload hardware bitmaps.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width; val h = info.size.height
            val longEdge = max(w, h)
            if (longEdge > maxEdge) {
                val s = maxEdge.toFloat() / longEdge
                decoder.setTargetSize((w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1))
            }
        }.let { if (it.config != Bitmap.Config.ARGB_8888) it.copy(Bitmap.Config.ARGB_8888, false) else it }
    }

    /** Saves a JPEG to Pictures/Vannam and returns its content Uri. */
    fun saveJpeg(bitmap: Bitmap, quality: Int = 95, name: String = "Vannam_${System.currentTimeMillis()}"): Uri =
        save(bitmap, Bitmap.CompressFormat.JPEG, quality, "$name.jpg", "image/jpeg")

    /** Saves a PNG (keeps transparency, for cut-outs) to Pictures/Vannam. */
    fun savePng(bitmap: Bitmap, name: String = "Vannam_${System.currentTimeMillis()}"): Uri =
        save(bitmap, Bitmap.CompressFormat.PNG, 100, "$name.png", "image/png")

    private fun save(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int, file: String, mime: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Vannam")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Couldn't create the gallery entry")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                check(bitmap.compress(format, quality, out)) { "Image encoding failed" }
            } ?: error("Couldn't open the gallery entry")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
