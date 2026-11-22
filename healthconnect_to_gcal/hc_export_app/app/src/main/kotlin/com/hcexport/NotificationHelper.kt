package com.hcexport

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hcexport.shared.LiveSessionDto

object NotificationHelper {
    private const val CHANNEL_ID = "sync_complete"
    private const val SYNC_NOTIFICATION_ID = 1

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sync complete",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Shows after each auto-sync" }
            ctx.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun notifyMerge(ctx: Context, merges: List<Pair<String, String>>) {
        if (merges.isEmpty()) return
        createChannel(ctx)
        val lines = merges.joinToString("\n") { (hcEvent, calTitle) ->
            "$hcEvent  ←  $calTitle"
        }
        val title = if (merges.size == 1) "Calendar event merged" else "${merges.size} calendar events merged"
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(merges.first().let { (hc, cal) -> "$hc ← $cal" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)
            .notify(SYNC_NOTIFICATION_ID, notification)
    }

    fun notifySyncComplete(ctx: Context, summary: String) {
        createChannel(ctx)
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("HC Sync complete")
            .setContentText(summary.lines().firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)
            .notify(SYNC_NOTIFICATION_ID, notification)
    }

    // ── Live workout started on the watch ────────────────────────────────────

    private const val LIVE_CHANNEL_ID = "live_workout"
    private const val LIVE_NOTIFICATION_ID = 2

    private fun createLiveChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                LIVE_CHANNEL_ID,
                "Live workout",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "A workout running on the watch can be joined on the phone"
            }
            ctx.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    /** The watch started a workout while the phone was elsewhere. */
    fun notifyWatchWorkout(ctx: Context, session: LiveSessionDto) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createLiveChannel(ctx)
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
        val notification = NotificationCompat.Builder(ctx, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Workout started on watch")
            .setContentText("${session.date} · ${session.typeKey} — tap to join")
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)
            .notify(LIVE_NOTIFICATION_ID, notification)
    }
}
