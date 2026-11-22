package com.hcexport

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A workout the athlete deliberately put on a day's journal.
 *
 * The journal's other entries are derived — a calendar event names a workout,
 * or Health Connect recorded a session, or values were entered into the AI
 * plan. This is the one piece the app owns: scheduling a session creates the
 * entry before any of those exist, and can optionally also write a Google
 * Calendar event. The window is kept even when no event was written, so the
 * journal can show the planned time and merge a watch session that overlaps it.
 */
data class ScheduledSession(
    val date: String,
    val typeKey: String,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val calendarId: Long? = null,
    val eventId: Long? = null,
    val createdAtMs: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("date", date)
        put("type", typeKey)
        startMs?.let { put("sm", it) }
        endMs?.let { put("em", it) }
        calendarId?.let { put("cal", it) }
        eventId?.let { put("ev", it) }
        put("ca", createdAtMs)
    }

    companion object {
        fun fromJson(obj: JSONObject): ScheduledSession? {
            val date = obj.optString("date").takeIf { WorkoutTypes.isValidDate(it) }
                ?: return null
            val typeKey = obj.optString("type").takeIf { it.isNotBlank() } ?: return null
            return ScheduledSession(
                date = date,
                typeKey = typeKey,
                startMs = if (obj.has("sm")) obj.optLong("sm") else null,
                endMs = if (obj.has("em")) obj.optLong("em") else null,
                calendarId = if (obj.has("cal")) obj.optLong("cal") else null,
                eventId = if (obj.has("ev")) obj.optLong("ev") else null,
                createdAtMs = obj.optLong("ca", 0L),
            )
        }
    }
}

/**
 * The scheduled sessions, in the same SharedPreferences world as the journal
 * notes: device-local by design, exactly like the health journal itself.
 */
object ScheduledSessionStore {

    private const val NAME = "hc_scheduled_sessions"
    private const val KEY = "sessions"

    @Synchronized
    fun getAll(ctx: Context): List<ScheduledSession> {
        val raw = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { ScheduledSession.fromJson(it) }
            }
        }.getOrDefault(emptyList())
    }

    fun getForDay(ctx: Context, date: String): List<ScheduledSession> =
        getAll(ctx).filter { it.date == date }

    fun get(ctx: Context, date: String, typeKey: String): ScheduledSession? =
        getAll(ctx).firstOrNull { it.date == date && it.typeKey == typeKey }

    /** Add or replace the session for this date and workout type. */
    @Synchronized
    fun put(ctx: Context, session: ScheduledSession) {
        val next = getAll(ctx)
            .filterNot { it.date == session.date && it.typeKey == session.typeKey } + session
        write(ctx, next)
        WearPushWorker.enqueue(ctx, delayMs = 1_500L)
    }

    @Synchronized
    fun remove(ctx: Context, date: String, typeKey: String) {
        write(ctx, getAll(ctx).filterNot { it.date == date && it.typeKey == typeKey })
        WearPushWorker.enqueue(ctx, delayMs = 1_500L)
    }

    private fun write(ctx: Context, sessions: List<ScheduledSession>) {
        val array = JSONArray(sessions.map { it.toJson() })
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }
}
