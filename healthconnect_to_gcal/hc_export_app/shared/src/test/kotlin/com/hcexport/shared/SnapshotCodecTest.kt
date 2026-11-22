package com.hcexport.shared

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotCodecTest {

    private fun sample() = Snapshot(
        updatedAt = 1_760_000_000_000L,
        settings = LiveSettingsDto(
            leadInSeconds = 8, intervalSeconds = 120, sound = false, vibration = true,
        ),
        days = listOf(
            JournalDayDto(
                date = "2026-11-11",
                items = listOf(
                    JournalItemDto(
                        key = "w:2026-11-11:wod",
                        date = "2026-11-11",
                        kind = "workout",
                        title = "🔥WOD",
                        summary = "5 rounds for time",
                        body = "",
                        typeKey = "wod",
                        typeLabel = "🔥WOD",
                        startMs = 1L,
                        endMs = 2L,
                        stats = listOf(StatDto("DURATION", "55m")),
                        note = "Felt strong. משקל טוב",
                        scheduled = true,
                        planned = false,
                        hasPlan = true,
                        planSummary = "Snatch + squats",
                        planTotalSets = 11,
                        planLoggedSets = 4,
                        hasWatchData = true,
                        content = "Strength:\n5×5",
                    ),
                    JournalItemDto(
                        key = "e:e1", date = "2026-11-11", kind = "activity",
                        title = "Run", summary = "42m of run.", body = "42m of run.",
                        typeKey = "", typeLabel = "", startMs = 3L, endMs = 4L,
                        stats = listOf(StatDto("DISTANCE", "5.20")),
                        note = "", scheduled = false, planned = false, hasPlan = false,
                        planSummary = "", planTotalSets = 0, planLoggedSets = 0,
                        hasWatchData = false, content = "",
                    ),
                ),
            ),
        ),
        plans = listOf(
            PlanDto(
                date = "2026-11-11",
                typeKey = "wod",
                planJson = """{"title":"t","sections":[]}""",
                resultsJson = """{"e:0:0:0":{"reps":"5"}}""",
                model = "DeepSeek",
                updatedAt = 99L,
            ),
        ),
    )

    @Test
    fun roundTripPreservesEverything() {
        val original = sample()
        assertEquals(original, SnapshotCodec.decode(SnapshotCodec.encode(original)))
    }

    @Test
    fun hebrewAndEmojiSurvive() {
        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(sample()))
        assertEquals("Felt strong. משקל טוב", decoded.days.first().items.first().note)
        assertEquals("🔥WOD", decoded.days.first().items.first().title)
    }

    @Test
    fun chunksReassembleToTheExactBytes() {
        val bytes = SnapshotCodec.encode(sample())
        val chunks = SnapshotCodec.chunk(bytes, 100)
        assertTrue(chunks.size > 1)
        assertArrayEquals(bytes, SnapshotCodec.join(chunks))
    }

    @Test
    fun checksumDetectsCorruption() {
        val bytes = SnapshotCodec.encode(sample())
        val hash = SnapshotCodec.sha256Hex(bytes)
        val corrupted = bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        assertFalse(hash == SnapshotCodec.sha256Hex(corrupted))
    }

    @Test
    fun emptyChunkListStillEncodes() {
        assertArrayEquals(ByteArray(0), SnapshotCodec.join(SnapshotCodec.chunk(ByteArray(0))))
    }

    @Test
    fun liveResultRoundTripsAndRejectsJunk() {
        val result = LiveResultDto(
            "2026-11-11", "wod", """{"s:0":{"t":"4:12"}}""", 123L, planUpdatedAt = 99L,
        )
        assertEquals(result, LiveResultCodec.decode(LiveResultCodec.encode(result)))
        assertNull(LiveResultCodec.decode("not json"))
        assertNull(LiveResultCodec.decode("""{"date":"x","typeKey":"wod"}"""))
    }
}
