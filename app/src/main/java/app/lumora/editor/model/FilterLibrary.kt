package app.lumora.editor.model

/**
 * Every one-tap filter in the editor, grouped by mood. All tunings are original to Vannam.
 * Names are unique across groups because a draft remembers its look by name.
 */
data class FilterGroup(val name: String, val filters: List<Pair<String, EditParams>>)

object FilterLibrary {
    private fun c(vararg p: Pair<Float, Float>) = CurvePoints(listOf(0f to 0f) + p.toList() + listOf(1f to 1f))
    /** A curve that lifts blacks to [black] and pulls whites to [white]. */
    private fun lift(black: Float, white: Float = 1f) = CurvePoints(listOf(0f to black, 0.5f to (black + (white - black) * 0.5f), 1f to white))
    private val sCurve = c(0.25f to 0.2f, 0.75f to 0.8f)
    private val softS = c(0.25f to 0.23f, 0.75f to 0.78f)
    private val matte = c(0.0f to 0.08f, 0.25f to 0.28f, 0.75f to 0.74f)

    /** Split tone helper: shadow colour, highlight colour, amount. */
    private fun EditParams.tone(s: Int, h: Int, a: Float) = copy(toneShadow = s, toneHighlight = h, toneAmount = a)

    val exclusive = FilterGroup("Vannam Exclusive", listOf(
        "Madurai Jasmine" to EditParams(exposure = 0.12f, contrast = -0.1f, fade = 0.18f, warmth = 0.1f, glow = 0.2f, highlights = -0.15f)
            .tone(0xB07A86, 0xFFF3DC, 0.3f),
        "Filter Coffee" to EditParams(warmth = 0.3f, fade = 0.25f, saturation = -0.25f, contrast = 0.08f, grain = 0.15f,
            curves = Curves(master = matte)).tone(0x4A2E22, 0xF2D3A8, 0.35f),
        "Kolam" to EditParams(vibrance = 0.4f, clarity = 0.2f, fade = 0.15f, grain = 0.12f, lightLeak = 0.2f, leakStyle = 3,
            curves = Curves(master = softS)),
        "Old Madras" to EditParams(saturation = -0.4f, fade = 0.25f, warmth = 0.2f, vignette = 0.3f, grain = 0.25f)
            .tone(0x1E4D57, 0xF4D9A6, 0.45f),
        "Temple Gold" to EditParams(warmth = 0.35f, contrast = 0.15f, clarity = 0.2f, glow = 0.2f, vignette = 0.3f)
            .tone(0x3B1F0E, 0xFFD27A, 0.35f),
        "Lotus" to EditParams(vibrance = 0.2f, fade = 0.12f, contrast = 0.05f).tone(0x1E6E6B, 0xFF9EC0, 0.4f),
        "Cotton Candy" to EditParams(exposure = 0.15f, contrast = -0.15f, fade = 0.25f, saturation = -0.1f)
            .tone(0x8FB8FF, 0xFFB8D9, 0.55f),
        "Silk Saree" to EditParams(vibrance = 0.45f, saturation = 0.15f, glow = 0.3f, contrast = 0.12f, warmth = 0.1f,
            curves = Curves(red = c(0.5f to 0.55f))),
        "Monsoon Film" to EditParams(warmth = -0.25f, fade = 0.2f, grain = 0.25f, dehaze = -0.15f, saturation = -0.15f)
            .tone(0x23404A, 0xDDE7E3, 0.35f),
        "Turmeric" to EditParams(warmth = 0.4f, vibrance = 0.3f, contrast = 0.1f).tone(0x6B3A12, 0xFFD34D, 0.3f),
        "Clean Girl" to EditParams(exposure = 0.2f, contrast = -0.12f, highlights = -0.2f, warmth = 0.08f, saturation = -0.1f,
            fade = 0.1f, glow = 0.12f),
        "Dark Academia" to EditParams(exposure = -0.15f, contrast = 0.2f, saturation = -0.35f, warmth = 0.2f, vignette = 0.4f,
            grain = 0.2f).tone(0x1F3326, 0xC9A26B, 0.4f),
        "Cottagecore" to EditParams(warmth = 0.15f, tint = -0.08f, fade = 0.25f, glow = 0.18f, saturation = -0.1f)
            .tone(0x4C6B45, 0xFFF0C9, 0.3f),
        "Coquette" to EditParams(exposure = 0.18f, contrast = -0.1f, fade = 0.2f, tint = 0.12f, glow = 0.15f)
            .tone(0xC07A92, 0xFFE3EC, 0.45f),
        "Vaporwave" to EditParams(contrast = 0.15f, saturation = 0.2f, glow = 0.2f).tone(0x5A2BD8, 0xFF7AD9, 0.5f),
        "Dreamcore" to EditParams(glow = 0.5f, fade = 0.2f, exposure = 0.1f, saturation = -0.15f, lensBlur = 0.2f)
            .tone(0x6E78C8, 0xFFE7F2, 0.35f),
        "Retro Bollywood" to EditParams(warmth = 0.35f, saturation = 0.2f, glow = 0.3f, fade = 0.15f, grain = 0.2f,
            lightLeak = 0.35f, leakStyle = 1).tone(0x5B1B2E, 0xFFC88A, 0.3f),
        "Sea Breeze" to EditParams(exposure = 0.1f, warmth = -0.2f, vibrance = 0.25f, fade = 0.12f).tone(0x1C6E8C, 0xF5F0E0, 0.3f),
        "Indigo Night" to EditParams(exposure = -0.15f, contrast = 0.2f, saturation = -0.2f, vignette = 0.35f)
            .tone(0x1A1F5C, 0xB7C6FF, 0.5f),
        "Mehendi" to EditParams(warmth = 0.25f, tint = -0.1f, clarity = 0.25f, saturation = -0.15f).tone(0x3A2A12, 0xD9B36B, 0.4f),
        "Rose Gold" to EditParams(warmth = 0.15f, tint = 0.1f, glow = 0.2f, fade = 0.1f).tone(0x7A4A4A, 0xFFD2BF, 0.4f),
        "Pastel Pop" to EditParams(exposure = 0.12f, contrast = -0.2f, vibrance = 0.35f, fade = 0.3f),
    ))

