package dev.michelle

import android.content.Context

/** Plain settings (not secret): which Claude model analyzes the photos, whether we asked for Health Connect. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var model: String
        get() = prefs.getString("model", null)?.takeIf { it in MODELS } ?: MODELS.first()
        set(value) = prefs.edit().putString("model", value).apply()

    /** Whether Michelle already asked for the Health Connect permission once (it asks on the first save). */
    var askedHealth: Boolean
        get() = prefs.getBoolean("asked_health", false)
        set(value) = prefs.edit().putBoolean("asked_health", value).apply()

    companion object {
        // the best estimates first; Haiku is a fraction of the cost and quicker, maybe less accurate
        val MODELS = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5")
    }
}
