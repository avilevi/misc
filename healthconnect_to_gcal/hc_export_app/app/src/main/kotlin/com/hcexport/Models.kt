package com.hcexport

import org.json.JSONArray
import org.json.JSONObject

data class HrSample(val offsetMin: Int, val bpm: Int) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("o", offsetMin)
        put("b", bpm)
    }
    companion object {
        fun fromJson(obj: JSONObject): HrSample =
            HrSample(obj.getInt("o"), obj.getInt("b"))
    }
}

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
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sm", startMs)
        put("em", endMs)
        put("tc", typeCode)
        put("tt", title)
        distanceM?.let { put("dm", it) }
        caloriesKcal?.let { put("ck", it) }
        avgHrBpm?.let { put("ah", it) }
        maxHrBpm?.let { put("mh", it) }
        if (hrSamples.isNotEmpty()) {
            put("hr", JSONArray(hrSamples.map { it.toJson() }))
        }
        paceSecPerKm?.let { put("pp", it) }
        stepsCount?.let { put("st", it) }
        put("nt", notes)
        put("sp", sourcePkg)
    }
}

data class SleepEvent(
    val startMs: Long,
    val endMs: Long,
    val stages: List<SleepStage>,
    val sourcePkg: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sm", startMs)
        put("em", endMs)
        put("sp", sourcePkg)
        if (stages.isNotEmpty()) {
            put("sg", JSONArray(stages.map { it.toJson() }))
        }
    }
}

data class SleepStage(
    val startMs: Long,
    val endMs: Long,
    val stageCode: Int,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sm", startMs)
        put("em", endMs)
        put("sc", stageCode)
    }
}
