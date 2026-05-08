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
        val durMin  = (j.getLong("em") - j.getLong("sm")) / 60_000
        val typeLabel = CalendarHelper.EXERCISE_TYPES[j.optInt("tc", -1)]?.second ?: "Workout"
        val title   = j.optString("tt", "").trim()
        val name    = title.ifBlank { typeLabel }
        val distKm  = if (j.has("dm")) j.getDouble("dm") / 1000.0 else null
        val pace    = if (j.has("pp")) j.getDouble("pp") else null
        val kcal    = if (j.has("ck")) j.getDouble("ck") else null
        val avgHr   = if (j.has("ah")) j.getDouble("ah") else null
        val maxHr   = if (j.has("mh")) j.getDouble("mh") else null
        val steps   = if (j.has("st")) j.getLong("st") else null
        val source  = friendlySource(j.optString("sp", ""))

        val isQuick = durMin < 10

        val sb = StringBuilder()
        if (isQuick) {
            sb.append("Quick ${durMin}-minute ${name.lowercase()}")
        } else if (distKm != null && distKm > 0) {
            sb.append("Went for a ${distFormat(distKm)} km ${name.lowercase()}")
        } else {
            sb.append("Did ${fmtDur(durMin)} of ${name.lowercase()}")
        }

        val details = mutableListOf<String>()

        if (pace != null) details += "${fmtPace(pace)} /km pace"
        if (kcal != null) details += "${kcal.toInt()} kcal burned"
        if (steps != null) details += "${steps} steps"

        if (details.isNotEmpty()) {
            sb.append(" — ")
            sb.append(details.joinToString(", "))
        }
        sb.append(".")

        val hrParts = mutableListOf<String>()
        if (avgHr != null) hrParts += "averaged ${avgHr.toInt()} bpm"
        if (maxHr != null) hrParts += "peaking at ${maxHr.toInt()} bpm"
        if (hrParts.isNotEmpty()) {
            sb.append(" Heart rate ${hrParts.joinToString(", ")}.")
        }

        sb.append(" Tracked by $source.")
        return sb.toString()
    }

    private fun generateSleep(j: JSONObject): String {
        val totalMin = (j.getLong("em") - j.getLong("sm")) / 60_000
        val source   = friendlySource(j.optString("sp", ""))
        val sb = StringBuilder()
        sb.append("Slept ${fmtDur(totalMin)}.")

        val stagesArr = j.optJSONArray("sg")
        if (stagesArr != null && stagesArr.length() > 0) {
            val totals = linkedMapOf<String, Long>()
            for (i in 0 until stagesArr.length()) {
                val s = stagesArr.getJSONObject(i)
                val name = CalendarHelper.SLEEP_STAGES[s.optInt("sc", 0)] ?: "Stage ${s.optInt("sc")}"
                val dur  = (s.getLong("em") - s.getLong("sm")) / 60_000
                totals[name] = (totals[name] ?: 0L) + dur
            }
            val order = listOf("Deep Sleep", "REM", "Light Sleep", "Sleeping", "Awake", "Out of Bed", "Unknown")
            val parts = mutableListOf<String>()
            val shown = mutableSetOf<String>()
            for (name in order) {
                totals[name]?.let {
                    parts += "$name: ${fmtDur(it)}"
                    shown += name
                }
            }
            for ((name, dur) in totals) {
                if (name !in shown) parts += "$name: ${fmtDur(dur)}"
            }
            if (parts.isNotEmpty()) {
                sb.append(" ${parts.joinToString(" · ")}.")
            }
        }

        sb.append(" Tracked by $source.")
        return sb.toString()
    }

    private fun fmtDur(totalMin: Long): String = when {
        totalMin >= 60 -> {
            val h = totalMin / 60
            val m = totalMin % 60
            if (m > 0) "${h} hour${if (h > 1) "s" else ""} ${m} minutes"
            else "${h} hour${if (h > 1) "s" else ""}"
        }
        else -> "${totalMin} minutes"
    }

    private fun distFormat(km: Double): String =
        if (km >= 100) "%.0f".format(km) else if (km >= 10) "%.1f".format(km) else "%.2f".format(km)

    private fun fmtPace(secPerKm: Double): String {
        val min = (secPerKm / 60).toInt()
        val sec = (secPerKm % 60).toInt()
        return "%d:%02d".format(min, sec)
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
