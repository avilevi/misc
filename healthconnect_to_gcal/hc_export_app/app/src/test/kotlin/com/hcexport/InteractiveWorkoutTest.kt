package com.hcexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveWorkoutTest {

    private val sample = """
        {
          "title": "Heavy Metcon",
          "summary": "28:00 EMOM with a heavy press",
          "sections": [
            {
              "title": "Heavy Metcon",
              "kind": "metcon",
              "notes": "28:00 EMOM",
              "metrics": [
                {"key": "total_time", "label": "Total time", "kind": "time", "unit": ""}
              ],
              "exercises": [
                {
                  "name": "Push Press",
                  "notes": "Min 1",
                  "metrics": [
                    {"key": "weight", "label": "Weight", "kind": "weight", "unit": "kg"},
                    {"key": "reps", "label": "Reps", "kind": "reps", "unit": ""}
                  ],
                  "sets": [
                    {"label": "Min 1", "target": "3 reps"},
                    {"label": "Min 2", "target": "3 reps"}
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses sections exercises sets and metrics`() {
        val plan = InteractivePlan.fromJson(sample)
        assertNotNull(plan)
        assertEquals("Heavy Metcon", plan!!.title)
        assertEquals(1, plan.sections.size)
        val section = plan.sections.first()
        assertEquals("metcon", section.kind)
        assertEquals("Total time", section.metrics.first().label)
        assertEquals(MetricKind.TIME, section.metrics.first().kind)
        val exercise = section.exercises.first()
        assertEquals("Push Press", exercise.name)
        assertEquals(2, exercise.sets.size)
        assertEquals("Min 1", exercise.sets.first().label)
        assertEquals("3 reps", exercise.sets.first().target)
        assertEquals(MetricKind.WEIGHT, exercise.metrics[0].kind)
        assertEquals("kg", exercise.metrics[0].unit)
    }

    @Test
    fun `an exercise without metrics gets a text result metric`() {
        val plan = InteractivePlan.fromJson(
            """
            {"title":"X","sections":[{"title":"S","kind":"other","exercises":[
              {"name":"Run","sets":[]}
            ]}]}
            """.trimIndent()
        )!!
        val exercise = plan.sections.first().exercises.first()
        assertEquals(1, exercise.metrics.size)
        assertEquals(MetricKind.TEXT, exercise.metrics.first().kind)
        assertEquals(1, exercise.sets.size)
        assertEquals("Result", exercise.sets.first().label)
    }

    @Test
    fun `duplicate metric keys are made unique`() {
        val plan = InteractivePlan.fromJson(
            """
            {"title":"X","sections":[{"title":"S","exercises":[{
              "name":"Squat",
              "metrics":[
                {"key":"weight","label":"Weight","kind":"weight"},
                {"key":"weight","label":"Back-off weight","kind":"weight"}
              ],
              "sets":[{"label":"Set 1","target":"5"}]
            }]}]}
            """.trimIndent()
        )!!
        val metrics = plan.sections.first().exercises.first().metrics
        assertEquals(listOf("weight", "weight_2"), metrics.map { it.key })
    }

    @Test
    fun `plan json round trips`() {
        val plan = InteractivePlan.fromJson(sample)!!
        val again = InteractivePlan.fromJson(plan.toJson().toString())!!
        assertEquals(plan, again)
    }

    @Test
    fun `metric aliases map to the right kinds`() {
        assertEquals(MetricKind.WEIGHT, MetricKind.fromKey("load"))
        assertEquals(MetricKind.TIME, MetricKind.fromKey("duration"))
        assertEquals(MetricKind.CALORIES, MetricKind.fromKey("cal"))
        assertEquals(MetricKind.REPS, MetricKind.fromKey("rep"))
        assertEquals(MetricKind.TEXT, MetricKind.fromKey("mystery"))
    }

    @Test
    fun `result keys and values round trip`() {
        val scope = InteractiveKeys.set(section = 0, exercise = 1, set = 2)
        val results = InteractiveResults.set(emptyMap(), scope, "weight", "102.5")
        assertEquals("102.5", InteractiveResults.value(results, scope, "weight"))
        val raw = InteractiveResults.toJson(results)
        val again = InteractiveResults.fromJson(raw)
        assertEquals("102.5", InteractiveResults.value(again, scope, "weight"))
    }

    @Test
    fun `json extraction accepts fences and surrounding prose`() {
        val fenced = "```json\n{\"a\":1}\n```"
        assertEquals("{\"a\":1}", AiClient.extractJsonObject(fenced))
        assertEquals("{\"a\":1}", AiClient.extractJsonObject("Here is the plan:\n{\"a\":1}\nThanks"))
        assertEquals(null, AiClient.extractJsonObject("no json here"))
    }
}