    val film = FilterGroup("Film", listOf(
        "Portrait Film" to EditParams(warmth = 0.12f, fade = 0.12f, saturation = -0.1f, grain = 0.18f,
            curves = Curves(master = softS, red = c(0.5f to 0.53f))),
        "Slide Film" to EditParams(contrast = 0.3f, saturation = 0.3f, vibrance = 0.2f, curves = Curves(master = sCurve, blue = c(0.3f to 0.34f))),
        "Expired" to EditParams(fade = 0.3f, tint = 0.15f, warmth = 0.2f, grain = 0.35f, saturation = -0.15f,
            curves = Curves(green = lift(0.06f, 0.92f), blue = c(0.5f to 0.45f))),
        "Cross Process" to EditParams(contrast = 0.2f, saturation = 0.2f,
            curves = Curves(red = c(0.25f to 0.2f, 0.75f to 0.85f), green = c(0.25f to 0.22f, 0.75f to 0.82f), blue = lift(0.2f, 0.8f))),
        "Bleach Bypass" to EditParams(saturation = -0.6f, contrast = 0.35f, clarity = 0.3f, sharpen = 0.2f, curves = Curves(master = sCurve)),
        "Chrome" to EditParams(contrast = 0.15f, saturation = -0.2f, clarity = 0.15f, curves = Curves(blue = c(0.25f to 0.3f), red = c(0.75f to 0.77f))),
        "Velvet" to EditParams(saturation = 0.35f, contrast = 0.2f, vibrance = 0.15f, curves = Curves(master = sCurve, green = c(0.5f to 0.53f))),
        "Faded Print" to EditParams(fade = 0.45f, saturation = -0.2f, warmth = 0.1f, curves = Curves(master = matte)),
        "Superia" to EditParams(tint = -0.1f, warmth = -0.05f, contrast = 0.1f, grain = 0.2f, curves = Curves(green = c(0.5f to 0.54f))),
        "Gold 200" to EditParams(warmth = 0.3f, saturation = 0.1f, grain = 0.22f, fade = 0.08f, curves = Curves(master = softS)),
        "Cine Tungsten" to EditParams(warmth = -0.4f, contrast = 0.1f, glow = 0.15f, grain = 0.2f),
        "Instant" to EditParams(fade = 0.35f, contrast = -0.05f, warmth = 0.15f, vignette = 0.25f, curves = Curves(blue = lift(0.12f, 0.9f))),
    ))

