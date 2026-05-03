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
