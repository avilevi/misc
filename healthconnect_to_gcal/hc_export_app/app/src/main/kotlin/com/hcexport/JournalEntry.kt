package com.hcexport

import org.json.JSONObject
import java.util.UUID

data class JournalEntry(
    val id: String,
    // ── Original sync data (immutable) ──
    val originalStartMs: Long,
    val originalEndMs: Long,
    val originalTypeCode: Int,
    val originalTitle: String,
    val originalDistanceM: Double?,
    val originalCaloriesKcal: Double?,
    val originalAvgHrBpm: Double?,
    val originalMaxHrBpm: Double?,
    val originalPaceSecPerKm: Double?,
    val originalStepsCount: Long?,
    val originalNotes: String,
    val originalSourcePkg: String,
    // ── User overrides (null = use original) ──
    val customTitle: String?,
    val customNotes: String?,
    // ── Metadata ──
    val createdAtMs: Long,
    val updatedAtMs: Long,
) {
    fun effectiveTitle(): String = customTitle ?: originalTitle

    fun effectiveNotes(): String = customNotes ?: originalNotes

    fun hasCustomizations(): Boolean = customTitle != null || customNotes != null

    fun reverted(): JournalEntry = copy(
        customTitle = null,
        customNotes = null,
        updatedAtMs = System.currentTimeMillis(),
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("os", originalStartMs)
        put("oe", originalEndMs)
        put("tc", originalTypeCode)
        put("ot", originalTitle)
        originalDistanceM?.let { put("od", it) }
        originalCaloriesKcal?.let { put("oc", it) }
        originalAvgHrBpm?.let { put("oa", it) }
        originalMaxHrBpm?.let { put("om", it) }
        originalPaceSecPerKm?.let { put("op", it) }
        originalStepsCount?.let { put("os2", it) }
        put("on", originalNotes)
        put("osp", originalSourcePkg)
        customTitle?.let { put("ct", it) }
        customNotes?.let { put("cn", it) }
        put("ca", createdAtMs)
        put("ua", updatedAtMs)
    }

    companion object {
        fun fromJson(obj: JSONObject): JournalEntry = JournalEntry(
            id                 = obj.getString("id"),
            originalStartMs    = obj.getLong("os"),
            originalEndMs      = obj.getLong("oe"),
            originalTypeCode   = obj.getInt("tc"),
            originalTitle      = obj.optString("ot", ""),
            originalDistanceM  = if (obj.has("od")) obj.getDouble("od") else null,
            originalCaloriesKcal = if (obj.has("oc")) obj.getDouble("oc") else null,
            originalAvgHrBpm   = if (obj.has("oa")) obj.getDouble("oa") else null,
            originalMaxHrBpm   = if (obj.has("om")) obj.getDouble("om") else null,
            originalPaceSecPerKm = if (obj.has("op")) obj.getDouble("op") else null,
            originalStepsCount = if (obj.has("os2")) obj.getLong("os2") else null,
            originalNotes      = obj.optString("on", ""),
            originalSourcePkg  = obj.optString("osp", ""),
            customTitle        = obj.optString("ct", "").ifEmpty { null },
            customNotes        = obj.optString("cn", "").ifEmpty { null },
            createdAtMs        = obj.getLong("ca"),
            updatedAtMs        = obj.getLong("ua"),
        )

        fun fromExerciseEvent(event: ExerciseEvent): JournalEntry = JournalEntry(
            id                  = UUID.randomUUID().toString(),
            originalStartMs     = event.startMs,
            originalEndMs       = event.endMs,
            originalTypeCode    = event.typeCode,
            originalTitle       = event.title,
            originalDistanceM   = event.distanceM,
            originalCaloriesKcal = event.caloriesKcal,
            originalAvgHrBpm    = event.avgHrBpm,
            originalMaxHrBpm    = event.maxHrBpm,
            originalPaceSecPerKm = event.paceSecPerKm,
            originalStepsCount  = event.stepsCount,
            originalNotes       = event.notes,
            originalSourcePkg   = event.sourcePkg,
            customTitle         = null,
            customNotes         = null,
            createdAtMs         = System.currentTimeMillis(),
            updatedAtMs         = System.currentTimeMillis(),
        )
    }
}
