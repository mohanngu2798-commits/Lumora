package app.lumora.editor.data

import android.content.Context

/** Small on-device settings. Nothing here ever leaves the phone. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("vannamayam", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    var use: String?
        get() = sp.getString("use", null)
        set(v) = sp.edit().putString("use", v).apply()

    var interests: Set<String>
        get() = sp.getStringSet("interests", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("interests", v).apply()

    var postTypes: Set<String>
        get() = sp.getStringSet("postTypes", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("postTypes", v).apply()

    /** True when the answers lean towards social posts, so Feed Studio goes first on home. */
    val studioFirst: Boolean
        get() = interests.any { it in setOf("Seamless carousels", "Panorama posts", "Photo collages", "Feed filters", "Designer fonts") } &&
            "Edit photos" !in interests
}
