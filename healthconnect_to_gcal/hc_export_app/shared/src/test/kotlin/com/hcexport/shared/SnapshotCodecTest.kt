package com.hcexport.shared

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotCodecTest {

    private fun sample() = Snapshot(
        updatedAt = 1_760_000_000_000L,
        workouts = listOf(
            WorkoutDto("2026-11-11", "wod", "🔥WOD", "Strength:\n5×5", 0),
            WorkoutDto("2026-11-11", "hyrox", "🫁 HYROX", "500m Ski — שלום 🏋️", 1),
        ),
        journal = listOf(
            JournalDto(
                id = "e1", date = "2026-11-10", entryType = "exercise",
                title = "🏋️ Weightlifting – 55m", narrative = "Felt strong. משקל טוב",
                startMs = 1L, endMs = 2L, typeCode = 71, distanceM = 1200.0,
                caloriesKcal = 350.0, avgHrBpm = 121.5, maxHrBpm = 168.0, steps = 42L,
                source = "com.samsung.health", mergedWods = listOf(
                    MergedWodDto("2026-11-10", "WOD", "Deadlifts"),
                ),
                wodContent = null,
            ),
        ),
    )

    @Test
    fun roundTripPreservesEverything() {
        val original = sample()
        assertEquals(original, SnapshotCodec.decode(SnapshotCodec.encode(original)))
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
}
