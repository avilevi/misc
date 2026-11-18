package com.hcexport

import java.time.Instant

/**
 * Builds a self-contained CSV archive of the workout database. This is the
 * escape hatch for the local history: the file can live in Drive, on a NAS, or
 * anywhere else for as long as the user wants.
 */
object WorkoutExport {

    fun toCsv(workouts: List<Workout>): String {
        val sb = StringBuilder("\uFEFF")
        sb.append("date,type_key,type_label,content,first_pulled_utc,last_seen_utc\r\n")
        workouts
            .sortedWith(compareBy<Workout>({ it.date }, { it.sortOrder }, { it.typeKey }))
            .forEach { workout ->
                sb.append(field(workout.date)).append(',')
                sb.append(field(workout.typeKey)).append(',')
                sb.append(field(workout.typeLabel)).append(',')
                sb.append(field(workout.content)).append(',')
                sb.append(field(timestamp(workout.firstSeenMs))).append(',')
                sb.append(field(timestamp(workout.lastSeenMs)))
                sb.append("\r\n")
            }
        return sb.toString()
    }

    private fun field(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""

    private fun timestamp(ms: Long): String =
        if (ms <= 0L) "" else Instant.ofEpochMilli(ms).toString()
}
