package com.hcexport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepSessionsTest {

    private val base = 1_000_000_000_000L
    private val hour = 3_600_000L
    private val minute = 60_000L

    private fun sleep(id: String, start: Long, end: Long, source: String) =
        JournalEntry(
            id = id,
            date = "2026-11-18",
            entryType = "sleep",
            originalDataJson = JSONObject().apply {
                put("sm", start)
                put("em", end)
                put("sp", source)
            }.toString(),
            customDataJson = null,
            customNarrative = null,
            createdAtMs = 0L,
            updatedAtMs = 0L,
        )

    @Test
    fun `a listed source outranks an unlisted one`() {
        val samsung = sleep("s", base, base + 8 * hour, "com.sec.android.app.shealth")
        val fitbit = sleep("f", base, base + 8 * hour, "com.fitbit.FitbitMobile")
        val priority = listOf("com.fitbit.FitbitMobile", "com.sec.android.app.shealth")

        assertTrue(SleepSessions.outranks(fitbit, samsung, priority))
        assertFalse(SleepSessions.outranks(samsung, fitbit, priority))
    }

    @Test
    fun `the same source may correct its own night`() {
        val old = sleep("old", base, base + 8 * hour, "com.fitbit.FitbitMobile")
        val fixed = sleep("fixed", base + minute, base + 8 * hour, "com.fitbit.FitbitMobile")

        assertTrue(SleepSessions.outranks(fixed, old, emptyList()))
    }

    @Test
    fun `overlapping sessions form one night and separate ones do not`() {
        val first = sleep("a", base, base + 8 * hour, "x")
        val second = sleep("b", base + 3 * minute, base + 8 * hour, "y")
        val nap = sleep("nap", base + 10 * hour, base + 11 * hour, "x")

        val clusters = SleepSessions.clusters(listOf(first, second, nap))
        assertEquals(2, clusters.size)
        assertEquals(listOf("a", "b"), clusters[0].map { it.id })
        assertEquals(listOf("nap"), clusters[1].map { it.id })
    }

    @Test
    fun `the preferred recording follows the priority list`() {
        val samsung = sleep("s", base, base + 8 * hour, "com.sec.android.app.shealth")
        val fitbit = sleep("f", base + 3 * minute, base + 8 * hour, "com.fitbit.FitbitMobile")

        assertEquals(
            "f",
            SleepSessions.preferred(
                listOf(samsung, fitbit),
                listOf("com.fitbit.FitbitMobile", "com.sec.android.app.shealth"),
            ).id,
        )
        assertEquals("s", SleepSessions.preferred(listOf(samsung, fitbit), emptyList()).id)
    }
}
