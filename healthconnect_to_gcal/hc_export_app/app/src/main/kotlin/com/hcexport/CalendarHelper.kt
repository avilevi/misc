package com.hcexport

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.util.*

object CalendarHelper {

    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val tz get() = TimeZone.getDefault().id

    // Exercise type code → (emoji, label)
    private val EXERCISE_TYPES = mapOf(
        0  to ("💪" to "Other Workout"),
        2  to ("🏸" to "Badminton"),
        4  to ("⚾" to "Baseball"),
        5  to ("🏀" to "Basketball"),
        6  to ("🚴" to "Cycling"),
        7  to ("🚴" to "Stationary Bike"),
        8  to ("💪" to "Boot Camp"),
        9  to ("🥊" to "Boxing"),
        10 to ("💪" to "Calisthenics"),
        12 to ("🎿" to "Cross-Country Skiing"),
        13 to ("💪" to "CrossFit"),
        15 to ("💃" to "Dancing"),
        16 to ("🏃" to "Elliptical"),
        18 to ("🤸" to "Exercise Class"),
        23 to ("⛳" to "Golf"),
        24 to ("🧘" to "Guided Breathing"),
        25 to ("🤸" to "Gymnastics"),
        27 to ("⚡" to "HIIT"),
        28 to ("🥾" to "Hiking"),
        30 to ("⛸️" to "Ice Skating"),
        31 to ("🪢" to "Jump Rope"),
        32 to ("🚣" to "Kayaking"),
        33 to ("💪" to "Kettlebell Training"),
        34 to ("🥊" to "Kickboxing"),
        36 to ("🥋" to "Martial Arts"),
        37 to ("🚵" to "Mountain Biking"),
        39 to ("🚣" to "Paddling"),
        41 to ("🧘" to "Pilates"),
        43 to ("🧗" to "Rock Climbing"),
        45 to ("🚣" to "Rowing"),
        46 to ("🚣" to "Rowing Machine"),
        47 to ("🏉" to "Rugby"),
        48 to ("🏃" to "Running"),
        49 to ("🏃" to "Treadmill Running"),
        52 to ("⛸️" to "Skating"),
        53 to ("⛷️" to "Skiing"),
        54 to ("🏂" to "Snowboarding"),
        56 to ("⚽" to "Soccer"),
        58 to ("🎾" to "Squash"),
        59 to ("🪜" to "Stair Climbing"),
        61 to ("🏋️" to "Strength Training"),
        62 to ("🧘" to "Stretching"),
        63 to ("🏄" to "Surfing"),
        64 to ("🏊" to "Open Water Swimming"),
        65 to ("🏊" to "Pool Swimming"),
        67 to ("🎾" to "Tennis"),
        68 to ("🏐" to "Volleyball"),
        69 to ("🚶" to "Walking"),
        71 to ("🏋️" to "Weightlifting"),
        72 to ("♿" to "Wheelchair"),
    )

    private val SLEEP_STAGES = mapOf(
        0 to "Unknown",
        1 to "Awake",
        2 to "Sleeping",
        3 to "Out of Bed",
        4 to "Light Sleep",
        5 to "Deep Sleep",
        6 to "REM",
    )

    // ── Calendar lookup ────────────────────────────────────────────────────

