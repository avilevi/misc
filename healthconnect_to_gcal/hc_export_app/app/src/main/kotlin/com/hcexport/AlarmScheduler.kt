package com.hcexport

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.util.Calendar

/**
 * Timing lives in AlarmManager, not in WorkManager.
 *
 * WorkManager's own delayed OneTimeWorkRequests are deferrable by the OS and the old
 * implementation replaced its own running unique work, so schedules could be lost.
 * Two inexact-but-Doze-safe alarms now drive everything: the earliest user sync
 * schedule and the fixed 02:00 workout pull. Each receiver re-arms before it enqueues
 * the work, so a crash still leaves the next run armed.
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    const val ACTION_SYNC = "com.hcexport.action.SYNC"
    const val ACTION_PULL = "com.hcexport.action.WORKOUT_PULL"

    private const val REQ_SYNC = 1001
    private const val REQ_PULL = 1002
    private const val REQ_TEST = 1003

    private const val PULL_HOUR = 2
    private const val PULL_MINUTE = 0

    fun ensureArmed(ctx: Context) {
        scheduleNextSync(ctx)
        scheduleNextPull(ctx)
    }

    fun scheduleNextSync(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val next = nextSyncMs(ctx)
        if (next == null) {
            am.cancel(syncPendingIntent(ctx))
            Prefs.setNextSyncAlarmMs(ctx, -1L)
            return
        }
        setAlarm(ctx, next, syncPendingIntent(ctx))
        Prefs.setNextSyncAlarmMs(ctx, next)
        Log.i(TAG, "Next sync alarm at $next (exact=${canScheduleExact(ctx)})")
    }

    fun scheduleNextPull(ctx: Context) {
        val next = nextPullTimeMs()
        setAlarm(ctx, next, pullPendingIntent(ctx))
        Prefs.setNextPullAlarmMs(ctx, next)
        Log.i(TAG, "Next workout pull alarm at $next (exact=${canScheduleExact(ctx)})")
    }

    /** Exact when the user has granted "Alarms & reminders"; otherwise the alarm
     *  manager's inexact-and-Doze-safe form. */
    private fun setAlarm(ctx: Context, triggerAtMs: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        }
    }

    fun canScheduleExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun exactAlarmSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${ctx.packageName}")
        }

    /** Debug helper: fires the ordinary sync alarm path in [delayMs]. */
    fun scheduleTestAlarm(ctx: Context, delayMs: Long = 60_000L) {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION_SYNC
            data = Uri.parse("hcexport://alarm/test")
        }
        val pi = PendingIntent.getBroadcast(
            ctx, REQ_TEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        ctx.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pi)
    }

    fun nextSyncMs(ctx: Context): Long? {
        val schedules = Prefs.getSyncSchedules(ctx)
        if (schedules.isEmpty()) return null
        val now = System.currentTimeMillis()
        return schedules.minOfOrNull { it.nextTriggerMs(now) }
    }

    fun nextPullTimeMs(nowMs: Long = System.currentTimeMillis()): Long {
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, PULL_HOUR)
            set(Calendar.MINUTE, PULL_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(Calendar.getInstance().apply { timeInMillis = nowMs })) {
            target.add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis
    }

    /** Most recent user-schedule occurrence at or before now, for catch-up. */
    fun mostRecentScheduleMs(ctx: Context, nowMs: Long = System.currentTimeMillis()): Long? {
        val schedules = Prefs.getSyncSchedules(ctx)
        if (schedules.isEmpty()) return null
        return schedules.maxOfOrNull { it.mostRecentTriggerMs(nowMs) }
    }

    private fun syncPendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION_SYNC
            data = Uri.parse("hcexport://alarm/sync")
        }
        return PendingIntent.getBroadcast(
            ctx, REQ_SYNC, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun pullPendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION_PULL
            data = Uri.parse("hcexport://alarm/pull")
        }
        return PendingIntent.getBroadcast(
            ctx, REQ_PULL, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
