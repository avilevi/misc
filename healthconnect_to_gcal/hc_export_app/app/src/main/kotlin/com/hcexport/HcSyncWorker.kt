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
        private const val DAYS_BACK = 90L
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting sync")
        return try {
            val client     = HealthConnectClient.getOrCreate(applicationContext)
            val now        = Instant.now()
            val start      = now.minus(DAYS_BACK, ChronoUnit.DAYS)
            val range      = TimeRangeFilter.between(start, now)
            val calendarId = CalendarHelper.findCalendarId(applicationContext)
                ?: return Result.failure().also { Log.e(TAG, "No calendar found") }

            var created = 0
            var skipped = 0

            // ── Exercises ──────────────────────────────────────────────────
            val exerciseResponse = client.readRecords(
                ReadRecordsRequest(ExerciseSessionRecord::class, range)
            )
            Log.i(TAG, "Found ${exerciseResponse.records.size} exercise sessions")

            for (session in exerciseResponse.records) {
                val sessionRange = TimeRangeFilter.between(session.startTime, session.endTime)
                var distanceM:    Double? = null
                var caloriesKcal: Double? = null
                var avgHr:        Double? = null
                var maxHr:        Double? = null

                runCatching {
                    val agg = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(
                                DistanceRecord.DISTANCE_TOTAL,
                                TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                            ),
                            timeRangeFilter = sessionRange,
                        )
                    )
                    distanceM    = agg[DistanceRecord.DISTANCE_TOTAL]?.inMeters
                    caloriesKcal = agg[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                }

                runCatching {
                    val hrResponse = client.readRecords(
                        ReadRecordsRequest(HeartRateRecord::class, sessionRange)
                    )
                    val samples = hrResponse.records.flatMap { it.samples }
                    if (samples.isNotEmpty()) {
                        avgHr = samples.map { it.beatsPerMinute.toDouble() }.average()
                        maxHr = samples.maxOf { it.beatsPerMinute }.toDouble()
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
                    notes        = session.notes ?: "",
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
            val sleepResponse = client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, range)
            )
            Log.i(TAG, "Found ${sleepResponse.records.size} sleep sessions")

            for (session in sleepResponse.records) {
                val event = SleepEvent(
                    startMs = session.startTime.toEpochMilli(),
                    endMs   = session.endTime.toEpochMilli(),
                    stages  = session.stages.map { stage ->
                        SleepStage(
                            startMs   = stage.startTime.toEpochMilli(),
                            endMs     = stage.endTime.toEpochMilli(),
                            stageCode = stage.stage,
                        )
                    },
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
