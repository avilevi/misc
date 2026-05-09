package com.hcexport

import org.json.JSONObject

object DiaryNarrator {

    fun generate(entry: JournalEntry): String {
        val json = JSONObject(entry.effectiveDataJson())
        return when (entry.entryType) {
            "exercise" -> generateExercise(json)
            "sleep" -> generateSleep(json)
            else -> "Unknown activity"
        }
    }

    private fun generateExercise(j: JSONObject): String {
        val notes = j.optString("nt", "").trim()
        if (notes.isNotEmpty()) return notes

        val durMin = (j.getLong("em") - j.getLong("sm")) / 60_000
        val typeLabel = CalendarHelper.EXERCISE_TYPES[j.optInt("tc", -1)]?.second ?: "Workout"
        val title = j.optString("tt", "").trim()
        val name = title.ifBlank { typeLabel }
        val source = friendlySource(j.optString("sp", ""))
        return "${fmtDur(durMin)} of ${name.lowercase()}. Tracked by $source."
    }

    private fun generateSleep(j: JSONObject): String {
        val totalMin = (j.getLong("em") - j.getLong("sm")) / 60_000
        val source = friendlySource(j.optString("sp", ""))
        return "Slept ${fmtDur(totalMin)}. Tracked by $source."
    }

    private fun fmtDur(totalMin: Long): String = when {
        totalMin >= 60 -> {
            val h = totalMin / 60
            val m = totalMin % 60
            if (m > 0) "${h}h ${m}m" else "${h}h"
        }
        else -> "${totalMin}m"
    }

    private fun friendlySource(pkg: String): String = when {
        pkg.contains("samsung") -> "Samsung Health"
        pkg.contains("fitbit")  -> "Fitbit"
        pkg.contains("garmin")  -> "Garmin Connect"
        pkg.contains("fitness") -> "Google Fit"
        pkg.contains("huawei")  -> "Huawei Health"
        pkg.contains("polar")   -> "Polar Flow"
        pkg.contains("strava")  -> "Strava"
        pkg.contains("withings")-> "Withings"
        pkg.contains("whoop")   -> "WHOOP"
        else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
