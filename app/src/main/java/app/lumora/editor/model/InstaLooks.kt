package app.lumora.editor.model

/**
 * Social-feed filters for Instagram Studio. Original Vannamayam tunings that cover the
 * classic feed styles: warm vintage, faded film, cool tones, punchy contrast and mono.
 */
object InstaLooks {
    private fun c(vararg p: Pair<Float, Float>) = CurvePoints(listOf(0f to 0f) + p.toList() + listOf(1f to 1f))
    private fun lift(black: Float, white: Float = 1f) = CurvePoints(listOf(0f to black, 0.5f to (0.5f + black / 3), 1f to white))

    val all: List<Pair<String, EditParams>> = listOf(
        "None" to EditParams(),
        "Marigold" to EditParams(warmth = 0.35f, vibrance = 0.25f, contrast = 0.08f, curves = Curves(master = c(0.25f to 0.27f, 0.75f to 0.8f))),
        "Chai" to EditParams(warmth = 0.25f, fade = 0.25f, saturation = -0.15f, curves = Curves(red = c(0.5f to 0.55f), blue = lift(0.08f, 0.9f))),
        "Polaroid" to EditParams(fade = 0.35f, warmth = 0.15f, tint = 0.05f, contrast = -0.05f, vignette = 0.2f,
            curves = Curves(blue = lift(0.1f, 0.92f), green = c(0.5f to 0.52f))),
        "Disposable" to EditParams(exposure = 0.1f, contrast = 0.15f, warmth = 0.2f, grain = 0.45f, vignette = 0.35f, lightLeak = 0.2f, leakStyle = 0),
        "Y2K Flash" to EditParams(exposure = 0.25f, contrast = 0.3f, highlights = -0.2f, saturation = 0.1f, sharpen = 0.3f, vignette = 0.45f),
        "Beige" to EditParams(saturation = -0.35f, warmth = 0.2f, fade = 0.3f, contrast = -0.1f, curves = Curves(blue = lift(0.06f, 0.88f))),
        "Coastal" to EditParams(warmth = -0.3f, tint = -0.05f, vibrance = 0.3f, highlights = -0.15f, curves = Curves(blue = c(0.3f to 0.36f))),
        "Mist" to EditParams(warmth = -0.15f, fade = 0.4f, contrast = -0.15f, saturation = -0.2f, dehaze = -0.15f),
        "Neon Nights" to EditParams(tint = 0.3f, warmth = -0.25f, contrast = 0.25f, saturation = 0.3f, glow = 0.3f,
            curves = Curves(red = c(0.7f to 0.78f), blue = c(0.3f to 0.4f))),
        "Golden Hour" to EditParams(warmth = 0.45f, exposure = 0.1f, glow = 0.3f, shadows = 0.15f, lightLeak = 0.25f, leakStyle = 1),
        "Mango" to EditParams(warmth = 0.3f, vibrance = 0.5f, saturation = 0.15f, contrast = 0.12f),
        "Moody" to EditParams(exposure = -0.2f, contrast = 0.25f, saturation = -0.3f, warmth = -0.1f, clarity = 0.2f, vignette = 0.4f,
            curves = Curves(master = c(0.2f to 0.15f, 0.8f to 0.78f))),
        "Cinema" to EditParams(contrast = 0.2f, saturation = -0.1f, fade = 0.1f,
            curves = Curves(red = c(0.3f to 0.26f, 0.7f to 0.74f), blue = c(0.3f to 0.38f, 0.7f to 0.66f))),
        "Peach" to EditParams(tint = 0.15f, warmth = 0.2f, exposure = 0.12f, fade = 0.15f, highlights = -0.1f),
        "Mint" to EditParams(tint = -0.2f, warmth = -0.1f, fade = 0.2f, vibrance = 0.15f),
        "Lush" to EditParams(vibrance = 0.45f, clarity = 0.3f, dehaze = 0.2f, contrast = 0.1f),
        "Ink" to EditParams(saturation = -1f, contrast = 0.4f, clarity = 0.25f, curves = Curves(master = c(0.25f to 0.18f, 0.75f to 0.82f))),
        "Silverline" to EditParams(saturation = -1f, fade = 0.3f, contrast = -0.05f, grain = 0.2f),
        "Sepia Days" to EditParams(saturation = -0.85f, warmth = 0.55f, fade = 0.2f, vignette = 0.25f),
    )
}
