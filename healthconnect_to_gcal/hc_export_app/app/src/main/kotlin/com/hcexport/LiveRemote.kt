package com.hcexport

import android.content.Context
import android.content.Intent
import android.util.Log

/** One control the running workout offers, in the order the screen shows it. */
data class LiveRemoteAction(val id: String, val label: String)

/**
 * What the live-workout notification and the band's media controls show.
 *
 * The snapshot is built from the **live** step, never from a set the phone is
 * only looking at: a band has no way to see that the phone swiped away.
 */
data class LiveRemoteSnapshot(
    val date: String,
    val typeKey: String,
    val workoutTitle: String,
    val title: String,
    val text: String,
    val bigText: String,
    val result: String?,
    val stageKey: String,
    val actions: List<LiveRemoteAction>,
    val done: Boolean,
    val mediaTitle: String,
    val mediaArtist: String,
    val mediaAlbum: String,
    val mediaDurationMs: Long,
    val mediaPositionMs: Long,
)

/** The live screen's side of the remote. Always called on the main thread. */
interface LiveRemoteHost {
    fun remoteSnapshot(): LiveRemoteSnapshot
    fun performRemoteAction(id: String)
}

/**
 * The one place a dumb band's controls reach the running workout.
 *
 * The live screen registers its session here; the foreground service polls the
 * snapshot for the notification and the media session, and turns every button
 * press — notification action or transport control — into one action id. New
 * actions only have to be added to the session's action list to reach both
 * surfaces.
 */
object LiveRemoteHub {
    const val ACTION_START = "start"
    const val ACTION_COMPLETE = "complete"
    const val ACTION_SKIP = "skip"
    const val ACTION_SKIP_REST = "skip_rest"

    @Volatile
    var host: LiveRemoteHost? = null
        private set

    fun attach(newHost: LiveRemoteHost) {
        host = newHost
    }

    fun detach(oldHost: LiveRemoteHost) {
        if (host === oldHost) host = null
    }

    /** Run one action from a notification button or a media control. */
    fun perform(actionId: String) {
        host?.performRemoteAction(actionId)
    }

    fun start(ctx: Context, date: String, typeKey: String) {
        val intent = Intent(ctx, LiveWorkoutService::class.java).apply {
            putExtra(LiveWorkoutService.EXTRA_DATE, date)
            putExtra(LiveWorkoutService.EXTRA_TYPE, typeKey)
        }
        // A recreation in the background cannot start a media service; the
        // workout still runs, it just has no band remote this time.
        runCatching { ctx.startForegroundService(intent) }
            .onFailure { Log.w("LiveRemoteHub", "Live remote unavailable", it) }
    }

    fun stop(ctx: Context) {
        ctx.stopService(Intent(ctx, LiveWorkoutService::class.java))
    }
}
