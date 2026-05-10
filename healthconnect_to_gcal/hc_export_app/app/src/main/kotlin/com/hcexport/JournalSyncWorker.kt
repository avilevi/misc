package com.hcexport

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

class JournalSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        private const val TAG = "JournalSyncWorker"
        const val KEY_FORCE_RESYNC = "force_resync"
    }

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting journal sync")
        val forceResync = inputData.getBoolean(KEY_FORCE_RESYNC, false)

        var summary: String? = null
        val result = try {
            val client = HealthConnectClient.getOrCreate(applicationContext)
            val now    = Instant.now()

            val start = if (forceResync || Prefs.getJournalLastSyncTime(applicationContext) == null) {
                val daysBack = Prefs.getJournalSyncDaysBack(applicationContext)
                now.minus(daysBack, ChronoUnit.DAYS)
            } else {
                Instant.ofEpochMilli(Prefs.getJournalLastSyncTime(applicationContext)!!)
            }
            val range = TimeRangeFilter.between(start, now)

            SyncLogger.log(applicationContext, "=== Journal sync started (from ${dateFmt.format(Date(start.toEpochMilli()))}${if (forceResync) ", forced" else ""}) ===")

            var created = 0
            var skipped = 0
            val merges = mutableListOf<Pair<String, String>>()

            // ── Exercises ──────────────────────────────────────────────────
            val allExercise = client.readRecords(
                ReadRecordsRequest(ExerciseSessionRecord::class, range)
            ).records
            Log.i(TAG, "Found ${allExercise.size} exercise sessions for journal")

            Prefs.addKnownExerciseSources(
                applicationContext,
                allExercise.map { it.metadata.dataOrigin.packageName }.toSet()
            )

            val exercisePriority = Prefs.getExerciseSourcePriority(applicationContext)
            val exercises = if (exercisePriority.isEmpty()) allExercise else
                SourceFilter.pickByPriority(
                    allExercise,
                    { it.startTime.toEpochMilli() },
                    { it.endTime.toEpochMilli() },
                    { it.metadata.dataOrigin.packageName },
                    exercisePriority,
                )

            SyncLogger.log(applicationContext, "Journal exercise sessions: ${exercises.size}")

            for (session in exercises) {
                val sessionRange = TimeRangeFilter.between(session.startTime, session.endTime)
                var distanceM:    Double? = null
                var caloriesKcal: Double? = null
                var paceSecPerKm: Double? = null
                var stepsCount:   Long?   = null
                var avgHr:        Double? = null
                var maxHr:        Double? = null
                var hrSamples:    List<HrSample> = emptyList()

                runCatching {
                    val agg = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(
                                DistanceRecord.DISTANCE_TOTAL,
                                TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                                SpeedRecord.SPEED_AVG,
                                StepsRecord.COUNT_TOTAL,
                            ),
                            timeRangeFilter = sessionRange,
                        )
                    )
                    distanceM    = agg[DistanceRecord.DISTANCE_TOTAL]?.inMeters
                    caloriesKcal = agg[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                    stepsCount   = agg[StepsRecord.COUNT_TOTAL]
                    val avgSpeedMps = agg[SpeedRecord.SPEED_AVG]?.inMetersPerSecond
                    if (avgSpeedMps != null && avgSpeedMps > 0.1)
                        paceSecPerKm = 1000.0 / avgSpeedMps
                }

                runCatching {
                    val hrResponse = client.readRecords(
                        ReadRecordsRequest(HeartRateRecord::class, sessionRange)
                    )
                    val samples = hrResponse.records.flatMap { it.samples }
                    if (samples.isNotEmpty()) {
                        avgHr = samples.map { it.beatsPerMinute.toDouble() }.average()
                        maxHr = samples.maxOf { it.beatsPerMinute }.toDouble()

                        val sessionStartMs = session.startTime.toEpochMilli()
                        val durationMin    = (session.endTime.toEpochMilli() - sessionStartMs) / 60_000
                        val bucketMin      = when { durationMin <= 20 -> 1; durationMin <= 90 -> 5; else -> 10 }

                        val buckets = mutableMapOf<Int, MutableList<Int>>()
                        for (s in samples) {
                            val bin = ((s.time.toEpochMilli() - sessionStartMs) / 60_000 / bucketMin).toInt() * bucketMin
                            buckets.getOrPut(bin) { mutableListOf() }.add(s.beatsPerMinute.toInt())
                        }
                        hrSamples = buckets.entries
                            .sortedBy { it.key }
                            .map { (min, bpms) -> HrSample(min, bpms.average().toInt()) }
                    }
                }

                val event = ExerciseEvent(
                    startMs      = session.startTime.toEpochMilli(),
                    endMs        = session.endTime.toEpochMilli(),
                    typeCode     = session.exerciseType,
                    title        = session.title ?: "",
                    distanceM    = distanceM,
                    caloriesKcal = caloriesKcal,
                    avgHrBpm     = avgHr,
                    maxHrBpm     = maxHr,
                    hrSamples    = hrSamples,
                    paceSecPerKm = paceSecPerKm,
                    stepsCount   = stepsCount,
                    notes        = session.notes ?: "",
                    sourcePkg    = session.metadata.dataOrigin.packageName,
                )

                // Merge overlapping WOD entries
                var mergedWod = ""
                val overlapping = JournalStorage.findOverlappingWod(
                    applicationContext, event.startMs, event.endMs
                )
                for (wodEntry in overlapping) {
                    val wodJson = org.json.JSONObject(wodEntry.effectiveDataJson())
                    mergedWod += if (mergedWod.isNotEmpty()) "\n\n" else ""
                    mergedWod += wodJson.optString("wod", "")
                    JournalStorage.deleteEntry(applicationContext, wodEntry.id)
                    merges += eventTitle to wodEntry.date
                    SyncLogger.log(applicationContext, "  MERGE WOD ${wodEntry.date} into exercise")
                }
                val finalNotes = if (mergedWod.isNotEmpty()) {
                    val existing = event.notes
                    if (existing.isNotBlank()) "$existing\n\n---\n$mergedWod" else mergedWod
                } else event.notes
                val mergedEvent = event.copy(notes = finalNotes)

                val entry = JournalEntry.fromExerciseEvent(mergedEvent)
                val eventTitle = CalendarHelper.exerciseTitle(mergedEvent)
                if (JournalStorage.addEntryIfAbsent(applicationContext, entry)) {
                    created++
                    SyncLogger.log(applicationContext, "  NEW  journal exercise: $eventTitle (${dateFmt.format(Date(mergedEvent.startMs))})")
                } else {
                    skipped++
                    SyncLogger.log(applicationContext, "  SKIP journal exercise: $eventTitle (${dateFmt.format(Date(mergedEvent.startMs))})")
                }
            }

            // ── Sleep sessions ─────────────────────────────────────────────
            val allSleep = client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, range)
            ).records
            Log.i(TAG, "Found ${allSleep.size} sleep sessions for journal")

            Prefs.addKnownSleepSources(
                applicationContext,
                allSleep.map { it.metadata.dataOrigin.packageName }.toSet()
            )

            val sleepPriority = Prefs.getSleepSourcePriority(applicationContext)
            val sleepSessions = if (sleepPriority.isEmpty()) allSleep else
                SourceFilter.pickByPriority(
                    allSleep,
                    { it.startTime.toEpochMilli() },
                    { it.endTime.toEpochMilli() },
                    { it.metadata.dataOrigin.packageName },
                    sleepPriority,
                )

            SyncLogger.log(applicationContext, "Journal sleep sessions: ${sleepSessions.size}")

            for (session in sleepSessions) {
                val event = SleepEvent(
                    startMs  = session.startTime.toEpochMilli(),
                    endMs    = session.endTime.toEpochMilli(),
                    stages   = session.stages.map { stage ->
                        SleepStage(
                            startMs   = stage.startTime.toEpochMilli(),
                            endMs     = stage.endTime.toEpochMilli(),
                            stageCode = stage.stage,
                        )
                    },
                    sourcePkg = session.metadata.dataOrigin.packageName,
                )

                val entry = JournalEntry.fromSleepEvent(event)
                val eventTitle = CalendarHelper.sleepTitle(event)
                if (JournalStorage.addEntryIfAbsent(applicationContext, entry)) {
                    created++
                    SyncLogger.log(applicationContext, "  NEW  journal sleep: $eventTitle (${dateFmt.format(Date(event.startMs))})")
                } else {
                    skipped++
                    SyncLogger.log(applicationContext, "  SKIP journal sleep: $eventTitle (${dateFmt.format(Date(event.startMs))})")
                }
            }

            // ── WOD entries ───────────────────────────────────────────────
            val wods = WodSync.getWodsForJournal(applicationContext)
            for (wod in wods) {
                // Skip if an HC entry already covers this WOD time slot (merged)
                if (JournalStorage.hasOverlappingEntry(applicationContext, wod.startMs, wod.endMs)) {
                    skipped++
                    SyncLogger.log(applicationContext, "  SKIP journal WOD: ${wod.dateStr} (already covered)")
                    continue
                }
                val entry = JournalEntry.fromWod(wod.dateStr, wod.startMs, wod.endMs, wod.content)
                if (JournalStorage.addEntryIfAbsent(applicationContext, entry)) {
                    created++
                    SyncLogger.log(applicationContext, "  NEW  journal WOD: ${wod.dateStr}")
                } else {
                    skipped++
                    SyncLogger.log(applicationContext, "  SKIP journal WOD: ${wod.dateStr}")
                }
            }

            Log.i(TAG, "Journal sync complete: $created created, $skipped skipped")

            Prefs.setJournalLastSyncTime(applicationContext, now.toEpochMilli())
            summary = buildString {
                append("Journal: ${dateFmt.format(Date(now.toEpochMilli()))}")
                append("\n$created new, $skipped skipped")
            }
            Prefs.setJournalLastSyncSummary(applicationContext, summary!!)
            SyncLogger.log(applicationContext, "=== Journal sync done: $created new, $skipped skipped ===")

            WodWidgetProvider.notifyDataChanged(applicationContext)

            if (merges.isNotEmpty())
                NotificationHelper.notifyMerge(applicationContext, merges)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Journal sync failed: ${e.message}", e)
            SyncLogger.log(applicationContext, "ERROR: Journal sync failed: ${e.message}")
            Result.failure()
        }

        summary?.let { NotificationHelper.notifySyncComplete(applicationContext, it) }

        return result
    }
}
