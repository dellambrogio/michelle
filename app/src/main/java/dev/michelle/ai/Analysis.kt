package dev.michelle.ai

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.annotation.JsonProperty

/** What Claude says about a photo, in the shape of Prompt.SCHEMA. */
data class Analysis(
    @param:JsonProperty(required = true) val food: Boolean,
    val title: String = "",
    val items: List<FoodItem> = emptyList(),
    val note: String = "",
) {
    companion object {
        private val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        /** The analysis in Claude's JSON, or null if it isn't one. */
        fun parse(json: String): Analysis? = runCatching { mapper.readValue<Analysis>(json) }.getOrNull()
            ?.takeIf { !it.food || it.items.isNotEmpty() }

        // "food" must be there: missing, it would read as false (no food) instead of a bad answer
        init { mapper.configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, true) }
    }
}

data class FoodItem(
    val name: String,
    val portion: String = "",
    val kcal: Double = 0.0,
    @param:JsonProperty("protein_g") val proteinG: Double = 0.0,
    @param:JsonProperty("carbs_g") val carbsG: Double = 0.0,
    @param:JsonProperty("fat_g") val fatG: Double = 0.0,
)
