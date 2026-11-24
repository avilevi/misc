package com.hcexport

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The one sync path.
 *
 * Order is deliberate:
 *   1. workout website -> local DB (everything else reads it)
 *   2. Health Connect -> calendar
 *   3. calendar WOD descriptions <- DB (journal merges these descriptions)
 *   4. journal ingest (merges the freshly filled calendar events)
 *   5. watch snapshot (carries the new journal)
 *
 * Each phase is isolated: a website outage or a Health Connect failure still lets the
 * other phases run and leaves a readable report in the log.
 */
object SyncEngine {

    private const val FRESH_PULL_MS = 10 * 60 * 1000L
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    data class Report(
        val pull: WorkoutPull.Result?,
        val fitr: FitrPull.Result?,
        val bridge: BridgePull.Result?,
        val calendar: HealthConnectToCalendar.Result?,
        val wodFilled: Int,
        val journal: JournalSync.Result?,
        val watchOk: Boolean,
        val errors: List<String>,
        val summary: String,
    )

    suspend fun run(
        ctx: Context,
        forceCalendar: Boolean = false,
        forceJournal: Boolean = false,
        pullOnly: Boolean = false,
    ): Report = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        // 1. Workouts -> DB. Fitr is the source whenever it is connected; the
        //    website scraper only runs for installs that have not connected it.
        val fitrConnected = FitrSecretStore.hasCredentials(ctx)
        val fitr = if (!fitrConnected) {
            FitrPull.Result(ok = true, skipped = true)
        } else {
            val lastFitr = Prefs.getLastFitrPullMs(ctx)
            val fitrFresh = !pullOnly && !forceCalendar && !forceJournal && lastFitr != null &&
                System.currentTimeMillis() - lastFitr < FRESH_PULL_MS
            if (fitrFresh) {
                FitrPull.Result(ok = true)
            } else {
                runCatching { FitrPull.refresh(ctx) }
                    .getOrElse { FitrPull.Result(ok = false, error = it.message ?: "failed") }
            }
        }
        if (!fitr.ok) errors += "fitr: ${fitr.error}"

        val lastPull = Prefs.getLastWorkoutPullMs(ctx)
        val pull = if (fitrConnected) {
            WorkoutPull.Result(ok = true, notModified = true)
        } else if (!pullOnly && !forceCalendar && !forceJournal && lastPull != null &&
            System.currentTimeMillis() - lastPull < FRESH_PULL_MS
        ) {
            WorkoutPull.Result(ok = true, notModified = true)
        } else {
            WorkoutPull.refresh(ctx)
        }
        if (!pull.ok) errors += "workouts: ${pull.error}"

        // 1b. BridgeAthletic -> DB (only when connected)
        val bridge = runCatching { BridgePull.refresh(ctx) }
            .getOrElse { BridgePull.Result(ok = false, error = it.message ?: "failed") }
        if (!bridge.ok) errors += "bridgeathletic: ${bridge.error}"

        if (pullOnly) {
            val watchOk = WearSnapshotPublisher.publish(ctx)
            if (!watchOk) errors += "watch push failed"
            return@withContext Report(
                pull = pull, fitr = fitr, bridge = bridge, calendar = null, wodFilled = 0,
                journal = null, watchOk = watchOk, errors = errors,
                summary = pullSummary(pull, fitr, bridge, errors),
            )
        }

        // 2. Health Connect -> calendar
        val calendar = runCatching { HealthConnectToCalendar.run(ctx, forceCalendar) }
            .getOrElse {
                HealthConnectToCalendar.Result(ok = false, error = it.message ?: "failed")
            }
        if (!calendar.ok) errors += "calendar: ${calendar.error}"

        // 3. Fill calendar WODs from the DB
        val wodFilled = runCatching { WodCalendarSync.run(ctx) }
            .onFailure { errors += "wod fill: ${it.message}" }
            .getOrDefault(0)

        // 4. Journal
        val journal = runCatching { JournalSync.run(ctx, forceJournal) }
            .getOrElse {
                JournalSync.Result(ok = false, error = it.message ?: "failed")
            }
        if (!journal.ok) errors += "journal: ${journal.error}"

        WodWidgetProvider.notifyDataChanged(ctx)

        // 5. Watch
        val watchOk = WearSnapshotPublisher.publish(ctx)
        if (!watchOk) errors += "watch push failed"

        // Summaries for the status card / notification
        val now = System.currentTimeMillis()
        Prefs.setLastSyncSummary(
            ctx,
            buildString {
                append("Last sync: ${dateFmt.format(Date(now))}")
                append("\nCalendar: ${calendar.created} new, ${calendar.skipped} skipped")
                append("\nWODs filled: $wodFilled")
                append("\nJournal: ${journal.created} new, ${journal.updated} updated")
                append("\nWorkouts: ${WorkoutStore.count(ctx)} in database")
                if (!fitr.skipped) {
                    append("\nFitr: ${fitr.created} new, ${fitr.updated} updated")
                }
                if (!bridge.skipped) {
                    append("\nBridgeAthletic: ${bridge.created} new, ${bridge.updated} updated")
                }
                if (errors.isNotEmpty()) append("\n⚠ ${errors.joinToString("; ")}")
            },
        )
        Prefs.setJournalLastSyncSummary(
            ctx,
            "Journal: ${journal.created} new, ${journal.updated} updated" +
                if (journal.skipped > 0) ", ${journal.skipped} skipped" else "",
        )

        val summary = Prefs.getLastSyncSummary(ctx) ?: ""
        NotificationHelper.notifySyncComplete(ctx, summary)
        if (journal.merges.isNotEmpty()) {
            NotificationHelper.notifyMerge(ctx, journal.merges)
        }

        Report(
            pull = pull,
            fitr = fitr,
            bridge = bridge,
            calendar = calendar,
            wodFilled = wodFilled,
            journal = journal,
            watchOk = watchOk,
            errors = errors,
            summary = summary,
        )
    }

    private fun pullSummary(
        pull: WorkoutPull.Result,
        fitr: FitrPull.Result,
        bridge: BridgePull.Result,
        errors: List<String>,
    ): String = buildString {
        append(
            if (pull.notModified) "Workouts unchanged"
            else "Workouts: ${pull.created} new, ${pull.updated} updated"
        )
        if (!fitr.skipped) {
            append("\nFitr: ${fitr.created} new, ${fitr.updated} updated")
        }
        if (!bridge.skipped) {
            append("\nBridgeAthletic: ${bridge.created} new, ${bridge.updated} updated")
        }
        if (errors.isNotEmpty()) append("\n⚠ ${errors.joinToString("; ")}")
    }
}
