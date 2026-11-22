package com.hcexport

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

/**
 * Keeps a running live workout reachable when the phone is in a pocket.
 *
 * Two surfaces, one action pipeline:
 *
 *  • a silent media session in a mediaPlayback foreground service, so the
 *    Galaxy Fit3 and Fitbit **Music Controller** (play/pause, next, previous)
 *    drives the workout, with the step and timer as the track metadata;
 *  • a high-priority per-step notification with real action buttons for the
 *    phone, the Wear watch and any band that mirrors notification actions.
 *
 * The session itself stays in the live screen; the service polls its snapshot
 * every second and never owns workout state. New actions are added to the
 * session's action list and reach both surfaces without a second mapping.
 */
class LiveWorkoutService : Service() {

    companion object {
        private const val TAG = "LiveWorkoutService"
        private const val MEDIA_CHANNEL_ID = "live_remote_media"
        private const val STEP_CHANNEL_ID = "live_remote_steps"
        private const val FOREGROUND_ID = 4100
        private const val STEP_ID_BASE = 4200
        /** One result notification for all workouts: a new finish replaces it. */
        private const val FINAL_ID = 4299
        /** Polls with no live screen before the service gives up (~12 s). */
        private const val MISSING_HOST_LIMIT = 12
        const val EXTRA_DATE = "date"
        const val EXTRA_TYPE = "type"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var mediaSession: MediaSession? = null
    private var silentPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastStageKey: String? = null
    private var lastTitle: String? = null
    private var lastText: String? = null
    private var lastActionsJson: String? = null
    private var missingHostPolls = 0
    private var publishedOnce = false
    private var date = ""
    private var typeKey = ""
    private var stopped = false
    private var lastPrimaryAt = 0L

    /** The action-button notification currently on screen, if any. */
    private var currentStepId = 0
    private var stepCounter = 0
    /** The final result notification survives the service. */
    private var keepStepOnStop = false

    private val ticker = object : Runnable {
        override fun run() {
            if (stopped) return
            poll()
            handler.postDelayed(this, 1_000L)
        }
    }

    private val pollSoon = Runnable { if (!stopped) poll() }

    override fun onCreate() {
        super.onCreate()
        createChannels()
        createMediaSession()
        startSilentPlayback()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_DATE)?.let { date = it }
        intent?.getStringExtra(EXTRA_TYPE)?.let { typeKey = it }
        if (!acquireWakeLock()) {
            // The workout cannot keep time without one.
            Log.w(TAG, "Wake lock unavailable")
        }
        if (!publishedOnce) {
            // A new workout replaces the previous workout's result card.
            getSystemService(NotificationManager::class.java).cancel(FINAL_ID)
            // A media notification must exist before the first poll so the
            // controller has something to attach to.
            goForeground(mediaNotification(initialSnapshot()))
        }
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        runCatching {
            wakeLock?.let { lock -> if (lock.isHeld) lock.release() }
        }
        wakeLock = null
        runCatching {
            silentPlayer?.let { player ->
                if (player.isPlaying) player.stop()
                player.release()
            }
        }
        silentPlayer = null
        runCatching {
            mediaSession?.let { session ->
                session.isActive = false
                session.release()
            }
        }
        mediaSession = null
        if (!keepStepOnStop && currentStepId != 0) {
            getSystemService(NotificationManager::class.java).cancel(currentStepId)
            currentStepId = 0
        }
        super.onDestroy()
    }

    // ── The workout's snapshot ──────────────────────────────────────────────

    private fun poll() {
        val host = LiveRemoteHub.host
        val snapshot = host?.remoteSnapshot()
        if (snapshot == null) {
            missingHostPolls += 1
            if (missingHostPolls >= MISSING_HOST_LIMIT) {
                // The live screen is gone (closed, or its activity was
                // destroyed). Nothing can be controlled any more.
                stopServiceCleanly(removeEverything = true)
            }
            return
        }
        missingHostPolls = 0
        publish(snapshot)
    }

