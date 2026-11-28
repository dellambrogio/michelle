package dev.michelle

import android.content.Context

/** How the weight graph projects where weight is heading. */
enum class Prediction(val label: String, val note: String) {
    THEIL_SEN("Robust straight line", "Theil–Sen: one odd day barely moves it"),
    LEAST_SQUARES("Straight line", "Least squares: every day counts the same, odd ones too"),
    WEEK_AVERAGE("7-day average", "The average of the last week, held flat ahead"),
    NONE("None", "Just the points"),
}

/** Plain settings (not secret): which Claude model analyzes the photos, the prediction, whether we asked for Health Connect. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var model: String
        get() = prefs.getString("model", null)?.takeIf { it in MODELS } ?: MODELS.first()
        set(value) = prefs.edit().putString("model", value).apply()

    /** The line through the weight graph that says where weight is heading. */
    var prediction: Prediction
        get() = Prediction.entries.firstOrNull { it.name == prefs.getString("prediction", null) } ?: Prediction.THEIL_SEN
        set(value) = prefs.edit().putString("prediction", value.name).apply()

    /** Whether Michelle already asked for the Health Connect permission once (it asks on the first save). */
    var askedHealth: Boolean
        get() = prefs.getBoolean("asked_health", false)
        set(value) = prefs.edit().putBoolean("asked_health", value).apply()

    companion object {
        // the best estimates first; Haiku is a fraction of the cost and quicker, maybe less accurate
        val MODELS = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5")
    }
}