    val lomo = FilterGroup("Lomo & Toy", listOf(
        "Lomo" to EditParams(contrast = 0.35f, saturation = 0.3f, vignette = 0.6f, curves = Curves(master = sCurve)),
        "Lomo Warm" to EditParams(contrast = 0.3f, saturation = 0.25f, warmth = 0.3f, vignette = 0.55f, lightLeak = 0.2f, leakStyle = 0),
        "Lomo Blue" to EditParams(contrast = 0.3f, warmth = -0.35f, saturation = 0.2f, vignette = 0.55f,
            curves = Curves(blue = c(0.3f to 0.4f), red = c(0.5f to 0.45f))),
        "Lomo Purple" to EditParams(contrast = 0.25f, tint = 0.3f, saturation = 0.2f, vignette = 0.5f, curves = Curves(green = c(0.5f to 0.44f))),
        "Toy Camera" to EditParams(contrast = 0.3f, saturation = 0.4f, vignette = 0.7f, lensBlur = 0.35f, grain = 0.2f),
        "Plastic Lens" to EditParams(glow = 0.35f, vignette = 0.6f, saturation = 0.2f, lensBlur = 0.25f, lightLeak = 0.3f, leakStyle = 2),
        "Pinhole" to EditParams(saturation = -0.7f, contrast = 0.2f, vignette = 0.8f, grain = 0.35f, lensBlur = 0.3f),
        "Redscale" to EditParams(contrast = 0.15f, saturation = -0.3f).tone(0x5A0A00, 0xFFC060, 0.7f),
        "Light Leak" to EditParams(lightLeak = 0.55f, leakStyle = 0, fade = 0.12f, warmth = 0.15f),
        "Sun Flare" to EditParams(lightLeak = 0.5f, leakStyle = 1, glow = 0.3f, exposure = 0.1f),
    ))

    val vintage = FilterGroup("Vintage", listOf(
        "1970" to EditParams(warmth = 0.35f, fade = 0.3f, saturation = -0.1f, grain = 0.2f, curves = Curves(blue = lift(0.12f, 0.85f))),
        "1985" to EditParams(contrast = 0.2f, saturation = 0.25f, tint = 0.1f, fade = 0.12f, grain = 0.15f, curves = Curves(red = c(0.6f to 0.66f))),
        "VHS" to EditParams(contrast = -0.1f, saturation = 0.15f, tint = 0.1f, fade = 0.2f, grain = 0.4f, glow = 0.15f,
            curves = Curves(blue = c(0.3f to 0.38f))),
        "Sepia" to EditParams(saturation = -1f, contrast = 0.05f).tone(0x3B2412, 0xF0D9AE, 0.8f),
        "Tintype" to EditParams(saturation = -1f, contrast = 0.35f, vignette = 0.6f, grain = 0.3f).tone(0x2A2520, 0xD8CDB6, 0.55f),
        "Cyanotype" to EditParams(saturation = -1f, contrast = 0.2f).tone(0x0B2B5C, 0xDCEBFF, 0.85f),
        "Antique" to EditParams(saturation = -0.6f, fade = 0.3f, warmth = 0.3f, vignette = 0.45f, grain = 0.3f),
        "Postcard" to EditParams(saturation = 0.25f, fade = 0.2f, warmth = 0.12f, curves = Curves(blue = lift(0.1f, 0.9f))),
        "Old Album" to EditParams(fade = 0.35f, warmth = 0.25f, saturation = -0.3f, tint = 0.05f, vignette = 0.3f),
        "Retro Teal" to EditParams(fade = 0.2f).tone(0x1D5B5E, 0xF6D7A8, 0.4f),
    ))

