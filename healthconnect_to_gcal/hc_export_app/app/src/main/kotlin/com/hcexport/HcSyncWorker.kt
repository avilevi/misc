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
import java.time.Instant
import java.time.temporal.ChronoUnit

class HcSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        private const val TAG = "HcSyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting sync")
        return try {
            val client     = HealthConnectClient.getOrCreate(applicationContext)
            val now        = Instant.now()
            val daysBack   = Prefs.getSyncDaysBack(applicationContext)
            val start      = now.minus(daysBack, ChronoUnit.DAYS)
            val range = TimeRangeFilter.between(start, now)

            val calendarId = Prefs.getCalendarId(applicationContext)
                ?: CalendarHelper.findCalendarId(applicationContext)
                ?: return Result.failure().also { Log.e(TAG, "No calendar found") }

            var created = 0
            var skipped = 0

            // ── Exercises ──────────────────────────────────────────────────
            val allExercise = client.readRecords(
                ReadRecordsRequest(ExerciseSessionRecord::class, range)
            ).records
            Log.i(TAG, "Found ${allExercise.size} exercise sessions")

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

                val eventTitle = CalendarHelper.exerciseTitle(event)
                if (CalendarHelper.eventExists(applicationContext, calendarId, eventTitle, event.startMs)) {
                    skipped++
                } else {
                    CalendarHelper.insertEvent(
                        applicationContext, calendarId,
                        eventTitle,
                        CalendarHelper.exerciseDescription(event),
                        event.startMs, event.endMs,
                    )
                    created++
                }
            }

            // ── Sleep sessions ─────────────────────────────────────────────
            val allSleep = client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, range)
            ).records
            Log.i(TAG, "Found ${allSleep.size} sleep sessions")

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

                val eventTitle = CalendarHelper.sleepTitle(event)
                if (CalendarHelper.eventExists(applicationContext, calendarId, eventTitle, event.startMs)) {
                    skipped++
                } else {
                    CalendarHelper.insertEvent(
                        applicationContext, calendarId,
                        eventTitle,
                        CalendarHelper.sleepDescription(event),
                        event.startMs, event.endMs,
                    )
                    created++
                }
            }

            Log.i(TAG, "Sync complete: $created created, $skipped skipped")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            Result.failure()
        }
    }
}
