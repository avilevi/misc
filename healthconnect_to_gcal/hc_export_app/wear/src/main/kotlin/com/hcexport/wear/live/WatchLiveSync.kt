package com.hcexport.wear.live

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.hcexport.shared.DEVICE_WATCH
import com.hcexport.shared.LiveResultCodec
import com.hcexport.shared.LiveResultDto
import com.hcexport.shared.LiveSessionCodec
import com.hcexport.shared.LiveSessionDto
import com.hcexport.shared.PATH_LIVE_REQUEST
import com.hcexport.shared.PATH_LIVE_SESSION
import com.hcexport.shared.resultsPath
import com.hcexport.wear.MainActivity
import com.hcexport.wear.WatchAppVisibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

/**
 * The watch's half of the live-workout link.
 *
 * The session record is a single DataItem both devices rewrite at stage
 * changes. Values go to the phone through a second item the phone's listener
 * stores, so a workout run entirely on the watch still lands in the phone's
 * journal and records.
 */
object WatchLiveSync {

    private const val TAG = "WatchLiveSync"

    private val _remote = MutableStateFlow<LiveSessionDto?>(null)
    val remote: StateFlow<LiveSessionDto?> = _remote

    private val _requestResult = MutableStateFlow<String?>(null)
    val requestResult: StateFlow<String?> = _requestResult

    @Volatile
    var localActive: Boolean = false
    @Volatile
    var local: LiveSessionDto? = null

    @Volatile
    private var notifiedSessionId: String? = null

    /**
     * Workouts ended on either device. A stale open record that predates the
     * close is ignored, so the other side cannot bring a finished workout
     * back to life; a genuinely new session (started after the close) is not.
     */
    private val closedAt = mutableMapOf<String, Long>()
    private val closedIds = mutableSetOf<String>()
    private var appCtx: Context? = null

    private const val CLOSED_PREFS = "watch_closed_workouts"
    private const val CLOSED_KEY = "closed"

    private fun workoutKey(date: String, typeKey: String) = "$date|$typeKey"