    val portrait = FilterGroup("Portrait", listOf(
        "Soft Skin" to EditParams(clarity = -0.3f, exposure = 0.08f, warmth = 0.08f, highlights = -0.1f, glow = 0.12f),
        "Rosy" to EditParams(tint = 0.12f, warmth = 0.12f, exposure = 0.1f, clarity = -0.15f, vibrance = 0.1f),
        "Porcelain" to EditParams(exposure = 0.2f, saturation = -0.2f, contrast = -0.05f, clarity = -0.2f, warmth = -0.05f),
        "Bronze" to EditParams(warmth = 0.3f, contrast = 0.15f, vibrance = 0.15f, clarity = 0.1f).tone(0x4A2A14, 0xFFD6A8, 0.25f),
        "Studio" to EditParams(contrast = 0.15f, clarity = 0.15f, vignette = 0.25f, highlights = -0.15f, shadows = 0.1f),
        "Glow Up" to EditParams(glow = 0.35f, exposure = 0.12f, clarity = -0.1f, vibrance = 0.15f),
        "Dewy" to EditParams(glow = 0.25f, fade = 0.1f, tint = 0.05f, highlights = -0.1f, exposure = 0.1f),
        "Natural Light" to EditParams(shadows = 0.25f, highlights = -0.2f, vibrance = 0.15f, warmth = 0.05f),
    ))

    val travel = FilterGroup("Travel & Nature", listOf(
        "Vivid Sky" to EditParams(dehaze = 0.3f, vibrance = 0.4f, highlights = -0.3f, curves = Curves(blue = c(0.5f to 0.55f))),
        "Forest" to EditParams(tint = -0.12f, clarity = 0.25f, vibrance = 0.2f, shadows = 0.15f, curves = Curves(green = c(0.5f to 0.54f))),
        "Desert" to EditParams(warmth = 0.4f, clarity = 0.2f, dehaze = 0.15f, contrast = 0.1f).tone(0x6B3A1A, 0xFFD9A0, 0.2f),
        "Snow" to EditParams(exposure = 0.25f, warmth = -0.15f, highlights = -0.2f, saturation = -0.1f, clarity = 0.1f),
        "Ocean" to EditParams(warmth = -0.25f, vibrance = 0.35f, dehaze = 0.2f, curves = Curves(blue = c(0.3f to 0.36f))),
        "Autumn" to EditParams(warmth = 0.3f, saturation = 0.15f, tint = 0.05f, curves = Curves(red = c(0.5f to 0.56f), green = c(0.5f to 0.47f))),
        "Tropical" to EditParams(vibrance = 0.5f, saturation = 0.15f, warmth = 0.1f, tint = -0.08f, clarity = 0.15f),
        "Hills" to EditParams(dehaze = 0.35f, clarity = 0.25f, warmth = -0.05f, vibrance = 0.2f),
        "Sunset" to EditParams(warmth = 0.45f, tint = 0.1f, glow = 0.25f, vibrance = 0.25f).tone(0x4B1D4E, 0xFFB35C, 0.3f),
        "City" to EditParams(clarity = 0.35f, contrast = 0.15f, saturation = -0.2f, warmth = -0.1f, hdr = 0.2f),
    ))

