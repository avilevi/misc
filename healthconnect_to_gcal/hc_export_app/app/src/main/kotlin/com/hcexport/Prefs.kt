package com.hcexport

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

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

    /** The calendar last chosen when scheduling a session from the journal. */
    fun getScheduledCalendarId(ctx: Context): Long? {
        val id = prefs(ctx).getLong("scheduled_calendar_id", -1L)
        return if (id == -1L) null else id
    }

    fun setScheduledCalendarId(ctx: Context, id: Long) =
        prefs(ctx).edit().putLong("scheduled_calendar_id", id).apply()

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
        prefs(ctx).getLong("journal_sync_days_back", 14L)

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

    // ── AI ─────────────────────────────────────────────────────────────────

    /**
     * The pre-provider model preference. It is read only to seed the Claude
     * provider on upgrade; new code uses [AiConfigStore].
     */
    internal fun legacyAiModel(ctx: Context): String? =
        prefs(ctx).getString("ai_model", null)?.takeIf { it.isNotBlank() }

    internal fun clearLegacyAiModel(ctx: Context) {
        prefs(ctx).edit().remove("ai_model").apply()
    }

    // ── Workout database ───────────────────────────────────────────────────

    /** "week" (default), "month" or "day" — the calendar view Workouts opens in. */
    fun getDefaultWorkoutView(ctx: Context): String =
        prefs(ctx).getString("default_workout_view", "week") ?: "week"

    fun setDefaultWorkoutView(ctx: Context, view: String) =
        prefs(ctx).edit().putString("default_workout_view", view).apply()

    /** "week" (default), "month" or "day" — the calendar view the Journal opens in. */
    fun getJournalView(ctx: Context): String =
        prefs(ctx).getString("journal_view", "week") ?: "week"

    fun setJournalView(ctx: Context, view: String) =
        prefs(ctx).edit().putString("journal_view", view).apply()

    /**
     * The first day of the week in the Journal and Workouts calendars. It
     * defaults to the device locale's own first day (Sunday in Israel and the
     * US, Monday in most of Europe), never to a hardcoded Monday.
     */
    fun getFirstDayOfWeek(ctx: Context): DayOfWeek {
        val stored = prefs(ctx).getInt("first_day_of_week", 0)
        val value = if (stored in 1..7) stored else localeFirstDayOfWeek().value
        return DayOfWeek.of(value)
    }

    fun setFirstDayOfWeek(ctx: Context, day: DayOfWeek) =
        prefs(ctx).edit().putInt("first_day_of_week", day.value).apply()

    private fun localeFirstDayOfWeek(): DayOfWeek =
        WeekFields.of(Locale.getDefault()).firstDayOfWeek

    // ── Design ─────────────────────────────────────────────────────────────

    /** Minimal by default; verbose keeps every explanatory line. */
    fun getDesignStyle(ctx: Context): DesignStyle =
        DesignStyle.fromKey(prefs(ctx).getString("design_style", null))

    fun setDesignStyle(ctx: Context, style: DesignStyle) =
        prefs(ctx).edit().putString("design_style", style.key).apply()

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



    // ── Live workout ──────────────────────────────────────────────────────

    /** The get-ready countdown before a live section starts. Zero turns it off. */
    fun getLiveLeadInSeconds(ctx: Context): Int =
        prefs(ctx).getInt("live_lead_in_seconds", 10)

    fun setLiveLeadInSeconds(ctx: Context, seconds: Int) =
        prefs(ctx).edit().putInt("live_lead_in_seconds", seconds.coerceIn(0, 60)).apply()

    /** The set window used when the workout text does not prescribe one. */
    fun getLiveSetIntervalSeconds(ctx: Context): Int =
        prefs(ctx).getInt("live_set_interval_seconds", 90)

    fun setLiveSetIntervalSeconds(ctx: Context, seconds: Int) =
        prefs(ctx).edit().putInt("live_set_interval_seconds", seconds.coerceIn(10, 900)).apply()

    fun getLiveSound(ctx: Context): Boolean =
        prefs(ctx).getBoolean("live_sound", true)

    fun setLiveSound(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean("live_sound", enabled).apply()

    fun getLiveVibration(ctx: Context): Boolean =
        prefs(ctx).getBoolean("live_vibration", true)

    fun setLiveVibration(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean("live_vibration", enabled).apply()

    fun getLiveKeepScreenOn(ctx: Context): Boolean =
        prefs(ctx).getBoolean("live_keep_screen_on", true)

    fun setLiveKeepScreenOn(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean("live_keep_screen_on", enabled).apply()

    /**
     * How long the live step notification stays quiet before it re-alerts
     * itself, in minutes. 0 turns the renewal off.
     */
    fun getLiveRemoteRenewMinutes(ctx: Context): Int =
        prefs(ctx).getInt("live_remote_renew_minutes", 1).coerceIn(0, 60)

    fun setLiveRemoteRenewMinutes(ctx: Context, minutes: Int) =
        prefs(ctx).edit().putInt("live_remote_renew_minutes", minutes.coerceIn(0, 60)).apply()

    // ── BridgeAthletic ─────────────────────────────────────────────────────

    fun getLastBridgePullMs(ctx: Context): Long? {
        val v = prefs(ctx).getLong("bridge_last_pull_ms", -1L)
        return if (v == -1L) null else v
    }

    fun setLastBridgePullMs(ctx: Context, ms: Long) =
        prefs(ctx).edit().putLong("bridge_last_pull_ms", ms).apply()

    fun getBridgeLastSummary(ctx: Context): String? =
        prefs(ctx).getString("bridge_last_summary", null)

    fun setBridgeLastSummary(ctx: Context, summary: String?) =
        prefs(ctx).edit().putString("bridge_last_summary", summary).apply()

    fun getBridgeLastError(ctx: Context): String? =
        prefs(ctx).getString("bridge_last_error", null)?.takeIf { it.isNotBlank() }

    fun setBridgeLastError(ctx: Context, error: String?) =
        prefs(ctx).edit().putString("bridge_last_error", error).apply()

    fun getBridgeProfileName(ctx: Context): String? =
        prefs(ctx).getString("bridge_profile_name", null)?.takeIf { it.isNotBlank() }

    fun setBridgeProfileName(ctx: Context, name: String) =
        prefs(ctx).edit().putString("bridge_profile_name", name).apply()

    // ── Fitr ───────────────────────────────────────────────────────────────

    fun getLastFitrPullMs(ctx: Context): Long? {
        val v = prefs(ctx).getLong("fitr_last_pull_ms", -1L)
        return if (v == -1L) null else v
    }

    fun setLastFitrPullMs(ctx: Context, ms: Long) =
        prefs(ctx).edit().putLong("fitr_last_pull_ms", ms).apply()

    fun getFitrLastSummary(ctx: Context): String? =
        prefs(ctx).getString("fitr_last_summary", null)

    fun setFitrLastSummary(ctx: Context, summary: String?) =
        prefs(ctx).edit().putString("fitr_last_summary", summary).apply()

    fun getFitrLastError(ctx: Context): String? =
        prefs(ctx).getString("fitr_last_error", null)?.takeIf { it.isNotBlank() }

    fun setFitrLastError(ctx: Context, error: String?) =
        prefs(ctx).edit().putString("fitr_last_error", error).apply()

    fun getFitrProfileName(ctx: Context): String? =
        prefs(ctx).getString("fitr_profile_name", null)?.takeIf { it.isNotBlank() }

    fun setFitrProfileName(ctx: Context, name: String) =
        prefs(ctx).edit().putString("fitr_profile_name", name).apply()

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