    /** Load the ended-workout tombstones once; they outlive the process. */
    fun init(ctx: Context) {
        if (appCtx != null) return
        appCtx = ctx.applicationContext
        val raw = appCtx
            ?.getSharedPreferences(CLOSED_PREFS, Context.MODE_PRIVATE)
            ?.getString(CLOSED_KEY, "{}") ?: "{}"
        runCatching {
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                closedAt[key] = obj.optLong(key, 0L)
            }
        }
    }

    private fun persistClosed() {
        val ctx = appCtx ?: return
        val recent = closedAt.entries.sortedByDescending { it.value }.take(80)
        val obj = JSONObject()
        recent.forEach { (key, time) -> obj.put(key, time) }
        runCatching {
            ctx.getSharedPreferences(CLOSED_PREFS, Context.MODE_PRIVATE)
                .edit().putString(CLOSED_KEY, obj.toString()).apply()
        }
    }

    fun isClosed(session: LiveSessionDto): Boolean {
        if (session.sessionId in closedIds) return true
        val closed = closedAt[workoutKey(session.date, session.typeKey)] ?: return false
        // A session that began at or before the close is the one that ended;
        // a later start is a deliberately new workout. The margin absorbs a
        // little clock difference between the two devices.
        return session.startedAt < closed - 5_000L
    }

    private fun noteClosed(session: LiveSessionDto) {
        closedIds += session.sessionId
        closedAt[workoutKey(session.date, session.typeKey)] = System.currentTimeMillis()
        persistClosed()
    }

    fun onRemote(ctx: Context, session: LiveSessionDto) {
        init(ctx)
        if (session.origin == DEVICE_WATCH) return
        _remote.value = session
        if (!session.open) {
            noteClosed(session)
            return
        }
        _requestResult.value = null
        if (session.sessionId != notifiedSessionId && !WatchAppVisibility.foreground) {
            notifiedSessionId = session.sessionId
            runCatching { notifyPhoneWorkout(ctx, session) }
        }
    }

    /** The latest record in the Data Layer, for an app that was closed when
     *  the phone started the workout. */
    fun loadLatest(ctx: Context) {
        runCatching {
            val buffer = Tasks.await(Wearable.getDataClient(ctx).dataItems)
            try {
                for (item in buffer) {
                    if (item.uri.path != PATH_LIVE_SESSION) continue
                    val bytes = item.data ?: continue
                    val session = LiveSessionCodec.decode(String(bytes, Charsets.UTF_8))
                        ?: continue
                    onRemote(ctx, session)
                }
            } finally {
                buffer.release()
            }
        }
    }

    fun onPhoneNoWorkout() {
        _requestResult.value = "none"
    }

    fun clearRequestResult() {
        _requestResult.value = null
    }

    /** This watch's stage, for the phone to adopt. */
    fun publish(ctx: Context, session: LiveSessionDto) {
        local = session
        localActive = session.open
        putSession(ctx, session)
    }

    /** Values of a running session, for the phone's journal. */
    fun publishResults(
        ctx: Context,
        date: String,
        typeKey: String,
        resultsJson: String,
        planUpdatedAt: Long,
        baseResultsJson: String,
    ) {
        runCatching {
            val payload = LiveResultCodec.encode(
                LiveResultDto(
                    date = date,
                    typeKey = typeKey,
                    resultsJson = resultsJson,
                    updatedAt = System.currentTimeMillis(),
                    planUpdatedAt = planUpdatedAt,
                    baseResultsJson = baseResultsJson,
                )
            ).toByteArray(Charsets.UTF_8)
            Tasks.await(
                Wearable.getDataClient(ctx).putDataItem(
                    PutDataRequest.create(resultsPath(date, typeKey))
                        .setData(payload)
                        .setUrgent()
                )
            )
        }
    }

    /**
     * Publish this watch's terminal state for [session]. It is written even
     * when nothing was published yet (an end in the first moment of a set),
     * and it is remembered locally so neither an older phone record nor this
     * watch's own auto-join can revive the workout.
     */
    fun closeSession(ctx: Context, session: LiveSessionDto) {
        localActive = false
        local = null
        val closed = session.copy(
            open = false,
            origin = DEVICE_WATCH,
            updatedAt = System.currentTimeMillis(),
        )
        noteClosed(closed)
        putSession(ctx, closed)
        runCatching { Thread.sleep(400) }
        putSession(ctx, closed.copy(updatedAt = System.currentTimeMillis()))
        val current = _remote.value
        if (current != null && current.date == session.date &&
            current.typeKey == session.typeKey
        ) {
            _remote.value = closed
        }
    }

    private fun putSession(ctx: Context, session: LiveSessionDto) {
        runCatching {
            Tasks.await(
                Wearable.getDataClient(ctx).putDataItem(
                    PutDataRequest.create(PATH_LIVE_SESSION)
                        .setData(LiveSessionCodec.encode(session).toByteArray(Charsets.UTF_8))
                        .setUrgent()
                )
            )
        }.onFailure { Log.e(TAG, "Could not publish live session", it) }
    }

    /** "Sync to phone": ask the phone for its stage. */
    fun requestPhoneState(ctx: Context): Boolean = try {
        _requestResult.value = null
        val nodes = Tasks.await(Wearable.getNodeClient(ctx).connectedNodes)
        val phone = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
        if (phone == null) {
            false
        } else {
            val payload = JSONObject().apply {
                put("type", "sync.request")
                put("from", DEVICE_WATCH)
            }.toString().toByteArray(Charsets.UTF_8)
            Tasks.await(
                Wearable.getMessageClient(ctx).sendMessage(phone.id, PATH_LIVE_REQUEST, payload)
            )
            true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Could not ask the phone for its state", e)
        false
    }

    /** The phone asked where this watch's workout is. */
    fun respondToPhone(ctx: Context, requestNodeId: String) {
        val current = local
        if (current != null && localActive && current.open) {
            publish(
                ctx,
                current.copy(
                    origin = DEVICE_WATCH,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            return
        }
        runCatching {
            val payload = JSONObject().apply {
                put("type", "sync.none")
                put("from", DEVICE_WATCH)
            }.toString().toByteArray(Charsets.UTF_8)
            Tasks.await(
                Wearable.getMessageClient(ctx).sendMessage(requestNodeId, PATH_LIVE_REQUEST, payload)
            )
        }
    }

    // ── The local run survives leaving the app ──────────────────────────────

    private const val RUN_FILE = "live_run.json"

    fun saveRun(ctx: Context, session: LiveSession, date: String, typeKey: String, planUpdatedAt: Long) {
        runCatching {
            File(ctx.filesDir, RUN_FILE).writeText(
                LiveSessionCodec.encode(session.toDto(date, typeKey, planUpdatedAt))
            )
        }
    }

    /** The run to resume, or null when it is another workout, stale or done. */
    fun loadRun(
        ctx: Context,
        date: String,
        typeKey: String,
        planUpdatedAt: Long,
        now: Long = System.currentTimeMillis(),
    ): LiveSessionDto? {
        val file = File(ctx.filesDir, RUN_FILE)
        if (!file.exists()) return null
        val run = runCatching { LiveSessionCodec.decode(file.readText()) }.getOrNull()
            ?: return null
        if (!run.open || run.date != date || run.typeKey != typeKey) return null
        if (isClosed(run)) {
            clearRun(ctx)
            return null
        }
        // A regenerated plan invalidates the positional value keys.
        if (planUpdatedAt > 0 && run.planUpdatedAt > 0 && planUpdatedAt > run.planUpdatedAt) {
            return null
        }
        if (now - run.updatedAt > 12 * 60 * 60 * 1000L) return null
        return run
    }

    fun clearRun(ctx: Context) {
        runCatching { File(ctx.filesDir, RUN_FILE).delete() }
    }

    // ── "A workout started on the phone" ────────────────────────────────────

    private fun notifyPhoneWorkout(ctx: Context, session: LiveSessionDto) {
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            "live_workout",
            "Live workout",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "A workout running on the phone can be joined here" }
        manager.createNotificationChannel(channel)

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_LIVE_DATE, session.date)
            putExtra(MainActivity.EXTRA_LIVE_TYPE, session.typeKey)
        }
        val pending = PendingIntent.getActivity(
            ctx,
            session.sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(ctx, "live_workout")
            .setSmallIcon(com.hcexport.wear.R.drawable.ic_notification)
            .setContentTitle("Workout started on phone")
            .setContentText("Tap to join · ${session.typeKey}")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(1, notification)
    }
}
