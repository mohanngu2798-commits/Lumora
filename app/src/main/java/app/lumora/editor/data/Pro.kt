package app.lumora.editor.data

import android.content.Context
import app.lumora.editor.BuildConfig

/**
 * Vannam Pro: who gets unlimited AI.
 * - Launch offer: everyone gets everything free until [offerEnds] (launch day + 15 days, set at build time).
 *   A build without a launch date (test builds) keeps the offer on.
 * - After that, free users get [FREE_AI_PER_DAY] AI Studio results a day; Pro is unlimited.
 * Purchases plug in through the store's billing once the offer ends; [proUntil] holds the paid-up date.
 */
object Pro {
    const val FREE_AI_PER_DAY = 3
    const val OFFER_DAYS = 15

    data class Plan(val id: String, val title: String, val price: String, val note: String, val badge: String? = null)
    val plans = listOf(
        Plan("pro_monthly", "Monthly", "₹99", "per month"),
        Plan("pro_yearly", "Yearly", "₹499", "per year, ₹42 a month", badge = "SAVE 58%"),
    )
    val perks = listOf(
        "Unlimited AI Studio: restore faces, enhance, upscale 4x",
        "Remove backgrounds with one tap",
        "Every look, City Mood and Vannam Exclusive filter",
        "Full-resolution saves, no watermark",
    )

    /** Millis when the launch offer ends, or 0 when no launch date is set (offer stays on). */
    val offerEnds: Long get() = BuildConfig.LAUNCH_OFFER_END

    fun offerActive(now: Long = System.currentTimeMillis()) = offerEnds == 0L || now < offerEnds

    fun isPro(context: Context, now: Long = System.currentTimeMillis()) = offerActive(now) || Prefs(context).proUntil > now

    /** AI results left today for a free user (Int.MAX_VALUE for Pro). */
    fun aiLeft(context: Context): Int {
        if (isPro(context)) return Int.MAX_VALUE
        val p = Prefs(context)
        return if (p.aiDay != today()) FREE_AI_PER_DAY else (FREE_AI_PER_DAY - p.aiCount).coerceAtLeast(0)
    }

    fun countAiUse(context: Context) {
        if (isPro(context)) return
        val p = Prefs(context)
        if (p.aiDay != today()) { p.aiDay = today(); p.aiCount = 0 }
        p.aiCount = p.aiCount + 1
    }

    private fun today() = (System.currentTimeMillis() + 19_800_000L) / 86_400_000L  // day number, Indian time
}