    fun findCalendarId(context: Context, preferredName: String = "Fitness"): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
        )?.use { cursor ->
            val idCol      = cursor.getColumnIndex(CalendarContract.Calendars._ID)
            val nameCol    = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val typeCol    = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
            val primaryCol = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

            var primaryGoogleId: Long? = null
            while (cursor.moveToNext()) {
                val id      = cursor.getLong(idCol)
                val name    = cursor.getString(nameCol) ?: ""
                val type    = cursor.getString(typeCol) ?: ""
                val primary = cursor.getInt(primaryCol) == 1

                if (name.equals(preferredName, ignoreCase = true))
                    return id   // exact match — use it

                if (type == "com.google" && primary && primaryGoogleId == null)
                    primaryGoogleId = id
            }
            return primaryGoogleId
        }
        return null
    }

    // ── Deduplication ──────────────────────────────────────────────────────

    fun eventExists(context: Context, calendarId: Long, title: String, startMs: Long): Boolean {
        val projection = arrayOf(CalendarContract.Events._ID)
        val selection  = "${CalendarContract.Events.CALENDAR_ID} = ? " +
                         "AND ${CalendarContract.Events.TITLE} = ? " +
                         "AND ${CalendarContract.Events.DTSTART} = ?"
        val args = arrayOf(calendarId.toString(), title, startMs.toString())
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, projection, selection, args, null
        )?.use { return it.count > 0 }
        return false
    }

    // ── Event insertion ────────────────────────────────────────────────────

    fun insertEvent(context: Context, calendarId: Long, title: String,
                    description: String, startMs: Long, endMs: Long) {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID,  calendarId)
            put(CalendarContract.Events.TITLE,        title)
            put(CalendarContract.Events.DESCRIPTION,  description)
            put(CalendarContract.Events.DTSTART,      startMs)
            put(CalendarContract.Events.DTEND,        endMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, tz)
        }
        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
    }

    // ── Title & description formatting ────────────────────────────────────

    fun exerciseTitle(event: ExerciseEvent): String {
        val (emoji, label) = EXERCISE_TYPES[event.typeCode] ?: ("🏃" to "Exercise (${event.typeCode})")
        val name     = event.title.takeIf { it.isNotBlank() } ?: label
        return "$emoji $name – ${fmtDur((event.endMs - event.startMs) / 60_000)}"
    }

    fun exerciseDescription(event: ExerciseEvent): String = buildString {
        if (event.distanceM != null)    appendLine("Distance:   %.2f km".format(event.distanceM / 1000))
        if (event.caloriesKcal != null) appendLine("Calories:   ${event.caloriesKcal.toInt()} kcal")
        if (event.avgHrBpm != null)     appendLine("Avg HR:     ${event.avgHrBpm.toInt()} bpm")
        if (event.maxHrBpm != null)     appendLine("Max HR:     ${event.maxHrBpm.toInt()} bpm")
        appendLine()
        appendLine("${timeFmt.format(Date(event.startMs))}  →  ${timeFmt.format(Date(event.endMs))}")
        if (event.notes.isNotBlank()) { appendLine(); append("Notes: ${event.notes}") }
    }.trimEnd()

    fun sleepTitle(event: SleepEvent): String =
        "😴 Sleep – ${fmtDur((event.endMs - event.startMs) / 60_000)}"

    fun sleepDescription(event: SleepEvent): String = buildString {
        appendLine("${timeFmt.format(Date(event.startMs))}  →  ${timeFmt.format(Date(event.endMs))}")

        if (event.stages.isNotEmpty()) {
            val totals = mutableMapOf<String, Long>()
            event.stages.forEach { stage ->
                val name = SLEEP_STAGES[stage.stageCode] ?: "Stage ${stage.stageCode}"
                totals[name] = (totals[name] ?: 0L) + (stage.endMs - stage.startMs) / 60_000
            }
            appendLine()
            appendLine("Sleep stages:")
            val order = listOf("Deep Sleep", "REM", "Light Sleep", "Sleeping", "Awake", "Out of Bed", "Unknown")
            val shown = mutableSetOf<String>()
            for (name in order) {
                totals[name]?.let { appendLine("  %-14s %s".format(name, fmtDur(it))); shown += name }
            }
            totals.keys.filter { it !in shown }.forEach { appendLine("  %-14s %s".format(it, fmtDur(totals[it]!!))) }
        }
    }.trimEnd()

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun fmtDur(totalMinutes: Long): String = when {
        totalMinutes >= 60 -> {
            val h = totalMinutes / 60; val m = totalMinutes % 60
            if (m > 0) "${h}h ${m}m" else "${h}h"
        }
        else -> "${totalMinutes}m"
    }
}
