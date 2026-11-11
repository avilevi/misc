package com.hcexport

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/** Health Connect -> calendar events. */
object HealthConnectToCalendar {

    private const val TAG = "HealthConnectToCalendar"
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    data class Result(
        val ok: Boolean,
        val created: Int = 0,
        val skipped: Int = 0,
        val error: String? = null,
    )

    suspend fun run(ctx: Context, forceResync: Boolean): Result = withContext(Dispatchers.IO) {
        val now = Instant.now()
        try {
            val start = if (forceResync || Prefs.getLastSyncTime(ctx) == null) {
                now.minus(Prefs.getSyncDaysBack(ctx), ChronoUnit.DAYS)
            } else {
                Instant.ofEpochMilli(Prefs.getLastSyncTime(ctx)!!)
            }

            SyncLogger.log(
                ctx,
                "=== Calendar sync started (from ${dateFmt.format(Date(start.toEpochMilli()))}${if (forceResync) ", forced" else ""}) ===",
            )

            val calendarId = Prefs.getCalendarId(ctx)
                ?: CalendarHelper.findCalendarId(ctx)
                ?: throw IllegalStateException("No calendar found")

            var created = 0
            var skipped = 0

            val exercises = HealthConnectReader.readExercises(ctx, start, now)
            SyncLogger.log(ctx, "Exercise sessions found: ${exercises.size}")
            for (event in exercises) {
                val title = CalendarHelper.exerciseTitle(event)
                if (CalendarHelper.eventExists(ctx, calendarId, title, event.startMs)) {
                    skipped++
                    SyncLogger.log(ctx, "  SKIP exercise: $title (${dateFmt.format(Date(event.startMs))})")
                } else {
                    CalendarHelper.insertEvent(
                        ctx, calendarId, title,
                        CalendarHelper.exerciseDescription(event),
                        event.startMs, event.endMs,
                    )
                    created++
                    SyncLogger.log(ctx, "  NEW  exercise: $title (${dateFmt.format(Date(event.startMs))})")
                }
            }

            val sleepSessions = HealthConnectReader.readSleep(ctx, start, now)
            SyncLogger.log(ctx, "Sleep sessions found: ${sleepSessions.size}")
            for (event in sleepSessions) {
                val title = CalendarHelper.sleepTitle(event)
                if (CalendarHelper.eventExists(ctx, calendarId, title, event.startMs)) {
                    skipped++
                    SyncLogger.log(ctx, "  SKIP sleep: $title (${dateFmt.format(Date(event.startMs))})")
                } else {
                    CalendarHelper.insertEvent(
                        ctx, calendarId, title,
                        CalendarHelper.sleepDescription(event),
                        event.startMs, event.endMs,
                    )
                    created++
                    SyncLogger.log(ctx, "  NEW  sleep: $title (${dateFmt.format(Date(event.startMs))})")
                }
            }

            Prefs.setLastSyncTime(ctx, now.toEpochMilli())
            SyncLogger.log(ctx, "=== Calendar sync done: $created new, $skipped skipped ===")
            Result(ok = true, created = created, skipped = skipped)
        } catch (e: Exception) {
            Log.e(TAG, "Calendar sync failed", e)
            SyncLogger.log(ctx, "ERROR: calendar sync failed: ${e.message}")
            Result(ok = false, error = e.message ?: e::class.java.simpleName)
        }
    }
}
