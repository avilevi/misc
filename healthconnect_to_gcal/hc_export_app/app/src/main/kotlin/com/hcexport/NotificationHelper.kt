package com.hcexport

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

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
        val lines = merges.joinToString("\n") { (hcEvent, wodDate) ->
            "$hcEvent  ←  WOD $wodDate"
        }
        val title = if (merges.size == 1) "WOD merged into HC event" else "${merges.size} WODs merged into HC events"
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(merges.first().let { (hc, wod) -> "$hc ← WOD $wod" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)
            .notify(SYNC_NOTIFICATION_ID, notification)
    }

    fun notifySyncComplete(ctx: Context, summary: String) {
        createChannel(ctx)
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("HC Sync complete")
            .setContentText(summary.lines().firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)
            .notify(SYNC_NOTIFICATION_ID, notification)
    }
}
