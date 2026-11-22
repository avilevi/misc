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

/**
 * Health Connect + calendar -> journal storage.
 *
 * WOD entries stay calendar-driven (date and times come from the "WOD" events) but
 * their content comes from the local workout database, so a date with several
 * workout types still produces exactly one journal WOD.
 */
object JournalSync {

    private const val TAG = "JournalSync"
    private const val DAYS_AHEAD = 7
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    data class Result(
        val ok: Boolean,
        val created: Int = 0,
        val updated: Int = 0,
        val skipped: Int = 0,
        val merges: List<Pair<String, String>> = emptyList(),
        val error: String? = null,
    )

    suspend fun run(ctx: Context, forceResync: Boolean): Result = withContext(Dispatchers.IO) {
        val now = Instant.now()
        try {
            val start = if (forceResync || Prefs.getJournalLastSyncTime(ctx) == null) {
                now.minus(Prefs.getJournalSyncDaysBack(ctx), ChronoUnit.DAYS)
            } else {
                Instant.ofEpochMilli(Prefs.getJournalLastSyncTime(ctx)!!)
            }

            SyncLogger.log(
                ctx,
                "=== Journal sync started (from ${dateFmt.format(Date(start.toEpochMilli()))}${if (forceResync) ", forced" else ""}) ===",
            )

            var created = 0
            var updated = 0
            var skipped = 0
            val merges = mutableListOf<Pair<String, String>>()

            // ── WOD entries (content from the DB, dates/times from the calendar) ──
            val daysBack = Prefs.getJournalSyncDaysBack(ctx).toInt()
            val calWods = CalendarHelper.findWodEvents(ctx, DAYS_AHEAD, daysBack)
            val wodEntries = mutableListOf<JournalEntry>()
            for (event in calWods) {
                val wod = WorkoutStore.getWod(ctx, event.dateStr) ?: continue
                wodEntries += JournalEntry.fromWod(event.dateStr, event.startMs, event.endMs, wod.content)
            }
            val (wodCreated, wodUpdated) = JournalStorage.upsertWodEntries(ctx, wodEntries)
            created += wodCreated
            updated += wodUpdated
            SyncLogger.log(ctx, "Journal WODs: $wodCreated new, $wodUpdated updated")

            // ── Exercises ──────────────────────────────────────────────────
            val exercises = HealthConnectReader.readExercises(ctx, start, now)
            SyncLogger.log(ctx, "Journal exercise sessions: ${exercises.size}")

            // Workouts per day, read once per day even when it has several sessions.
            val workoutsByDay = mutableMapOf<String, List<Workout>>()
            fun workoutsOn(day: String): List<Workout> =
                workoutsByDay.getOrPut(day) { WorkoutStore.getByDate(ctx, day) }

            val exerciseEntries = mutableListOf<JournalEntry>()
            for (event in exercises) {
                // A session merges only with an event that names a workout the
                // journal has. A meeting that merely overlaps in time names no
                // workout and is never merged into the entry.
                val day = dayFmt.format(Date(event.startMs))
                val dayEvents = CalendarHelper.findWorkoutEvents(ctx, event.startMs, event.endMs)
                    .filter { it.date == day }
                val named = JournalFeed.journalEventsForSession(
                    workouts = workoutsOn(day),
                    events = dayEvents,
                    startMs = event.startMs,
                    endMs = event.endMs,
                )
                val mergedInfos = mutableListOf<MergedWodInfo>()
                for (calEvent in named) {
                    mergedInfos += MergedWodInfo(
                        dateStr = calEvent.date,
                        title = calEvent.title,
                        content = WodCalendarSync.stripMarker(calEvent.description),
                    )
                    merges += CalendarHelper.exerciseTitle(event) to calEvent.title
                    SyncLogger.log(ctx, "  MERGE ${calEvent.title} ${calEvent.date} into exercise")
                }
                val merged = if (mergedInfos.isNotEmpty()) event.copy(mergedWods = mergedInfos) else event
                exerciseEntries += JournalEntry.fromExerciseEvent(merged)
            }
            val (exCreated, exUpdated) = JournalStorage.upsertEntries(ctx, exerciseEntries)
            created += exCreated
            updated += exUpdated
            SyncLogger.log(ctx, "Journal exercises: $exCreated new, $exUpdated updated")

            // ── Sleep ──────────────────────────────────────────────────────
            val sleeps = HealthConnectReader.readSleep(ctx, start, now)
            SyncLogger.log(ctx, "Journal sleep sessions: ${sleeps.size}")
            val sleepEntries = sleeps.map { JournalEntry.fromSleepEvent(it) }
            val (sleepCreated, sleepUpdated) = JournalStorage.upsertSleepEntries(
                ctx, sleepEntries, Prefs.getSleepSourcePriority(ctx),
            )
            created += sleepCreated
            updated += sleepUpdated
            skipped += sleepEntries.size - sleepCreated - sleepUpdated

            Prefs.setJournalLastSyncTime(ctx, now.toEpochMilli())
            SyncLogger.log(ctx, "=== Journal sync done: $created new, $updated updated, $skipped skipped ===")
            Result(ok = true, created = created, updated = updated, skipped = skipped, merges = merges)
        } catch (e: Exception) {
            Log.e(TAG, "Journal sync failed", e)
            SyncLogger.log(ctx, "ERROR: journal sync failed: ${e.message}")
            Result(ok = false, error = e.message ?: e::class.java.simpleName)
        }
    }
}