    private fun publish(snapshot: LiveRemoteSnapshot) {
        if (snapshot.date.isNotBlank()) date = snapshot.date
        if (snapshot.typeKey.isNotBlank()) typeKey = snapshot.typeKey
        updateMediaSession(snapshot)

        val actionsJson = snapshot.actions.joinToString("|") { "${it.id}=${it.label}" }
        val stageChanged = snapshot.stageKey != lastStageKey
        val contentChanged = snapshot.title != lastTitle ||
            snapshot.text != lastText ||
            actionsJson != lastActionsJson ||
            stageChanged

        lastStageKey = snapshot.stageKey
        lastTitle = snapshot.title
        lastText = snapshot.text
        lastActionsJson = actionsJson
        publishedOnce = true

        if (stageChanged) {
            // A new set or round: a new action notification (and a band
            // alert). The media card is the same one, updated quietly.
            goForeground(mediaNotification(snapshot))
            postStepNotification(snapshot, alert = true)
        } else if (contentChanged) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(FOREGROUND_ID, mediaNotification(snapshot))
            if (currentStepId != 0) {
                manager.notify(currentStepId, stepNotification(snapshot, alert = false))
            }
        }

        if (snapshot.done) {
            // The result notification stays after the controls are gone.
            keepStepOnStop = true
            stopServiceCleanly(removeEverything = false)
        }
    }

    private fun goForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(FOREGROUND_ID, notification)
        }
    }

    private fun stopServiceCleanly(removeEverything: Boolean) {
        if (stopped) return
        stopped = true
        handler.removeCallbacksAndMessages(null)
        if (removeEverything) {
            keepStepOnStop = false
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            // The media card goes; the final result notification stays.
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    // ── Channels and the two notifications ──────────────────────────────────

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        // The media card is a controller, not an alert: every new set must
        // vibrate the band through the step notification below.
        manager.createNotificationChannel(
            NotificationChannel(
                MEDIA_CHANNEL_ID,
                "Live workout media",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "The media session a watch band controls during a workout"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                STEP_CHANNEL_ID,
                "Live workout steps",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "The current set with its Mark complete and Skip buttons"
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 220, 120, 220)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    private fun initialSnapshot() = LiveRemoteSnapshot(
        date = date,
        typeKey = typeKey,
        workoutTitle = "HC Sync",
        title = "Live workout",
        text = "Starting…",
        bigText = "The live workout is starting.",
        result = null,
        stageKey = "starting",
        actions = emptyList(),
        done = false,
        mediaTitle = "Live workout",
        mediaArtist = "Starting…",
        mediaAlbum = "HC Sync",
        mediaDurationMs = 1L,
        mediaPositionMs = 0L,
    )

    /** The silent foreground notification; on Android 13+ it is the media card. */
    private fun mediaNotification(snapshot: LiveRemoteSnapshot): Notification {
        val builder = baseBuilder(MEDIA_CHANNEL_ID, snapshot.title, snapshot.text)
            .setSubText(snapshot.result ?: snapshot.workoutTitle)
            .setContentIntent(contentIntent(snapshot))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        addActions(builder, snapshot, this::actionIntent)
        val style = Notification.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
        if (snapshot.actions.isNotEmpty()) {
            style.setShowActionsInCompactView(*compactActionIndices(snapshot))
        }
        builder.setStyle(style)
        return builder.build()
    }

    /** The action-button notification; this is the one that pops on each set. */
    private fun stepNotification(snapshot: LiveRemoteSnapshot, alert: Boolean): Notification {
        val builder = baseBuilder(STEP_CHANNEL_ID, snapshot.title, snapshot.text)
            .setSubText(snapshot.result ?: snapshot.workoutTitle)
            .setStyle(Notification.BigTextStyle().bigText(snapshot.bigText))
            .setContentIntent(contentIntent(snapshot))
            .setOngoing(false)
            .setAutoCancel(snapshot.done)
            .setOnlyAlertOnce(!alert)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        addActions(builder, snapshot, this::actionIntent)
        return builder.build()
    }

    private fun postStepNotification(snapshot: LiveRemoteSnapshot, alert: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        val newId = if (snapshot.done) {
            FINAL_ID
        } else {
            stepCounter = (stepCounter + 1) % 50
            STEP_ID_BASE + stepCounter
        }
        manager.notify(newId, stepNotification(snapshot, alert))
        if (currentStepId != 0 && currentStepId != newId) {
            manager.cancel(currentStepId)
        }
        currentStepId = newId
    }

    private fun addActions(
        builder: Notification.Builder,
        snapshot: LiveRemoteSnapshot,
        intentFor: (String) -> PendingIntent,
    ) {
        snapshot.actions.take(3).forEach { action ->
            builder.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, iconFor(action.id)),
                    action.label,
                    intentFor(action.id),
                ).build()
            )
        }
    }

    private fun compactActionIndices(snapshot: LiveRemoteSnapshot): IntArray =
        snapshot.actions.indices.take(3).toList().toIntArray()

    private fun baseBuilder(
        channelId: String,
        title: String,
        text: String,
    ): Notification.Builder {
        val builder = Notification.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setShowWhen(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder
    }

    private fun iconFor(actionId: String): Int = when (actionId) {
        LiveRemoteHub.ACTION_SKIP, LiveRemoteHub.ACTION_SKIP_REST -> R.drawable.ic_live_skip
        else -> R.drawable.ic_live_check
    }

    private fun contentIntent(snapshot: LiveRemoteSnapshot): PendingIntent {
        val openDate = snapshot.date.ifBlank { date }
        val openType = snapshot.typeKey.ifBlank { typeKey }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_LIVE_DATE, openDate)
            putExtra(MainActivity.EXTRA_LIVE_TYPE, openType)
        }
        return PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(actionId: String): PendingIntent {
        val intent = Intent(this, LiveActionReceiver::class.java).apply {
            action = LiveActionReceiver.ACTION_LIVE_REMOTE
            putExtra(LiveActionReceiver.EXTRA_ACTION, actionId)
        }
        return PendingIntent.getBroadcast(
            this,
            actionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ── The band's media controller ─────────────────────────────────────────

    private fun createMediaSession() {
        val session = MediaSession(this, "HcExportLiveWorkout")
        session.setFlags(
            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        session.setCallback(
            object : MediaSession.Callback() {
                override fun onPlay() = transport(0)
                override fun onPause() = transport(0)
                override fun onSkipToNext() = transport(1)
                override fun onSkipToPrevious() = transport(2)
            },
            handler,
        )
        session.isActive = true
        mediaSession = session
    }

    /**
     * Play/pause is the first action (mark complete), next is the second
     * (skip), previous the third if the workout ever offers one. The order is
     * the screen's own order, so a new action reaches the band without a
     * second mapping to maintain.
     */
    private fun transport(slot: Int) {
        val snapshot = LiveRemoteHub.host?.remoteSnapshot() ?: return
        val action = snapshot.actions.getOrNull(slot) ?: return
        if (slot == 0) {
            // A media controller can send play and pause for one press; do
            // not run the same toggle twice.
            val now = SystemClock.elapsedRealtime()
            if (now - lastPrimaryAt < 350L) return
            lastPrimaryAt = now
        }
        LiveRemoteHub.perform(action.id)
        // The state may have advanced (and the timer resets); refresh now.
        handler.removeCallbacks(pollSoon)
        handler.postDelayed(pollSoon, 80L)
    }

    private fun updateMediaSession(snapshot: LiveRemoteSnapshot) {
        val session = mediaSession ?: return
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, snapshot.mediaTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, snapshot.mediaArtist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, snapshot.mediaAlbum)
                .putLong(
                    MediaMetadata.METADATA_KEY_DURATION,
                    snapshot.mediaDurationMs.coerceAtLeast(1L),
                )
                .build()
        )
        // Always PLAYING: the play/pause button is the workout's primary
        // action, not a transport that can stop the clock.
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(
                    PlaybackState.STATE_PLAYING,
                    snapshot.mediaPositionMs.coerceAtLeast(0L),
                    1.0f,
                    SystemClock.elapsedRealtime(),
                )
                .build()
        )
    }

    /**
     * The clock, the countdown and the per-set notification must keep running
     * with the screen off; a foreground service alone does not keep the CPU
     * awake. The timeout is a backstop for a session that never closes.
     */
    private fun acquireWakeLock(): Boolean {
        val existing = wakeLock
        if (existing != null && existing.isHeld) return true
        return runCatching {
            val lock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HcExport:live-workout")
                .apply { setReferenceCounted(false) }
            lock.acquire(3 * 60 * 60 * 1000L)
            wakeLock = lock
            true
        }.getOrDefault(false)
    }

    /**
     * A silent loop keeps a media controller alive. It asks for no audio
     * focus, so the athlete's music and the workout's cues are untouched.
     */
    private fun startSilentPlayback() {        runCatching {
            val descriptor = resources.openRawResourceFd(R.raw.silent) ?: return@runCatching
            silentPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length,
                )
                descriptor.close()
                isLooping = true
                setVolume(0f, 0f)
                prepare()
                start()
            }
        }.onFailure { Log.w(TAG, "Silent playback unavailable", it) }
    }
}
