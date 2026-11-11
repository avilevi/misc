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
        put("workouts", JSONArray(snapshot.workouts.map { it.toJson() }))
        put("journal", JSONArray(snapshot.journal.map { it.toJson() }))
    }

    fun fromJson(obj: JSONObject): Snapshot {
        val schema = obj.optInt("schema", SNAPSHOT_SCHEMA)
        return Snapshot(
            schema = schema,
            updatedAt = obj.optLong("updatedAt", 0L),
            workouts = obj.optJSONArray("workouts")?.let { arr ->
                (0 until arr.length()).mapNotNull { runCatching { workoutFromJson(arr.getJSONObject(it)) }.getOrNull() }
            } ?: emptyList(),
            journal = obj.optJSONArray("journal")?.let { arr ->
                (0 until arr.length()).mapNotNull { runCatching { journalFromJson(arr.getJSONObject(it)) }.getOrNull() }
            } ?: emptyList(),
        )
    }

    private fun WorkoutDto.toJson(): JSONObject = JSONObject().apply {
        put("date", date)
        put("typeKey", typeKey)
        put("typeLabel", typeLabel)
        put("content", content)
        put("sortOrder", sortOrder)
    }

    private fun JournalDto.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("date", date)
        put("entryType", entryType)
        put("title", title)
        put("narrative", narrative)
        put("startMs", startMs)
        put("endMs", endMs)
        put("typeCode", typeCode)
        if (distanceM != null) put("distanceM", distanceM)
        if (caloriesKcal != null) put("caloriesKcal", caloriesKcal)
        if (avgHrBpm != null) put("avgHrBpm", avgHrBpm)
        if (maxHrBpm != null) put("maxHrBpm", maxHrBpm)
        if (steps != null) put("steps", steps)
        put("source", source)
        put("mergedWods", JSONArray(mergedWods.map { it.toJson() }))
        if (wodContent != null) put("wodContent", wodContent)
    }

    private fun MergedWodDto.toJson(): JSONObject = JSONObject().apply {
        put("date", date)
        put("title", title)
        put("content", content)
    }

    private fun workoutFromJson(obj: JSONObject): WorkoutDto = WorkoutDto(
        date = obj.getString("date"),
        typeKey = obj.getString("typeKey"),
        typeLabel = obj.optString("typeLabel", obj.getString("typeKey")),
        content = obj.optString("content", ""),
        sortOrder = obj.optInt("sortOrder", 0),
    )

    private fun journalFromJson(obj: JSONObject): JournalDto = JournalDto(
        id = obj.getString("id"),
        date = obj.getString("date"),
        entryType = obj.optString("entryType", "exercise"),
        title = obj.optString("title", ""),
        narrative = obj.optString("narrative", ""),
        startMs = obj.optLong("startMs", 0L),
        endMs = obj.optLong("endMs", 0L),
        typeCode = obj.optInt("typeCode", -1),
        distanceM = if (obj.has("distanceM")) obj.getDouble("distanceM") else null,
        caloriesKcal = if (obj.has("caloriesKcal")) obj.getDouble("caloriesKcal") else null,
        avgHrBpm = if (obj.has("avgHrBpm")) obj.getDouble("avgHrBpm") else null,
        maxHrBpm = if (obj.has("maxHrBpm")) obj.getDouble("maxHrBpm") else null,
        steps = if (obj.has("steps")) obj.getLong("steps") else null,
        source = obj.optString("source", ""),
        mergedWods = obj.optJSONArray("mergedWods")?.let { arr ->
            (0 until arr.length()).mapNotNull {
                runCatching {
                    val m = arr.getJSONObject(it)
                    MergedWodDto(
                        date = m.optString("date", ""),
                        title = m.optString("title", ""),
                        content = m.optString("content", ""),
                    )
                }.getOrNull()
            }
        } ?: emptyList(),
        wodContent = if (obj.has("wodContent")) obj.getString("wodContent") else null,
    )

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}
