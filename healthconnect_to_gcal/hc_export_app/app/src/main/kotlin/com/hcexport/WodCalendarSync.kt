package com.hcexport

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fills "WOD" calendar events from the local workout database.
 *
 * The website is no longer fetched here: the DB is the source of truth and is
 * refreshed before this phase. Marked events are also updated when the DB content
 * changes, which the old implementation never did.
 */
object WodCalendarSync {

    private const val TAG = "WodCalendarSync"

    const val MARKER = "# populated by wod_sync"
    private const val DAYS_AHEAD = 7

    suspend fun run(ctx: Context): Int = withContext(Dispatchers.IO) {
        val daysBack = Prefs.getJournalSyncDaysBack(ctx).toInt()
        val events = CalendarHelper.findWodEvents(ctx, DAYS_AHEAD, daysBack)
        if (events.isEmpty()) {
            Log.i(TAG, "No WOD events found")
            return@withContext 0
        }

        var updated = 0
        for (event in events) {
            val wod = WorkoutStore.getWod(ctx, event.dateStr)
            if (wod == null) {
                SyncLogger.log(ctx, "  SKIP WOD ${event.dateStr}: not in workout database")
                continue
            }
            val desired = "${wod.content}\n\n$MARKER"
            val existing = event.description?.trimEnd()
            if (existing == desired) {
                SyncLogger.log(ctx, "  SKIP WOD ${event.dateStr}: up to date")
                continue
            }
            CalendarHelper.updateEventDescription(ctx, event.id, desired)
            updated++
            SyncLogger.log(ctx, "  WOD ${event.dateStr}: description updated from database")
        }
        Log.i(TAG, "WOD calendar fill complete: $updated updated")
        updated
    }

    /** Remove the bookkeeping marker from text shown to the user. */
    fun stripMarker(text: String?): String =
        text?.replace(MARKER, "")?.trim().orEmpty()
}
