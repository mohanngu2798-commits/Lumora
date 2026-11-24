package app.lumora.editor.model

/**
 * The full, non-destructive description of an edit. The original photo is never modified;
 * the GPU pipeline re-renders from the source with these values every frame and on export.
 */
data class EditParams(
    val exposure: Float = 0f,     // stops, -2..2
    val contrast: Float = 0f,     // -1..1
    val highlights: Float = 0f,   // -1..1
    val shadows: Float = 0f,      // -1..1
    val saturation: Float = 0f,   // -1..1 (-1 = black & white)
    val vibrance: Float = 0f,     // -1..1
    val warmth: Float = 0f,       // -1..1
    val tint: Float = 0f,         // -1..1 (green .. magenta)
    val fade: Float = 0f,         // 0..1
    val sharpen: Float = 0f,      // 0..1
    val vignette: Float = 0f,     // -1..1 (negative = light vignette)
    val grain: Float = 0f,        // 0..1
    val curves: Curves = Curves(),
    val crop: CropSpec = CropSpec(),
    val lookName: String? = null,
) {
    /** Same geometry, no tonal changes. Used for the press-and-hold "compare" view. */
    fun originalLook(): EditParams = EditParams(crop = crop)

    /** Applies a look's tones while keeping the user's crop. */
    fun withLook(look: EditParams, name: String): EditParams = look.copy(crop = crop, lookName = name)
}

/**
 * Crop, rotate and flip. [aspect] is output width / height; null keeps the original shape.
 * [quarterTurns] rotates clockwise in 90 degree steps.
 */
data class CropSpec(
    val aspect: Float? = null,
    val quarterTurns: Int = 0,
    val flipHorizontal: Boolean = false,
) {
    val swapsSides: Boolean get() = quarterTurns % 2 != 0

    /** Normalized (0..1) centered crop rectangle in source-image space: [x, y, w, h]. */
    fun sourceRect(srcWidth: Int, srcHeight: Int): FloatArray {
        val target = aspect ?: return floatArrayOf(0f, 0f, 1f, 1f)
        val srcAspect = srcWidth.toFloat() / srcHeight
        // The aspect the user picked is for the rotated output; undo the rotation for source space.
        val a = if (swapsSides) 1f / target else target
        return if (srcAspect > a) {
            val w = a / srcAspect
            floatArrayOf((1f - w) / 2f, 0f, w, 1f)
        } else {
            val h = srcAspect / a
            floatArrayOf(0f, (1f - h) / 2f, 1f, h)
        }
    }

    /** Output size in pixels for a source of the given size. */
    fun outputSize(srcWidth: Int, srcHeight: Int): Pair<Int, Int> {
        val r = sourceRect(srcWidth, srcHeight)
        val w = (srcWidth * r[2]).toInt().coerceAtLeast(1)
        val h = (srcHeight * r[3]).toInt().coerceAtLeast(1)
        return if (swapsSides) h to w else w to h
    }
}
