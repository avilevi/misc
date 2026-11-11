package com.hcexport

import java.time.LocalDate

/** A row of the local workout database. */
data class Workout(
    val date: String,
    val typeKey: String,
    val typeLabel: String,
    val content: String,
    val sortOrder: Int = 0,
    val firstSeenMs: Long = 0L,
    val lastSeenMs: Long = 0L,
)

object WorkoutTypes {

    /** Stable key used for uniqueness and for picking the calendar WOD. */
    fun keyFor(label: String): String {
        val upper = label.uppercase()
        return when {
            "WOD" in upper -> "wod"
            "HYROX" in upper -> "hyrox"
            "PUMP" in upper && "BLEND" in upper -> "pump_blend"
            "PUMP" in upper -> "pump"
            "WEIGHTLIFTING" in upper -> "weightlifting"
            else -> label.lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "other" }
        }
    }

    fun isValidDate(date: String): Boolean =
        try { LocalDate.parse(date); true } catch (_: Exception) { false }

    /** The type whose content belongs in a calendar event titled "WOD". */
    const val WOD_KEY = "wod"
}
