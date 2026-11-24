package app.lumora.editor.model

/** Built-in one-tap looks. All original parameter sets, free to use and tweak. */
object Looks {
    private fun s(vararg p: Pair<Float, Float>) = CurvePoints(listOf(0f to 0f) + p.toList() + listOf(1f to 1f))

    val all: List<Pair<String, EditParams>> = listOf(
        "Original" to EditParams(),
        "Clear" to EditParams(contrast = 0.12f, vibrance = 0.25f, sharpen = 0.3f),
        "Sunlit" to EditParams(exposure = 0.15f, warmth = 0.35f, vibrance = 0.2f, shadows = 0.2f),
        "Harbor" to EditParams(warmth = -0.3f, tint = -0.05f, contrast = 0.1f, fade = 0.15f),
        "Matte" to EditParams(fade = 0.45f, contrast = -0.1f, saturation = -0.15f,
            curves = Curves(master = s(0.25f to 0.3f, 0.75f to 0.72f))),
        "Pop" to EditParams(contrast = 0.25f, saturation = 0.25f, vibrance = 0.3f,
            curves = Curves(master = s(0.25f to 0.2f, 0.75f to 0.8f))),
        "Dusk" to EditParams(warmth = -0.2f, tint = 0.25f, shadows = 0.15f, vignette = 0.35f,
            curves = Curves(blue = s(0.3f to 0.38f), red = s(0.7f to 0.76f))),
        "Film 400" to EditParams(fade = 0.2f, grain = 0.35f, warmth = 0.12f, saturation = -0.1f,
            curves = Curves(master = s(0.2f to 0.24f, 0.8f to 0.78f), green = s(0.5f to 0.52f))),
        "Rangoli" to EditParams(vibrance = 0.45f, saturation = 0.15f, clarity = 0.25f, lightLeak = 0.35f, leakStyle = 3),
        "Bloom" to EditParams(glow = 0.45f, exposure = 0.1f, warmth = 0.15f, fade = 0.08f),
        "Drama" to EditParams(hdr = 0.6f, clarity = 0.3f, saturation = -0.1f, vignette = 0.3f),
        "Monsoon" to EditParams(warmth = -0.35f, dehaze = -0.2f, fade = 0.15f, saturation = -0.1f),
        "Clear Sky" to EditParams(dehaze = 0.45f, vibrance = 0.3f, clarity = 0.15f),
        "Golden" to EditParams(warmth = 0.4f, glow = 0.25f, lightLeak = 0.3f, leakStyle = 0, contrast = 0.08f),
        "Silver" to EditParams(saturation = -1f, contrast = 0.2f, sharpen = 0.2f),
        "Noir" to EditParams(saturation = -1f, contrast = 0.45f, vignette = 0.5f, grain = 0.2f,
            curves = Curves(master = s(0.3f to 0.2f, 0.7f to 0.8f))),
    )
}