    val food = FilterGroup("Food", listOf(
        "Fresh" to EditParams(vibrance = 0.35f, exposure = 0.1f, clarity = 0.15f, warmth = 0.05f),
        "Bakery" to EditParams(warmth = 0.3f, exposure = 0.12f, fade = 0.1f, glow = 0.15f),
        "Crisp" to EditParams(clarity = 0.35f, sharpen = 0.35f, contrast = 0.12f, vibrance = 0.15f),
        "Juicy" to EditParams(saturation = 0.3f, vibrance = 0.25f, contrast = 0.15f, curves = Curves(red = c(0.5f to 0.54f))),
        "Cafe" to EditParams(warmth = 0.2f, fade = 0.2f, saturation = -0.15f, vignette = 0.25f, curves = Curves(master = matte)),
        "Street Food" to EditParams(warmth = 0.25f, hdr = 0.3f, vibrance = 0.3f, vignette = 0.2f),
    ))

    val cinema = FilterGroup("Cinema", listOf(
        "Teal & Orange" to EditParams(contrast = 0.15f, vibrance = 0.15f).tone(0x0E5A6B, 0xFFB070, 0.45f),
        "Blockbuster" to EditParams(contrast = 0.3f, clarity = 0.2f, saturation = -0.1f, vignette = 0.3f).tone(0x103A4A, 0xF2B27A, 0.35f),
        "Thriller" to EditParams(exposure = -0.15f, saturation = -0.4f, contrast = 0.25f, tint = -0.15f, vignette = 0.4f),
        "Green Code" to EditParams(saturation = -0.3f, contrast = 0.2f, tint = -0.35f, curves = Curves(green = c(0.5f to 0.56f))),
        "Pastel Cinema" to EditParams(fade = 0.25f, contrast = -0.05f, vibrance = 0.15f, warmth = 0.1f).tone(0xC8798A, 0xFFE2A8, 0.3f),
        "Western" to EditParams(warmth = 0.4f, saturation = -0.2f, contrast = 0.2f, clarity = 0.2f, grain = 0.15f),
        "Sci-Fi" to EditParams(warmth = -0.4f, contrast = 0.2f, glow = 0.2f, saturation = -0.2f).tone(0x0A2A4A, 0xA8E0FF, 0.35f),
        "Anamorphic" to EditParams(contrast = 0.2f, glow = 0.25f, vignette = 0.35f, warmth = -0.1f, fade = 0.08f),
    ))

    val night = FilterGroup("Night & Neon", listOf(
        "Neon" to EditParams(saturation = 0.35f, contrast = 0.25f, glow = 0.35f).tone(0x3A1A8C, 0xFF4FB0, 0.35f),
        "Cyber" to EditParams(contrast = 0.3f, saturation = 0.2f, glow = 0.25f).tone(0x00A6B5, 0xFF2E9A, 0.45f),
        "Midnight" to EditParams(exposure = -0.2f, warmth = -0.35f, contrast = 0.2f, vignette = 0.4f),
        "City Lights" to EditParams(glow = 0.4f, shadows = 0.2f, vibrance = 0.3f, warmth = 0.1f),
        "Moonlight" to EditParams(saturation = -0.5f, warmth = -0.3f, exposure = -0.05f, glow = 0.2f).tone(0x1B2348, 0xCFE0FF, 0.4f),
        "Firelight" to EditParams(warmth = 0.5f, shadows = 0.15f, glow = 0.3f, vignette = 0.35f),
    ))

    val mono = FilterGroup("Black & White", listOf(
        "Mono" to EditParams(saturation = -1f),
        "Soft Mono" to EditParams(saturation = -1f, fade = 0.25f, contrast = -0.1f),
        "High Key" to EditParams(saturation = -1f, exposure = 0.4f, contrast = -0.1f, highlights = -0.2f),
        "Low Key" to EditParams(saturation = -1f, exposure = -0.3f, contrast = 0.4f, vignette = 0.5f),
        "Grit" to EditParams(saturation = -1f, clarity = 0.5f, hdr = 0.4f, grain = 0.35f, contrast = 0.2f),
        "Selenium" to EditParams(saturation = -1f, contrast = 0.2f).tone(0x3A2A3E, 0xF0ECE8, 0.5f),
        "Platinum" to EditParams(saturation = -1f, fade = 0.15f).tone(0x4A4A44, 0xF4F0E4, 0.45f),
        "Duotone Rose" to EditParams(saturation = -1f, contrast = 0.15f).tone(0x2B1A5C, 0xFF8FB8, 0.9f),
        "Duotone Lime" to EditParams(saturation = -1f, contrast = 0.15f).tone(0x123C4A, 0xD8FF7A, 0.9f),
        "Duotone Sunset" to EditParams(saturation = -1f, contrast = 0.15f).tone(0x5A0F3C, 0xFFB347, 0.9f),
    ))

