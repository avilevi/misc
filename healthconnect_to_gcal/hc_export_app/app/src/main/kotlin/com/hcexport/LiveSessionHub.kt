package com.hcexport

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.hcexport.shared.DEVICE_PHONE
import com.hcexport.shared.LiveSessionCodec
import com.hcexport.shared.LiveSessionDto
import com.hcexport.shared.PATH_LIVE_REQUEST
import com.hcexport.shared.PATH_LIVE_SESSION
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * The phone's half of the live-workout link.
 *
 * The live screen publishes its stage as one DataItem whenever the session
 * moves (start, next set, pause, round end — never on every clock tick).
 * The watch does the same. Whichever side changes a stage publishes, the
 * other adopts: both devices see the same set, the same timer window and the
 * same values, and either one can carry the workout on its own if the link
 * drops.
 */
object LiveSessionHub {

    private const val TAG = "LiveSessionHub"

    /** The last record the watch published. */
    private val _remote = MutableStateFlow<LiveSessionDto?>(null)
    val remote: StateFlow<LiveSessionDto?> = _remote

    /** A fresh request's answer, when there is no workout to sync to. */
    private val _requestResult = MutableStateFlow<String?>(null)
    val requestResult: StateFlow<String?> = _requestResult

    /** The record this phone last published, if its live screen is open. */
    @Volatile
    var local: LiveSessionDto? = null
        private set

    @Volatile
    var localActive: Boolean = false

    @Volatile
    private var lastNotifiedSessionId: String? = null

    /**
     * Workouts ended on either device. An older open record cannot revive one;
     * a genuinely new session (started after the close) still can.
     */
    private val closedAt = mutableMapOf<String, Long>()
    private val closedIds = mutableSetOf<String>()
    private var appCtx: Context? = null

    private const val CLOSED_PREFS = "hc_closed_workouts"
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
            val obj = org.json.JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                closedAt[key] = obj.optLong(key, 0L)
            }
        }
    }

    private fun persistClosed() {
        val ctx = appCtx ?: return
        // Keep the map small: the most recent closes are what can be revived.
        val recent = closedAt.entries.sortedByDescending { it.value }.take(80)
        val obj = org.json.JSONObject()
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

    /** The latest record in the Data Layer, for an app that was closed when
     *  the watch started the workout. */
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

    fun onRemote(ctx: Context, session: LiveSessionDto) {
        init(ctx)
        // A record the phone wrote itself is not news.
        if (session.origin == DEVICE_PHONE) return
        _remote.value = session
        if (!session.open) {
            noteClosed(session)
            return
        }
        if (_requestResult.value != null) _requestResult.value = null
        if (session.sessionId != lastNotifiedSessionId && !AppVisibility.foreground) {
            lastNotifiedSessionId = session.sessionId
            runCatching { NotificationHelper.notifyWatchWorkout(ctx, session) }
        }
    }

    fun clearRemote() {
        _remote.value = null
    }

    /** The screen adopted another device's stage: still a running workout. */
    fun markFollowing(session: LiveSessionDto) {
        local = session.copy(origin = DEVICE_PHONE)
        localActive = true
    }

    fun clearRequestResult() {
        _requestResult.value = null
    }

    /** Publish this phone's current stage to the watch. */
    fun publish(ctx: Context, session: LiveSessionDto) {
        local = session
        localActive = session.open
        putSession(ctx, session)
    }

    /**
     * Publish this phone's terminal state for [session]. It is written even
     * when nothing was published yet, and remembered locally so an older
     * watch record cannot bring the workout back.
     */
    fun closeSession(ctx: Context, session: LiveSessionDto) {
        localActive = false
        local = null
        val closed = session.copy(
            open = false,
            origin = DEVICE_PHONE,
            updatedAt = System.currentTimeMillis(),
        )
        noteClosed(closed)
        putSession(ctx, closed)
        // A stage write already in flight may land just after the close; a
        // second close a moment later is the final word on the Data Layer.
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
        try {
            val request = PutDataRequest.create(PATH_LIVE_SESSION)
                .setData(LiveSessionCodec.encode(session).toByteArray(Charsets.UTF_8))
                .setUrgent()
            Tasks.await(Wearable.getDataClient(ctx).putDataItem(request))
        } catch (e: Exception) {
            Log.e(TAG, "Could not publish live session", e)
        }
    }

    /** Ask the watch for its current stage ("sync from watch"). */
    fun requestWatchState(ctx: Context): Boolean = try {
        _requestResult.value = null
        val node = Tasks.await(Wearable.getNodeClient(ctx).connectedNodes)
            .firstOrNull { it.isNearby } ?: Tasks.await(Wearable.getNodeClient(ctx).connectedNodes).firstOrNull()
        if (node == null) {
            false
        } else {
            val payload = JSONObject().apply {
                put("type", "sync.request")
                put("from", DEVICE_PHONE)
            }.toString().toByteArray(Charsets.UTF_8)
            Tasks.await(
                Wearable.getMessageClient(ctx).sendMessage(node.id, PATH_LIVE_REQUEST, payload)
            )
            true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Could not ask the watch for its state", e)
        false
    }

    /** The watch asked for this phone's stage; answer on the same node. */
    fun respondToWatch(ctx: Context, requestNodeId: String) {
        val current = local
        if (current != null && localActive && current.open) {
            publish(
                ctx,
                current.copy(
                    origin = DEVICE_PHONE,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            return
        }
        runCatching {
            val payload = JSONObject().apply {
                put("type", "sync.none")
                put("from", DEVICE_PHONE)
            }.toString().toByteArray(Charsets.UTF_8)
            Tasks.await(
                Wearable.getMessageClient(ctx).sendMessage(requestNodeId, PATH_LIVE_REQUEST, payload)
            )
        }
    }

    fun onWatchHasNoWorkout() {
        _requestResult.value = "none"
    }
}
