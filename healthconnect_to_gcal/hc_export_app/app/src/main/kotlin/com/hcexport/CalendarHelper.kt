package com.hcexport

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

object CalendarHelper {

    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val tz get() = TimeZone.getDefault().id

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

                if (name.equals(preferredName, ignoreCase = true)) return id
                if (type == "com.google" && primary && primaryGoogleId == null) primaryGoogleId = id
            }
            return primaryGoogleId
        }
        return null
    }

    // ── Calendar creation ──────────────────────────────────────────────────

    fun createCalendar(context: Context, name: String, accountName: String, accountType: String): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME,          accountName)
            put(CalendarContract.Calendars.ACCOUNT_TYPE,          accountType)
            put(CalendarContract.Calendars.NAME,                  name)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, name)
            put(CalendarContract.Calendars.CALENDAR_COLOR,        0xFF4CAF50.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT,         accountName)
            put(CalendarContract.Calendars.VISIBLE,               1)
            put(CalendarContract.Calendars.SYNC_EVENTS,           1)
        }
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, accountType)
            .build()
        return try {
            context.contentResolver.insert(uri, values)?.lastPathSegment?.toLongOrNull()
        } catch (_: Exception) { null }
    }

    // ── Deduplication ──────────────────────────────────────────────────────

    fun eventExists(context: Context, calendarId: Long, title: String, startMs: Long): Boolean {
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? " +
                        "AND ${CalendarContract.Events.TITLE} = ? " +
                        "AND ${CalendarContract.Events.DTSTART} = ?"
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            selection,
            arrayOf(calendarId.toString(), title, startMs.toString()),
            null,
        )?.use { return it.count > 0 }
        return false
    }

    // ── Event insertion ────────────────────────────────────────────────────

    fun insertEvent(context: Context, calendarId: Long, title: String,
                    description: String, startMs: Long, endMs: Long) {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID,   calendarId)
            put(CalendarContract.Events.TITLE,         title)
            put(CalendarContract.Events.DESCRIPTION,   description)
            put(CalendarContract.Events.DTSTART,       startMs)
            put(CalendarContract.Events.DTEND,         endMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, tz)
        }
        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
    }

    // ── Title & description formatting ────────────────────────────────────

    fun exerciseTitle(event: ExerciseEvent): String {
        val (emoji, label) = EXERCISE_TYPES[event.typeCode] ?: ("🏃" to "Exercise (${event.typeCode})")
        val name = event.title.takeIf { it.isNotBlank() } ?: label
        return "$emoji $name – ${fmtDur((event.endMs - event.startMs) / 60_000)}"
    }

    fun exerciseDescription(event: ExerciseEvent): String = buildString {
        appendLine("${timeFmt.format(Date(event.startMs))}  →  ${timeFmt.format(Date(event.endMs))}")
        appendLine()

        if (event.distanceM    != null) appendLine("Distance:   %.2f km".format(event.distanceM / 1000))
        if (event.paceSecPerKm != null) appendLine("Avg pace:   ${fmtPace(event.paceSecPerKm)} /km")
        if (event.stepsCount   != null) appendLine("Steps:      ${event.stepsCount}")
        if (event.caloriesKcal != null) appendLine("Calories:   ${event.caloriesKcal.toInt()} kcal")
        if (event.avgHrBpm     != null) appendLine("Avg HR:     ${event.avgHrBpm.toInt()} bpm")
        if (event.maxHrBpm     != null) appendLine("Max HR:     ${event.maxHrBpm.toInt()} bpm")

        if (event.hrSamples.isNotEmpty()) {
            appendLine()
            appendLine("HR over time:")
            for (s in event.hrSamples) {
                appendLine("  %3dm → %d bpm".format(s.offsetMin, s.bpm))
            }
        }

        if (event.notes.isNotBlank()) { appendLine(); appendLine("Notes: ${event.notes}") }

        appendLine()
        append("Source: ${friendlySource(event.sourcePkg)}")
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
            totals.keys.filter { it !in shown }.forEach {
                appendLine("  %-14s %s".format(it, fmtDur(totals[it]!!)))
            }
        }

        appendLine()
        append("Source: ${friendlySource(event.sourcePkg)}")
    }.trimEnd()

    // ── WOD events ────────────────────────────────────────────────────────

    data class WodEventInfo(val id: Long, val dateStr: String, val description: String?)

    fun findWodEvents(context: Context, daysAhead: Int): List<WodEventInfo> {
        val todayStart = Instant.now().truncatedTo(ChronoUnit.DAYS)
        val rangeEnd   = todayStart.plus(daysAhead.toLong(), ChronoUnit.DAYS)

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DESCRIPTION,
        )
        val selection = "${CalendarContract.Events.TITLE} = ? " +
            "AND ${CalendarContract.Events.DTSTART} >= ? " +
            "AND ${CalendarContract.Events.DTSTART} < ? " +
            "AND ${CalendarContract.Events.DELETED} = 0"

        val results = mutableListOf<WodEventInfo>()
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            arrayOf("WOD", todayStart.toEpochMilli().toString(), rangeEnd.toEpochMilli().toString()),
            null,
        )?.use { cursor ->
            val idCol    = cursor.getColumnIndex(CalendarContract.Events._ID)
            val startCol = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
            val descCol  = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            while (cursor.moveToNext()) {
                val id    = cursor.getLong(idCol)
                val start = cursor.getLong(startCol)
                val desc  = cursor.getString(descCol)
                val date  = Instant.ofEpochMilli(start)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                results += WodEventInfo(id, date, desc)
            }
        }
        return results
    }

    fun updateEventDescription(context: Context, eventId: Long, description: String) {
        val values = ContentValues().apply {
            put(CalendarContract.Events.DESCRIPTION, description)
        }
        context.contentResolver.update(
            CalendarContract.Events.CONTENT_URI,
            values,
            "${CalendarContract.Events._ID} = ?",
            arrayOf(eventId.toString()),
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun fmtDur(totalMinutes: Long): String = when {
        totalMinutes >= 60 -> {
            val h = totalMinutes / 60; val m = totalMinutes % 60
            if (m > 0) "${h}h ${m}m" else "${h}h"
        }
        else -> "${totalMinutes}m"
    }

    private fun fmtPace(secPerKm: Double): String {
        val total = secPerKm.toInt()
        return "%d:%02d".format(total / 60, total % 60)
    }

    private fun friendlySource(pkg: String): String = when {
        pkg.contains("samsung") -> "Samsung Health"
        pkg.contains("fitbit")  -> "Fitbit"
        pkg.contains("garmin")  -> "Garmin Connect"
        pkg.contains("google.android.apps.fitness") -> "Google Fit"
        pkg.contains("huawei")  -> "Huawei Health"
        pkg.contains("polar")   -> "Polar Flow"
        pkg.contains("strava")  -> "Strava"
        pkg.contains("withings")-> "Withings"
        pkg.contains("whoop")   -> "WHOOP"
        else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
