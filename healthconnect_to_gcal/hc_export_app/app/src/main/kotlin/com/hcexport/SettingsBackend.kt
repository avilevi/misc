package com.hcexport

import android.content.Context
import android.provider.CalendarContract

data class CalendarAccount(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
)

data class SyncStatus(
    val lastSyncMs: Long?,
    val lastSyncSummary: String?,
    val lastPullSummary: String?,
    val lastPullMs: Long?,
    val nextSyncMs: Long?,
    val nextPullMs: Long,
    val watchPushMs: Long?,
    val workoutCount: Int,
)

data class WorkoutSettingsStatus(
    val count: Int,
    val latestDate: String?,
    val lastPullMs: Long?,
    val lastPullSummary: String?,
    val watchPushMs: Long?,
)

object SettingsBackend {

    fun loadGoogleCalendars(context: Context): List<CalendarAccount> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
        )
        val result = mutableListOf<CalendarAccount>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection, null, null, null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(CalendarContract.Calendars._ID)
            val nameCol = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val acctCol = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
            val typeCol = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
            while (cursor.moveToNext()) {
                val type = cursor.getString(typeCol) ?: ""
                if (type != "com.google") continue
                result += CalendarAccount(
                    id = cursor.getLong(idCol),
                    displayName = cursor.getString(nameCol) ?: "",
                    accountName = cursor.getString(acctCol) ?: "",
                    accountType = type,
                )
            }
        }
        return result
    }

    fun syncStatus(context: Context): SyncStatus {
        val nextPull = Prefs.getNextPullAlarmMs(context) ?: AlarmScheduler.nextPullTimeMs()
        val fitr = FitrSecretStore.hasCredentials(context)
        return SyncStatus(
            lastSyncMs = Prefs.getLastSyncTime(context),
            lastSyncSummary = Prefs.getLastSyncSummary(context),
            lastPullSummary = if (fitr) Prefs.getFitrLastSummary(context)
                else Prefs.getLastWorkoutPullSummary(context),
            lastPullMs = if (fitr) Prefs.getLastFitrPullMs(context)
                else Prefs.getLastWorkoutPullMs(context),
            nextSyncMs = AlarmScheduler.nextSyncMs(context),
            nextPullMs = nextPull,
            watchPushMs = Prefs.getLastWatchPushMs(context),
            workoutCount = WorkoutStore.count(context),
        )
    }

    fun workoutStatus(context: Context): WorkoutSettingsStatus {
        val fitr = FitrSecretStore.hasCredentials(context)
        return WorkoutSettingsStatus(
            count = WorkoutStore.count(context),
            latestDate = WorkoutStore.latestDate(context),
            lastPullMs = if (fitr) Prefs.getLastFitrPullMs(context)
                else Prefs.getLastWorkoutPullMs(context),
            lastPullSummary = if (fitr) Prefs.getFitrLastSummary(context)
                else Prefs.getLastWorkoutPullSummary(context),
            watchPushMs = Prefs.getLastWatchPushMs(context),
        )
    }

    fun sourceSummary(sources: List<String>): String =
        if (sources.isEmpty()) "Run a sync first to discover sources."
        else sources.mapIndexed { i, pkg -> "${i + 1}. ${SourceBrands.displayName(pkg)}" }
            .joinToString(", ")
}
