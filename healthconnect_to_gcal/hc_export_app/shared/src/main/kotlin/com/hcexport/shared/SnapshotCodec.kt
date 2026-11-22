package com.hcexport.shared

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * JSON + gzip + chunking for the watch snapshot.
 *
 * The phone writes [encode]d bytes as 60 KB DataItems and the watch reads them
 * back. The checksum covers exactly the bytes that were encoded, so a partially
 * replicated generation is rejected instead of being rendered as truncated data.
 */
object SnapshotCodec {

    const val CHUNK_SIZE = 60_000

    fun encode(snapshot: Snapshot): ByteArray = gzip(toJson(snapshot).toString().toByteArray(Charsets.UTF_8))

    fun decode(bytes: ByteArray): Snapshot = fromJson(JSONObject(String(gunzip(bytes), Charsets.UTF_8)))

    fun chunk(bytes: ByteArray, chunkSize: Int = CHUNK_SIZE): List<ByteArray> {
        if (bytes.isEmpty()) return listOf(ByteArray(0))
        val out = ArrayList<ByteArray>((bytes.size + chunkSize - 1) / chunkSize)
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + chunkSize, bytes.size)
            out += bytes.copyOfRange(offset, end)
            offset = end
        }
        return out
    }

    fun join(chunks: List<ByteArray>): ByteArray {
        val total = chunks.sumOf { it.size }
        val out = ByteArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(out, offset)
            offset += chunk.size
        }
        return out
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // ── JSON ────────────────────────────────────────────────────────────────

    fun toJson(snapshot: Snapshot): JSONObject = JSONObject().apply {
        put("schema", snapshot.schema)
        put("updatedAt", snapshot.updatedAt)
        put("settings", snapshot.settings.toJson())
        put("days", JSONArray(snapshot.days.map { it.toJson() }))
        put("plans", JSONArray(snapshot.plans.map { it.toJson() }))
    }

    fun fromJson(obj: JSONObject): Snapshot {
        val schema = obj.optInt("schema", SNAPSHOT_SCHEMA)
        return Snapshot(
            schema = schema,
            updatedAt = obj.optLong("updatedAt", 0L),
            days = obj.optJSONArray("days")?.let { arr ->
                (0 until arr.length()).mapNotNull { runCatching { dayFromJson(arr.getJSONObject(it)) }.getOrNull() }
            } ?: emptyList(),
            plans = obj.optJSONArray("plans")?.let { arr ->
                (0 until arr.length()).mapNotNull { runCatching { planFromJson(arr.getJSONObject(it)) }.getOrNull() }
            } ?: emptyList(),
            settings = obj.optJSONObject("settings")?.let { LiveSettingsDto(
                leadInSeconds = it.optInt("leadIn", 10).coerceIn(0, 60),
                intervalSeconds = it.optInt("interval", 90).coerceIn(10, 900),
                sound = it.optBoolean("sound", true),
                vibration = it.optBoolean("vibration", true),
            ) } ?: LiveSettingsDto(),
        )
    }

    private fun LiveSettingsDto.toJson(): JSONObject = JSONObject().apply {
        put("leadIn", leadInSeconds)
        put("interval", intervalSeconds)
        put("sound", sound)
        put("vibration", vibration)
    }

    private fun JournalDayDto.toJson(): JSONObject = JSONObject().apply {
        put("date", date)
        put("items", JSONArray(items.map { it.toJson() }))
    }

    private fun JournalItemDto.toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        put("date", date)
        put("kind", kind)
        put("title", title)
        put("summary", summary)
        if (body.isNotBlank()) put("body", body)
        if (typeKey.isNotBlank()) put("typeKey", typeKey)
        if (typeLabel.isNotBlank()) put("typeLabel", typeLabel)
        put("startMs", startMs)
        put("endMs", endMs)
        if (stats.isNotEmpty()) put("stats", JSONArray(stats.map { it.toJson() }))
        if (note.isNotBlank()) put("note", note)
        if (scheduled) put("scheduled", true)
        if (planned) put("planned", true)
        if (hasPlan) put("hasPlan", true)
        if (planSummary.isNotBlank()) put("planSummary", planSummary)
        if (planTotalSets > 0) put("planTotalSets", planTotalSets)
        if (planLoggedSets > 0) put("planLoggedSets", planLoggedSets)
        if (hasWatchData) put("hasWatchData", true)
        if (content.isNotBlank()) put("content", content)
    }

    private fun StatDto.toJson(): JSONObject = JSONObject().apply {
        put("label", label)
        put("value", value)
    }

    private fun PlanDto.toJson(): JSONObject = JSONObject().apply {
        put("date", date)
        put("typeKey", typeKey)
        put("plan", planJson)
        put("results", resultsJson)
        put("model", model)
        put("updatedAt", updatedAt)
    }

    private fun dayFromJson(obj: JSONObject): JournalDayDto = JournalDayDto(
        date = obj.getString("date"),
        items = obj.optJSONArray("items")?.let { arr ->
            (0 until arr.length()).mapNotNull { runCatching { itemFromJson(arr.getJSONObject(it)) }.getOrNull() }
        } ?: emptyList(),
    )

    private fun itemFromJson(obj: JSONObject): JournalItemDto = JournalItemDto(
        key = obj.getString("key"),
        date = obj.optString("date", ""),
        kind = obj.optString("kind", "workout"),
        title = obj.optString("title", ""),
        summary = obj.optString("summary", ""),
        body = obj.optString("body", ""),
        typeKey = obj.optString("typeKey", ""),
        typeLabel = obj.optString("typeLabel", ""),
        startMs = obj.optLong("startMs", 0L),
        endMs = obj.optLong("endMs", 0L),
        stats = obj.optJSONArray("stats")?.let { arr ->
            (0 until arr.length()).mapNotNull {
                runCatching {
                    val stat = arr.getJSONObject(it)
                    StatDto(stat.optString("label", ""), stat.optString("value", ""))
                }.getOrNull()
            }
        } ?: emptyList(),
        note = obj.optString("note", ""),
        scheduled = obj.optBoolean("scheduled", false),
        planned = obj.optBoolean("planned", false),
        hasPlan = obj.optBoolean("hasPlan", false),
        planSummary = obj.optString("planSummary", ""),
        planTotalSets = obj.optInt("planTotalSets", 0),
        planLoggedSets = obj.optInt("planLoggedSets", 0),
        hasWatchData = obj.optBoolean("hasWatchData", false),
        content = obj.optString("content", ""),
    )

    private fun planFromJson(obj: JSONObject): PlanDto = PlanDto(
        date = obj.getString("date"),
        typeKey = obj.getString("typeKey"),
        planJson = obj.optString("plan", ""),
        resultsJson = obj.optString("results", "{}"),
        model = obj.optString("model", ""),
        updatedAt = obj.optLong("updatedAt", 0L),
    )

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}
