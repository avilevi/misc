package com.hcexport.shared

import org.json.JSONObject

/**
 * One live session's values, sent from the watch to the phone as a DataItem.
 *
 * [resultsJson] is the same `{scope:{metric:value}}` document the phone stores
 * in interactive.db. [updatedAt] is when the watch last committed a value; the
 * phone keeps whichever copy is newer, so a watch and a phone editing the same
 * workout never fight over an older state.
 */
data class LiveResultDto(
    val date: String,
    val typeKey: String,
    val resultsJson: String,
    val updatedAt: Long,
    /** The plan generation the watch ran; stale values are not accepted. */
    val planUpdatedAt: Long = 0L,
    /** The values the watch started from; only their changes are applied. */
    val baseResultsJson: String = "{}",
)

object LiveResultCodec {

    fun encode(result: LiveResultDto): String = JSONObject().apply {
        put("schema", SNAPSHOT_SCHEMA)
        put("date", result.date)
        put("typeKey", result.typeKey)
        put("results", result.resultsJson)
        put("updatedAt", result.updatedAt)
        put("planUpdatedAt", result.planUpdatedAt)
        put("baseResults", result.baseResultsJson)
    }.toString()

    fun decode(raw: String): LiveResultDto? = runCatching {
        val obj = JSONObject(raw)
        val date = obj.optString("date")
        val typeKey = obj.optString("typeKey")
        if (date.length != 10 || typeKey.isBlank()) return null
        LiveResultDto(
            date = date,
            typeKey = typeKey,
            resultsJson = obj.optString("results", "{}"),
            updatedAt = obj.optLong("updatedAt", 0L),
            planUpdatedAt = obj.optLong("planUpdatedAt", 0L),
            baseResultsJson = obj.optString("baseResults", "{}"),
        )
    }.getOrNull()
}
