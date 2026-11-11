package com.hcexport

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

object Prefs {
    private const val NAME = "hc_sync_prefs"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ── Sync range ─────────────────────────────────────────────────────────

    fun getSyncDaysBack(ctx: Context): Long =
        prefs(ctx).getLong("sync_days_back", 90L)

    fun setSyncDaysBack(ctx: Context, days: Long) =
        prefs(ctx).edit().putLong("sync_days_back", days).apply()

    // ── Calendar ───────────────────────────────────────────────────────────

    fun getCalendarId(ctx: Context): Long? {
        val id = prefs(ctx).getLong("calendar_id", -1L)
        return if (id == -1L) null else id
    }

    fun setCalendarId(ctx: Context, id: Long) =
        prefs(ctx).edit().putLong("calendar_id", id).apply()

    // ── Source priority ────────────────────────────────────────────────────

    fun getSleepSourcePriority(ctx: Context): List<String> =
        getList(ctx, "sleep_sources")

    fun setSleepSourcePriority(ctx: Context, list: List<String>) =
        setList(ctx, "sleep_sources", list)

    fun getExerciseSourcePriority(ctx: Context): List<String> =
        getList(ctx, "exercise_sources")

    fun setExerciseSourcePriority(ctx: Context, list: List<String>) =
        setList(ctx, "exercise_sources", list)

    // ── Source discovery ───────────────────────────────────────────────────

    fun addKnownSleepSources(ctx: Context, discovered: Set<String>) =
        mergeNewSources(ctx, "known_sleep_sources", "sleep_sources", discovered)

    fun addKnownExerciseSources(ctx: Context, discovered: Set<String>) =
        mergeNewSources(ctx, "known_exercise_sources", "exercise_sources", discovered)

    private fun mergeNewSources(ctx: Context, knownKey: String, priorityKey: String, discovered: Set<String>) {
        val known = getList(ctx, knownKey).toMutableSet()
        val isNew = known.addAll(discovered)
        if (isNew) {
            setList(ctx, knownKey, known.toList())
            val priority = getList(ctx, priorityKey).toMutableList()
            priority.addAll(discovered - priority.toSet())
            setList(ctx, priorityKey, priority)
        }
    }

    // ── Last sync ──────────────────────────────────────────────────────────

    fun getLastSyncTime(ctx: Context): Long? {
        val v = prefs(ctx).getLong("last_sync_time", -1L)
        return if (v == -1L) null else v
    }

    fun setLastSyncTime(ctx: Context, ms: Long) =
        prefs(ctx).edit().putLong("last_sync_time", ms).apply()

    fun getLastSyncSummary(ctx: Context): String? =
        prefs(ctx).getString("last_sync_summary", null)

    fun setLastSyncSummary(ctx: Context, summary: String) =
        prefs(ctx).edit().putString("last_sync_summary", summary).apply()

    // ── Journal last sync ───────────────────────────────────────────────────

    fun getJournalSyncDaysBack(ctx: Context): Long =
        prefs(ctx).getLong("journal_sync_days_back", 90L)

    fun setJournalSyncDaysBack(ctx: Context, days: Long) =
        prefs(ctx).edit().putLong("journal_sync_days_back", days).apply()

    fun getJournalLastSyncTime(ctx: Context): Long? {
        val v = prefs(ctx).getLong("journal_last_sync_time", -1L)
        return if (v == -1L) null else v
    }

    fun setJournalLastSyncTime(ctx: Context, ms: Long) =
        prefs(ctx).edit().putLong("journal_last_sync_time", ms).apply()

    fun getJournalLastSyncSummary(ctx: Context): String? =
        prefs(ctx).getString("journal_last_sync_summary", null)

    fun setJournalLastSyncSummary(ctx: Context, summary: String) =
        prefs(ctx).edit().putString("journal_last_sync_summary", summary).apply()

    // ── Workout database ───────────────────────────────────────────────────

    fun getWorkoutEtag(ctx: Context): String? =
        prefs(ctx).getString("workout_etag", null)

