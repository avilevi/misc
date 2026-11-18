package com.hcexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTextTest {

    private val sample = """
        Strength:
        Every 3:00 x 5 Sets
        5-5-3-3-3 Strict Press
        - Build to Heavy Set

        Metcon:
        10:00 EMOM
        Min 1: 15/12 Cal. Row/Bike

        A)
        500m Ski
        - keep the pace

        Stimulus:
        - unbroken wall-balls
    """.trimIndent()

    @Test
    fun `headers are recognised by colon and letter markers`() {
        val headers = WorkoutText.parse(sample).filterIsInstance<WorkoutBlock.Header>().map { it.text }
        assertEquals(listOf("Strength", "Metcon", "A", "Stimulus"), headers)
    }

    @Test
    fun `bullets are separated from body paragraphs`() {
        val blocks = WorkoutText.parse(sample)
        assertTrue(blocks.contains(WorkoutBlock.Bullet("Build to Heavy Set")))
        assertTrue(blocks.contains(WorkoutBlock.Bullet("keep the pace")))
        assertTrue(blocks.contains(WorkoutBlock.Bullet("unbroken wall-balls")))
        assertTrue(blocks.contains(WorkoutBlock.Body("Every 3:00 x 5 Sets\n5-5-3-3-3 Strict Press")))
    }

    @Test
    fun `summary skips a leading header`() {
        assertEquals("Every 3:00 x 5 Sets", WorkoutText.summary(sample))
    }

    @Test
    fun `summary drops a trailing colon from a body line`() {
        assertEquals(
            "Every 1:30 x 6 Sets",
            WorkoutText.summary("Every 1:30 x 6 Sets:\nMin 1: 3 Push Press"),
        )
    }

    @Test
    fun `display type strips leading symbols`() {
        assertEquals("WOD", WorkoutText.displayType("🔥WOD"))
        assertEquals("HYROX", WorkoutText.displayType("🫁 HYROX"))
        assertEquals("Barbell Strict Press", WorkoutText.displayType("Barbell Strict Press"))
    }

    @Test
    fun `blank content produces no blocks`() {
        assertTrue(WorkoutText.parse("   \n\n ").isEmpty())
        assertEquals("", WorkoutText.summary("   \n\n "))
    }
}
