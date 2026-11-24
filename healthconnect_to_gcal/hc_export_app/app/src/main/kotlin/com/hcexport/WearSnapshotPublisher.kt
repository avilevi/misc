package com.hcexport

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.hcexport.shared.JournalDayDto
import com.hcexport.shared.JournalItemDto
import com.hcexport.shared.LiveSettingsDto
import com.hcexport.shared.PATH_META
import com.hcexport.shared.PlanDto
import com.hcexport.shared.SNAPSHOT_SCHEMA
import com.hcexport.shared.Snapshot
import com.hcexport.shared.SnapshotCodec
import com.hcexport.shared.StatDto
import com.hcexport.shared.chunkPath
import com.hcexport.shared.generationPrefix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds the phone -> watch snapshot and publishes it as chunked DataItems.
 *
 * The payload is the phone's *rendered* journal — the same days the Journal
 * tab shows, with comments, watch data and AI summaries — plus the plans and
 * values the watch needs to run a live workout. The watch does not rebuild
 * the journal; it renders what the phone already decided.
 *
 * Chunks live under an immutable generation path and the meta item is written
 * last, so the watch never sees a half-written snapshot. The payload is
 * gzipped because a few months of journal entries exceed one DataItem.
 */
object WearSnapshotPublisher {

    private const val TAG = "WearSnapshotPublisher"

    // How far back the watch keeps the journal is the same lookback the journal
    // itself uses (Settings -> Sync & reliability -> Journal & watch history).
    private const val DAYS_AHEAD = 21L
    private const val MAX_NARRATIVE = 3_500
    private const val MAX_CONTENT = 4_000
    private const val MAX_NOTE = 600

    private val pushMutex = Mutex()

    suspend fun publish(ctx: Context): Boolean = pushMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val snapshot = build(ctx)
                val bytes = SnapshotCodec.encode(snapshot)
                val chunks = SnapshotCodec.chunk(bytes)
                val generation = maxOf(
                    System.currentTimeMillis(),
                    Prefs.getWearGeneration(ctx) + 1,
                )

                val dataClient = Wearable.getDataClient(ctx)

                for ((index, chunk) in chunks.withIndex()) {
                    val request = PutDataRequest.create(chunkPath(generation, index))
                        .setData(chunk)
                    Tasks.await(dataClient.putDataItem(request))
                }

                val meta = JSONObject().apply {
                    put("schema", SNAPSHOT_SCHEMA)
                    put("generation", generation)
                    put("chunks", chunks.size)
                    put("sha256", SnapshotCodec.sha256Hex(bytes))
                    put("updatedAt", snapshot.updatedAt)
                }
                Tasks.await(
                    dataClient.putDataItem(
                        PutDataRequest.create(PATH_META)
                            .setData(meta.toString().toByteArray(Charsets.UTF_8))
                            .setUrgent()
                    )
                )

                // Keep the current and previous generation; drop anything older.
                val previous = Prefs.getWearGeneration(ctx)
                val older = Prefs.getWearPreviousGeneration(ctx)
                Prefs.setWearPreviousGeneration(ctx, previous)
                Prefs.setWearGeneration(ctx, generation)
                if (older > 0 && older != previous && older != generation) {
                    runCatching {
                        Tasks.await(
                            dataClient.deleteDataItems(
                                Uri.parse("wear://*${generationPrefix(older)}"),
                                DataClient.FILTER_PREFIX,
                            )
                        )
                    }
                }

