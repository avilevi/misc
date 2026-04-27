package com.hcexport

data class HrSample(val offsetMin: Int, val bpm: Int)

data class ExerciseEvent(
    val startMs: Long,
    val endMs: Long,
    val typeCode: Int,
    val title: String,
    val distanceM: Double?,
    val caloriesKcal: Double?,
    val avgHrBpm: Double?,
    val maxHrBpm: Double?,
    val hrSamples: List<HrSample>,
    val paceSecPerKm: Double?,
    val stepsCount: Long?,
    val notes: String,
    val sourcePkg: String,
)

data class SleepEvent(
    val startMs: Long,
    val endMs: Long,
    val stages: List<SleepStage>,
    val sourcePkg: String,
)

data class SleepStage(
    val startMs: Long,
    val endMs: Long,
    val stageCode: Int,
)
