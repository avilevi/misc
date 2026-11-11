package com.hcexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutParserTest {

    private val html = """
        <html><body><main>
        <details class="accordion" name="all" open>
          <summary class="accordion-toggle">
            <div class="icon"></div>
            <div><div>2026-11-11</div><div>Wednesday</div></div>
          </summary>

          <details class="accordion" name="2026-11-11" open>
            <summary class="accordion-toggle">
              <div class="icon"></div>
              <div>🔥WOD</div>
            </summary>
            <p class="workout-summary">Strength:
5-5-3-3-3 Strict Press

Metcon:
10:00 EMOM
15 Wall-Balls (20/14)
</p>
          </details>

          <details class="accordion" name="2026-11-11">
            <summary class="accordion-toggle">
              <div class="icon"></div>
              <div>🫁 HYROX</div>
            </summary>
            <p class="workout-summary">500m Ski &amp; 200m Run
Less &lt; More &quot;quoted&quot;
</p>
          </details>

          <details class="accordion" name="2026-11-11">
            <summary class="accordion-toggle">
              <div class="icon"></div>
              <div>🔥WOD</div>
            </summary>
            <p class="workout-summary">duplicate should be ignored</p>
          </details>
        </details>

        <details class="accordion" name="2026-11-12">
          <summary class="accordion-toggle">
            <div class="icon"></div>
            <div>💪 Pump (Blend)</div>
          </summary>
          <p class="workout-summary">Set 1: 15 Reps
Set 2: 12 Reps</p>
        </details>

        <details class="accordion" name="not-a-date">
          <summary class="accordion-toggle">
            <div class="icon"></div>
            <div>Broken</div>
          </summary>
          <p class="workout-summary">ignored</p>
        </details>
        </main></body></html>
    """.trimIndent()

    @Test
    fun parsesEveryDateAndType() {
        val rows = WorkoutParser.parse(html)
        assertEquals(3, rows.size)
        assertEquals(listOf("2026-11-11", "2026-11-11", "2026-11-12"), rows.map { it.date })
        assertEquals(listOf("wod", "hyrox", "pump_blend"), rows.map { it.typeKey })
        assertEquals(listOf(0, 1, 0), rows.map { it.sortOrder })
    }

    @Test
    fun keepsTheFirstBlockWhenADateAndTypeRepeat() {
        val wod = WorkoutParser.parse(html).first { it.typeKey == "wod" }
        assertTrue(wod.content.contains("Strict Press"))
        assertTrue(!wod.content.contains("duplicate"))
    }

    @Test
    fun decodesHtmlEntitiesAndNormalisesBlankLines() {
        val hyrox = WorkoutParser.parse(html).first { it.typeKey == "hyrox" }
        assertTrue(hyrox.content.contains("500m Ski & 200m Run"))
        assertTrue(hyrox.content.contains("Less < More \"quoted\""))
        assertEquals(2, hyrox.content.lines().size)
    }

    @Test
    fun malformedPagesProduceNothing() {
        assertTrue(WorkoutParser.parse("<html>nope</html>").isEmpty())
    }

    @Test
    fun parsesTheRealPageFixture() {
        val real = javaClass.classLoader!!.getResource("hypr_sample.html")!!.readText()
        val rows = WorkoutParser.parse(real)
        assertTrue("expected at least 20 blocks, got ${rows.size}", rows.size >= 20)
        assertTrue(rows.any { it.typeKey == "wod" })
        assertTrue(rows.any { it.typeKey == "hyrox" })
        assertTrue(rows.any { it.typeKey == "pump" })
        assertTrue(rows.any { it.typeKey == "weightlifting" })
        assertEquals(rows.size, rows.distinctBy { it.date to it.typeKey }.size)
        assertTrue(rows.all { it.content.isNotBlank() })
        assertTrue(rows.none { it.date == "all" })
    }
}
