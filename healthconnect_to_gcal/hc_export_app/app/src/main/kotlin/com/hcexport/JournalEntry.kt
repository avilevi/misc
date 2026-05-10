package com.hcexport

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

data class JournalEntry(
    val id: String,
    val date: String,
    val entryType: String,
    val originalDataJson: String,
    val customDataJson: String?,
    val customNarrative: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
) {
    fun effectiveDataJson(): String = customDataJson ?: originalDataJson

    fun narrativeText(): String = customNarrative ?: DiaryNarrator.generate(this)

    fun hasCustomizations(): Boolean = customDataJson != null || customNarrative != null

    fun reverted(): JournalEntry = copy(
        customDataJson = null,
        customNarrative = null,
        updatedAtMs = System.currentTimeMillis(),
    )

    fun editDataField(key: String, value: String): JournalEntry {
        val base = JSONObject(effectiveDataJson())
        base.put(key, value)
        return copy(
            customDataJson = base.toString(),
            customNarrative = null,
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    fun editDataFields(updates: Map<String, String?>): JournalEntry {
        val base = JSONObject(effectiveDataJson())
        for ((key, value) in updates) {
            if (value != null) base.put(key, value) else base.remove(key)
        }
        return copy(
            customDataJson = base.toString(),
            customNarrative = null,
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("dt", date)
        put("et", entryType)
        put("oj", originalDataJson)
        customDataJson?.let { put("cj", it) }
        customNarrative?.let { put("cn", it) }
        put("ca", createdAtMs)
        put("ua", updatedAtMs)
    }

    companion object {
        private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        fun fromJson(obj: JSONObject): JournalEntry = JournalEntry(
            id               = obj.getString("id"),
            date             = obj.getString("dt"),
            entryType        = obj.getString("et"),
            originalDataJson = obj.getString("oj"),
            customDataJson   = obj.optString("cj", "").ifEmpty { null },
            customNarrative  = obj.optString("cn", "").ifEmpty { null },
            createdAtMs      = obj.getLong("ca"),
            updatedAtMs      = obj.getLong("ua"),
        )

        fun fromExerciseEvent(event: ExerciseEvent): JournalEntry {
            val cal = Calendar.getInstance().apply { timeInMillis = event.startMs }
            return JournalEntry(
                id               = UUID.randomUUID().toString(),
                date             = dateFmt.format(Date(event.startMs)),
                entryType        = "exercise",
                originalDataJson = event.toJson().toString(),
                customDataJson   = null,
                customNarrative  = null,
                createdAtMs      = System.currentTimeMillis(),
                updatedAtMs      = System.currentTimeMillis(),
            )
        }

        fun fromSleepEvent(event: SleepEvent): JournalEntry {
            // Sleep is placed on the day it ends, not when it starts
            return JournalEntry(
                id               = UUID.randomUUID().toString(),
                date             = dateFmt.format(Date(event.endMs)),
                entryType        = "sleep",
                originalDataJson = event.toJson().toString(),
                customDataJson   = null,
                customNarrative  = null,
                createdAtMs      = System.currentTimeMillis(),
                updatedAtMs      = System.currentTimeMillis(),
            )
        }

        fun fromWod(dateStr: String, startMs: Long, endMs: Long, content: String): JournalEntry {
            val json = JSONObject().apply {
                put("sm", startMs)
                put("em", endMs)
                put("date", dateStr)
                put("wod", content)
                put("sp", "wod")
            }
            return JournalEntry(
                id               = UUID.randomUUID().toString(),
                date             = dateStr,
                entryType        = "wod",
                originalDataJson = json.toString(),
                customDataJson   = null,
                customNarrative  = content,
                createdAtMs      = System.currentTimeMillis(),
                updatedAtMs      = System.currentTimeMillis(),
            )
        }
    }
}
