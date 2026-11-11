package com.hcexport

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

/**
 * One place for the Health Connect reading that the calendar and journal phases both
 * need. Previously this ~100-line block was duplicated in both workers.
 */
object HealthConnectReader {

    suspend fun readExercises(ctx: Context, start: Instant, end: Instant): List<ExerciseEvent> {
        val client = HealthConnectClient.getOrCreate(ctx)
        val range = TimeRangeFilter.between(start, end)
        val all = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range)).records

        Prefs.addKnownExerciseSources(
            ctx, all.map { it.metadata.dataOrigin.packageName }.toSet()
        )
        val priority = Prefs.getExerciseSourcePriority(ctx)
        val sessions = if (priority.isEmpty()) all else
            SourceFilter.pickByPriority(
                all,
                { it.startTime.toEpochMilli() },
                { it.endTime.toEpochMilli() },
                { it.metadata.dataOrigin.packageName },
                priority,
            )

        return sessions.map { session -> buildExercise(client, session) }
    }

    suspend fun readSleep(ctx: Context, start: Instant, end: Instant): List<SleepEvent> {
        val client = HealthConnectClient.getOrCreate(ctx)
        val range = TimeRangeFilter.between(start, end)
        val all = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range)).records

        Prefs.addKnownSleepSources(
            ctx, all.map { it.metadata.dataOrigin.packageName }.toSet()
        )
        val priority = Prefs.getSleepSourcePriority(ctx)
        val sessions = if (priority.isEmpty()) all else
            SourceFilter.pickByPriority(
                all,
                { it.startTime.toEpochMilli() },
                { it.endTime.toEpochMilli() },
                { it.metadata.dataOrigin.packageName },
                priority,
            )

        return sessions.map { session ->
            SleepEvent(
                startMs = session.startTime.toEpochMilli(),
                endMs = session.endTime.toEpochMilli(),
                stages = session.stages.map { stage ->
                    SleepStage(
                        startMs = stage.startTime.toEpochMilli(),
                        endMs = stage.endTime.toEpochMilli(),
                        stageCode = stage.stage,
                    )
                },
                sourcePkg = session.metadata.dataOrigin.packageName,
            )
        }
    }

    private suspend fun buildExercise(
        client: HealthConnectClient,
        session: ExerciseSessionRecord,
    ): ExerciseEvent {
        val sessionRange = TimeRangeFilter.between(session.startTime, session.endTime)
        var distanceM: Double? = null
        var caloriesKcal: Double? = null
        var paceSecPerKm: Double? = null
        var stepsCount: Long? = null
        var avgHr: Double? = null
        var maxHr: Double? = null
        var hrSamples: List<HrSample> = emptyList()

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
            distanceM = agg[DistanceRecord.DISTANCE_TOTAL]?.inMeters
            caloriesKcal = agg[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
            stepsCount = agg[StepsRecord.COUNT_TOTAL]
            val avgSpeedMps = agg[SpeedRecord.SPEED_AVG]?.inMetersPerSecond
            if (avgSpeedMps != null && avgSpeedMps > 0.1) {
                paceSecPerKm = 1000.0 / avgSpeedMps
            }
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
                val durationMin = (session.endTime.toEpochMilli() - sessionStartMs) / 60_000
                val bucketMin = when {
                    durationMin <= 20 -> 1
                    durationMin <= 90 -> 5
                    else -> 10
                }

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

        return ExerciseEvent(
            startMs = session.startTime.toEpochMilli(),
            endMs = session.endTime.toEpochMilli(),
            typeCode = session.exerciseType,
            title = session.title ?: "",
            distanceM = distanceM,
            caloriesKcal = caloriesKcal,
            avgHrBpm = avgHr,
            maxHrBpm = maxHr,
            hrSamples = hrSamples,
            paceSecPerKm = paceSecPerKm,
            stepsCount = stepsCount,
            notes = session.notes ?: "",
            sourcePkg = session.metadata.dataOrigin.packageName,
        )
    }
}