    fun setWorkoutEtag(ctx: Context, etag: String?) =
        prefs(ctx).edit().putString("workout_etag", etag).apply()

    fun getWorkoutLastModified(ctx: Context): String? =
        prefs(ctx).getString("workout_last_modified", null)

    fun setWorkoutLastModified(ctx: Context, value: String?) =
        prefs(ctx).edit().putString("workout_last_modified", value).apply()

    fun getLastWorkoutPullMs(ctx: Context): Long? {
        val v = prefs(ctx).getLong("last_workout_pull_ms", -1L)
        return if (v == -1L) null else v
    }

    fun setLastWorkoutPullMs(ctx: Context, ms: Long) =
        prefs(ctx).edit().putLong("last_workout_pull_ms", ms).apply()

    fun getLastWorkoutPullSummary(ctx: Context): String? =
        prefs(ctx).getString("last_workout_pull_summary", null)

    fun setLastWorkoutPullSummary(ctx: Context, summary: String) =
        prefs(ctx).edit().putString("last_workout_pull_summary", summary).apply()

    // ── Wear ───────────────────────────────────────────────────────────────

    fun getWearGeneration(ctx: Context): Long =
        prefs(ctx).getLong("wear_generation", 0L)

    fun setWearGeneration(ctx: Context, generation: Long) =
        prefs(ctx).edit().putLong("wear_generation", generation).apply()

    fun getWearPreviousGeneration(ctx: Context): Long =
        prefs(ctx).getLong("wear_previous_generation", 0L)

    fun setWearPreviousGeneration(ctx: Context, generation: Long) =
        prefs(ctx).edit().putLong("wear_previous_generation", generation).apply()

    fun getLastWatchPushMs(ctx: Context): Long? {
        val v = prefs(ctx).getLong("last_watch_push_ms", -1L)
        return if (v == -1L) null else v
    }

    fun setLastWatchPushMs(ctx: Context, ms: Long) =
        prefs(ctx).edit().putLong("last_watch_push_ms", ms).apply()

    // ── Catch-up / migration ───────────────────────────────────────────────

    fun getLastCatchupMs(ctx: Context): Long =
        prefs(ctx).getLong("last_catchup_ms", 0L)

    fun setLastCatchupMs(ctx: Context, ms: Long) =
        prefs(ctx).edit().putLong("last_catchup_ms", ms).apply()

    // ── Armed alarms (display) ─────────────────────────────────────────────

    fun getNextSyncAlarmMs(ctx: Context): Long? {
        val v = prefs(ctx).getLong("next_sync_alarm_at", -1L)
        return if (v == -1L) null else v
    }

    fun setNextSyncAlarmMs(ctx: Context, ms: Long) =
        prefs(ctx).edit().putLong("next_sync_alarm_at", ms).apply()

    fun getNextPullAlarmMs(ctx: Context): Long? {
        val v = prefs(ctx).getLong("next_pull_alarm_at", -1L)
        return if (v == -1L) null else v
    }

    fun setNextPullAlarmMs(ctx: Context, ms: Long) =
        prefs(ctx).edit().putLong("next_pull_alarm_at", ms).apply()



    // ── Sync schedules ─────────────────────────────────────────────────────

    fun getSyncSchedules(ctx: Context): List<SyncSchedule> {
        val json = prefs(ctx).getString("sync_schedules", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull {
                runCatching { SyncSchedule.fromJson(arr.getJSONObject(it)) }.getOrNull()
            }
        } catch (_: Exception) { emptyList() }
    }

    fun setSyncSchedules(ctx: Context, schedules: List<SyncSchedule>) =
        prefs(ctx).edit()
            .putString("sync_schedules", JSONArray(schedules.map { it.toJson() }).toString())
            .apply()

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun getList(ctx: Context, key: String): List<String> {
        val json = prefs(ctx).getString(key, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    private fun setList(ctx: Context, key: String, list: List<String>) =
        prefs(ctx).edit().putString(key, JSONArray(list).toString()).apply()
}