                Prefs.setLastWatchPushMs(ctx, System.currentTimeMillis())
                SyncLogger.log(
                    ctx,
                    "Watch snapshot sent: ${snapshot.days.size} days, " +
                        "${snapshot.days.sumOf { it.items.size }} journal entries, " +
                        "${snapshot.plans.size} AI plans, ${chunks.size} chunk(s)",
                )
                true
            } catch (e: Exception) {
                Log.e(TAG, "Watch push failed", e)
                SyncLogger.log(ctx, "ERROR: watch push failed: ${e.message}")
                false
            }
        }
    }

    fun build(ctx: Context, today: LocalDate = LocalDate.now()): Snapshot {
        val daysBack = Prefs.getJournalSyncDaysBack(ctx)
        val from = today.minusDays(daysBack)
        val to = today.plusDays(DAYS_AHEAD)
        val fromIso = from.toString()
        val toIso = to.toString()
        val zone = ZoneId.systemDefault()
        val startMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val workouts = runCatching { WorkoutStore.getBetween(ctx, fromIso, toIso) }
            .getOrDefault(emptyList())
        val events = runCatching { CalendarHelper.findWorkoutEvents(ctx, startMs, endMs) }
            .getOrDefault(emptyList())
        val sessions = runCatching { InteractiveStore.getBetween(ctx, fromIso, toIso) }
            .getOrDefault(emptyList())
        val entries = runCatching {
            JournalStorage.getEntries(ctx).filter { it.date in fromIso..toIso }
        }.getOrDefault(emptyList())
        val scheduled = runCatching {
            ScheduledSessionStore.getAll(ctx).filter { it.date in fromIso..toIso }
        }.getOrDefault(emptyList())
        val notes = runCatching { JournalNotesStore.getAll(ctx) }.getOrDefault(emptyMap())
        val hidden = runCatching { JournalHiddenStore.getAll(ctx) }.getOrDefault(emptySet())
        val sleepPriority = runCatching { Prefs.getSleepSourcePriority(ctx) }.getOrDefault(emptyList())
        val todayIso = today.toString()

        val workoutsByDate = workouts.groupBy { it.date }
        val eventsByDate = events.groupBy { it.date }
        val entriesByDate = entries.groupBy { it.date }
        val sessionsByDate = sessions.groupBy { it.date }
        val scheduledByDate = scheduled.groupBy { it.date }
        val entriesById = entries.associateBy { it.id }
        val sessionsByKey = sessions.associateBy { it.date to it.typeKey }

        val plans = sessions.mapNotNull { record ->
            if (InteractivePlan.fromJson(record.planJson) == null) return@mapNotNull null
            PlanDto(
                date = record.date,
                typeKey = record.typeKey,
                planJson = record.planJson,
                resultsJson = record.resultsJson,
                model = record.model,
                // The plan generation, not the last value typed: this is the
                // version a watch session was run against.
                updatedAt = record.createdAtMs,
            )
        }

        val dayItems = mutableMapOf<String, MutableList<JournalItemDto>>()
        var day = from
        while (!day.isAfter(to)) {
            val iso = day.toString()
            val items = JournalFeed.buildDay(
                date = iso,
                workouts = workoutsByDate[iso].orEmpty(),
                calendarEvents = eventsByDate[iso].orEmpty(),
                entries = entriesByDate[iso].orEmpty(),
                sessions = sessionsByDate[iso].orEmpty().associateBy { it.typeKey },
                notes = notes,
                sleepSourcePriority = sleepPriority,
                scheduledSessions = scheduledByDate[iso].orEmpty(),
                hiddenKeys = hidden,
            ).items
            if (items.isNotEmpty()) {
                dayItems.getOrPut(iso) { mutableListOf() } += items.map { item ->
                    item.toDto(
                        todayIso = todayIso,
                        entriesById = entriesById,
                        planKeys = sessionsByKey.keys,
                    )
                }
            }
            day = day.plusDays(1)
        }

        // A plan the athlete generated on the phone but has not scheduled and
        // has not logged into yet has no journal item. It is still a workout
        // the watch can run, so it gets a planned workout row of its own —
        // keyed like a real entry, so the two never become duplicates.
        val represented = dayItems.values.flatten()
            .filter { it.kind == "workout" }
            .map { it.date to it.typeKey }
            .toSet()
        for (record in sessions) {
            if (record.date !in fromIso..toIso) continue
            if ((record.date to record.typeKey) in represented) continue
            if (JournalKeys.workout(record.date, record.typeKey) in hidden) continue
            val plan = InteractivePlan.fromJson(record.planJson) ?: continue
            val workout = workouts.firstOrNull {
                it.date == record.date && it.typeKey == record.typeKey
            }
            val ai = JournalFeed.aiPlan(plan, InteractiveResults.fromJson(record.resultsJson))
            val entry = JournalItemDto(
                key = JournalKeys.workout(record.date, record.typeKey),
                date = record.date,
                kind = "workout",
                title = WorkoutText.displayType(workout?.typeLabel ?: record.typeKey),
                summary = ai.summary.takeIf { it.isNotBlank() }
                    ?: WorkoutText.summary(workout?.content.orEmpty()),
                body = "",
                typeKey = record.typeKey,
                typeLabel = workout?.typeLabel ?: record.typeKey,
                startMs = 0L,
                endMs = 0L,
                stats = buildList {
                    if (ai.loggedSets > 0) add(StatDto("LOGGED", ai.loggedSets.toString()))
                    ai.totalVolumeKg?.let {
                        add(StatDto("VOLUME KG", JournalFeed.formatVolume(it)))
                    }
                },
                note = notes[JournalKeys.workout(record.date, record.typeKey)]
                    .orEmpty().take(MAX_NOTE),
                scheduled = false,
                planned = record.date > todayIso,
                hasPlan = true,
                planSummary = ai.summary,
                planTotalSets = ai.totalSets,
                planLoggedSets = ai.loggedSets,
                hasWatchData = false,
                content = workout?.content.orEmpty().take(MAX_CONTENT),
            )
            dayItems.getOrPut(record.date) { mutableListOf() } += entry
        }

        val days = dayItems
            .filterValues { it.isNotEmpty() }
            .map { (date, items) -> JournalDayDto(date = date, items = items) }
            .sortedByDescending { it.date }

        return Snapshot(
            schema = SNAPSHOT_SCHEMA,
            updatedAt = System.currentTimeMillis(),
            days = days,
            plans = plans,
            settings = LiveSettingsDto(
                leadInSeconds = Prefs.getLiveLeadInSeconds(ctx),
                intervalSeconds = Prefs.getLiveSetIntervalSeconds(ctx),
                sound = Prefs.getLiveSound(ctx),
                vibration = Prefs.getLiveVibration(ctx),
            ),
        )
    }

    private fun JournalItem.toDto(
        todayIso: String,
        entriesById: Map<String, JournalEntry>,
        planKeys: Set<Pair<String, String>>,
    ): JournalItemDto = when (this) {
        is JournalItem.Workout -> {
            val bio = biometrics.firstOrNull()
            val plan = plan
            JournalItemDto(
                key = key,
                date = date,
                kind = "workout",
                title = WorkoutText.displayType(typeLabel),
                summary = plan?.summary?.takeIf { it.isNotBlank() }
                    ?: WorkoutText.summary(content),
                body = "",
                typeKey = typeKey,
                typeLabel = typeLabel,
                startMs = startMs ?: 0L,
                endMs = endMs ?: 0L,
                stats = buildList {
                    durationMin?.let { add(StatDto("DURATION", JournalFeed.formatDuration(it))) }
                    plan?.let { ai ->
                        if (ai.loggedSets > 0) {
                            add(StatDto("LOGGED", ai.loggedSets.toString()))
                        }
                        ai.totalVolumeKg?.let {
                            add(StatDto("VOLUME KG", JournalFeed.formatVolume(it)))
                        }
                    }
                    bio?.avgHrBpm?.let { add(StatDto("AVG BPM", it.toInt().toString())) }
                },
                note = note.take(MAX_NOTE),
                scheduled = scheduled,
                planned = date > todayIso,
                hasPlan = (date to typeKey) in planKeys,
                planSummary = plan?.summary.orEmpty(),
                planTotalSets = plan?.totalSets ?: 0,
                planLoggedSets = plan?.loggedSets ?: 0,
                hasWatchData = biometrics.isNotEmpty(),
                content = content.take(MAX_CONTENT),
            )
        }

        is JournalItem.Activity -> {
            val bio = biometrics
            val entry = entriesById[bio.entryId]
            val typeLabel = CalendarHelper.EXERCISE_TYPES[bio.typeCode]?.second ?: "Activity"
            val narrative = entry?.narrativeText()?.take(MAX_NARRATIVE).orEmpty()
            JournalItemDto(
                key = key,
                date = date,
                kind = "activity",
                title = bio.title.ifBlank { typeLabel },
                summary = narrative.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                body = narrative,
                typeKey = "activity",
                typeLabel = typeLabel,
                startMs = bio.startMs,
                endMs = bio.endMs,
                stats = buildList {
                    add(StatDto("DURATION", JournalFeed.formatDuration(bio.durationMin)))
                    bio.distanceM?.let { add(StatDto("DISTANCE KM", JournalFeed.formatDistance(it))) }
                    bio.avgHrBpm?.let { add(StatDto("AVG BPM", it.toInt().toString())) }
                    bio.caloriesKcal?.let { add(StatDto("KCAL", it.toInt().toString())) }
                },
                note = note.take(MAX_NOTE),
                scheduled = false,
                planned = false,
                hasPlan = false,
                planSummary = "",
                planTotalSets = 0,
                planLoggedSets = 0,
                hasWatchData = false,
                content = "",
            )
        }

        is JournalItem.Sleep -> {
            fun minutes(stage: String) = stages.firstOrNull { it.name == stage }?.minutes
            val entry = entriesById[entryId]
            JournalItemDto(
                key = key,
                date = date,
                kind = "sleep",
                title = "Sleep",
                summary = buildString {
                    append(JournalFeed.formatDuration(durationMin))
                    minutes("Deep Sleep")?.let {
                        append(" · Deep ").append(JournalFeed.formatDuration(it))
                    }
                    minutes("REM")?.let {
                        append(" · REM ").append(JournalFeed.formatDuration(it))
                    }
                },
                body = entry?.narrativeText()?.take(MAX_NARRATIVE).orEmpty(),
                typeKey = "sleep",
                typeLabel = "Sleep",
                startMs = startMs,
                endMs = endMs,
                stats = buildList {
                    add(StatDto("ASLEEP", JournalFeed.formatDuration(durationMin)))
                    minutes("Deep Sleep")?.let { add(StatDto("DEEP", JournalFeed.formatDuration(it))) }
                    minutes("REM")?.let { add(StatDto("REM", JournalFeed.formatDuration(it))) }
                    minutes("Awake")?.let { add(StatDto("AWAKE", JournalFeed.formatDuration(it))) }
                },
                note = note.take(MAX_NOTE),
                scheduled = false,
                planned = false,
                hasPlan = false,
                planSummary = "",
                planTotalSets = 0,
                planLoggedSets = 0,
                hasWatchData = false,
                content = "",
            )
        }
    }
}