    /** City moods: each tuned to a city's colours and character. */
    val cities = FilterGroup("City Moods", listOf(
        "Madurai" to EditParams(warmth = 0.3f, contrast = 0.12f, clarity = 0.2f, glow = 0.2f, vibrance = 0.2f, highlights = -0.1f)
            .tone(0x5A2A12, 0xFFF1D6, 0.35f),
        "Chennai" to EditParams(warmth = 0.2f, exposure = 0.1f, vibrance = 0.3f, dehaze = 0.2f, contrast = 0.1f)
            .tone(0x17607A, 0xFFE0A8, 0.3f),
        "Mumbai" to EditParams(contrast = 0.2f, glow = 0.25f, vibrance = 0.2f, shadows = 0.15f).tone(0x1B3A5C, 0xFFB86B, 0.35f),
        "Kolkata" to EditParams(warmth = 0.25f, fade = 0.2f, saturation = -0.15f, grain = 0.2f, vignette = 0.25f)
            .tone(0x3A2B1A, 0xFFD54A, 0.35f),
        "Delhi" to EditParams(warmth = 0.2f, dehaze = -0.15f, contrast = 0.1f, fade = 0.12f, glow = 0.15f)
            .tone(0x6B2A1E, 0xF5DCC0, 0.3f),
        "Jaipur" to EditParams(warmth = 0.15f, tint = 0.15f, vibrance = 0.3f, clarity = 0.15f).tone(0x7A2E3E, 0xFFC2B0, 0.4f),
        "Bengaluru" to EditParams(tint = -0.12f, warmth = -0.05f, vibrance = 0.3f, clarity = 0.15f, shadows = 0.1f)
            .tone(0x1E4A30, 0xEFF7E6, 0.25f),
        "Kochi" to EditParams(tint = -0.15f, vibrance = 0.35f, dehaze = 0.15f, warmth = 0.05f).tone(0x0E4A40, 0xF6EBC8, 0.3f),
        "Puducherry" to EditParams(exposure = 0.12f, warmth = 0.15f, vibrance = 0.3f, fade = 0.1f).tone(0x1F5FA8, 0xFFD966, 0.35f),
        "Varanasi" to EditParams(warmth = 0.45f, glow = 0.35f, contrast = 0.15f, vignette = 0.3f).tone(0x3A1A3A, 0xFFA640, 0.4f),
        "Hyderabad" to EditParams(warmth = 0.2f, glow = 0.2f, fade = 0.12f, clarity = 0.12f).tone(0x4A3A5A, 0xFFE9D0, 0.35f),
        "Goa" to EditParams(warmth = 0.3f, vibrance = 0.45f, exposure = 0.1f, lightLeak = 0.2f, leakStyle = 1)
            .tone(0x0F6A78, 0xFFC06A, 0.3f),
    ))

    val essentials = FilterGroup("Essentials", Looks.all)
    val feed = FilterGroup("Feed", InstaLooks.all.filter { it.first != "None" })

    val groups: List<FilterGroup> = listOf(exclusive, cities, essentials, film, lomo, vintage, portrait, feed, travel, food, cinema, night, mono)

    val all: List<Pair<String, EditParams>> by lazy { groups.flatMap { it.filters } }
    fun find(name: String?): EditParams? = all.firstOrNull { it.first == name }?.second
}
