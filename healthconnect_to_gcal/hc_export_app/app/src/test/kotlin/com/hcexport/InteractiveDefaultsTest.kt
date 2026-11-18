package com.hcexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveDefaultsTest {

    private fun metric(kind: MetricKind, unit: String = "") =
        MetricDef(key = kind.key, label = kind.label, kind = kind, unit = unit)

    @Test
    fun `reps default comes from the set's target`() {
        val reps = metric(MetricKind.REPS)
        assertEquals("5", InteractiveDefaults.forTarget("5 reps", reps))
        assertEquals("1", InteractiveDefaults.forTarget("1 rep", reps))
        assertEquals("15", InteractiveDefaults.forTarget("15 reps, each round", reps))
        assertEquals("5", InteractiveDefaults.forTarget("5 reps, build to heavy", reps))
        assertEquals("10", InteractiveDefaults.forTarget("10", reps))
        assertEquals("10", InteractiveDefaults.forTarget("10/10", reps))
        assertEquals("10", InteractiveDefaults.forTarget("10 each side", reps))
        assertEquals("10", InteractiveDefaults.forTarget("10 per leg", reps))
    }

    @Test
    fun `weight is never defaulted`() {
        val weight = metric(MetricKind.WEIGHT, "kg")
        assertNull(InteractiveDefaults.forTarget("60kg", weight))
        assertNull(InteractiveDefaults.forTarget("5 reps @ 60kg", weight))
        assertNull(InteractiveDefaults.forTarget("42.5/30kg", weight))
    }

    @Test
    fun `reps are not invented from another unit`() {
        val reps = metric(MetricKind.REPS)
        assertNull(InteractiveDefaults.forTarget("15 cal", reps))
        assertNull(InteractiveDefaults.forTarget("40s", reps))
        assertNull(InteractiveDefaults.forTarget("400m", reps))
    }

    @Test
    fun `time defaults understand seconds, minutes and mm-ss`() {
        val time = metric(MetricKind.TIME)
        assertEquals("0:40", InteractiveDefaults.forTarget("40s", time))
        assertEquals("0:30", InteractiveDefaults.forTarget("30 sec each side", time))
        assertEquals("1:30", InteractiveDefaults.forTarget("1:30", time))
        assertEquals("2:00", InteractiveDefaults.forTarget("2 min", time))
        assertEquals("1:30", InteractiveDefaults.forTarget("1.5 min", time))
    }

    @Test
    fun `calories and distance defaults are read from the target`() {
        assertEquals(
            "15",
            InteractiveDefaults.forTarget("15 cal", metric(MetricKind.CALORIES)),
        )
        assertEquals(
            "400",
            InteractiveDefaults.forTarget("400m", metric(MetricKind.DISTANCE, "m")),
        )
        assertEquals(
            "1500",
            InteractiveDefaults.forTarget("1.5 km", metric(MetricKind.DISTANCE, "m")),
        )
    }

    @Test
    fun `time value round trips and normalises`() {
        assertEquals(8 to 24, TimeValue.parse("8:24"))
        assertEquals(0 to 40, TimeValue.parse("0:40"))
        assertEquals(1 to 30, TimeValue.parse("90"))
        assertNull(TimeValue.parse(""))
        assertNull(TimeValue.parse("DNF"))
        assertEquals("8:24", TimeValue.format(8, 24))
        assertEquals("0:05", TimeValue.format(0, 5))
        assertEquals("8:24", TimeValue.normalise("8:24"))
        assertEquals("1:30", TimeValue.normalise("90"))
        assertEquals("DNF", TimeValue.normalise("DNF"))
    }

    @Test
    fun `results distinguish an untouched field from a cleared one`() {
        val scope = InteractiveKeys.set(0, 0, 0)
        val untouched = emptyMap<String, Map<String, String>>()
        assertFalse(InteractiveResults.contains(untouched, scope, "reps"))

        val cleared = InteractiveResults.set(untouched, scope, "reps", "")
        assertTrue(InteractiveResults.contains(cleared, scope, "reps"))
        assertEquals("", InteractiveResults.value(cleared, scope, "reps"))
    }
}
