package com.hcexport

import org.junit.Assert.assertEquals
import org.junit.Test

class MetricHintTest {

    private val plan = """
        {
          "title": "Slow Snatch",
          "sections": [
            {
              "title": "Olympic Lifting",
              "kind": "strength",
              "exercises": [
                {
                  "name": "Snatch from Boxes Above Knees",
                  "metrics": [
                    {"key": "weight", "label": "Weight", "kind": "weight", "unit": "kg"},
                    {"key": "reps", "label": "Reps", "kind": "reps", "unit": ""}
                  ],
                  "sets": [
                    {"label": "Set 1", "target": "3 reps", "metrics": [
                      {"key": "weight", "label": "Weight", "kind": "weight", "unit": "kg", "hint": "70% of 1RM"},
                      {"key": "reps", "label": "Reps", "kind": "reps", "unit": ""}
                    ]},
                    {"label": "Set 2", "target": "3 reps", "metrics": [
                      {"key": "weight", "label": "Weight", "kind": "weight", "unit": "kg", "hint": "75% of 1RM"},
                      {"key": "reps", "label": "Reps", "kind": "reps", "unit": ""}
                    ]}
                  ]
                },
                {
                  "name": "Kettlebell Figure 8th",
                  "metrics": [
                    {"key": "weight", "label": "Weight", "kind": "weight", "unit": "kg", "hint": "16–32 kg"},
                    {"key": "reps", "label": "Reps", "kind": "reps", "unit": ""}
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `weight hints are parsed per set and per exercise`() {
        val parsed = InteractivePlan.fromJson(plan)!!
        val section = parsed.sections.single()
        val (snatch, kettlebell) = section.exercises

        assertEquals("70% of 1RM", snatch.sets[0].metrics.first { it.kind == MetricKind.WEIGHT }.hint)
        assertEquals("75% of 1RM", snatch.sets[1].metrics.first { it.kind == MetricKind.WEIGHT }.hint)
        assertEquals("16–32 kg", kettlebell.metrics.first { it.kind == MetricKind.WEIGHT }.hint)

        // The exercise-level metric carries no hint; the set's own metrics win.
        assertEquals("", snatch.metrics.first { it.kind == MetricKind.WEIGHT }.hint)
    }

    @Test
    fun `hints survive the stored plan round-trip`() {
        val parsed = InteractivePlan.fromJson(plan)!!
        val again = InteractivePlan.fromJson(parsed.toJson().toString())!!
        val snatch = again.sections.single().exercises.first()
        assertEquals("70% of 1RM", snatch.sets[0].metrics.first { it.kind == MetricKind.WEIGHT }.hint)
        assertEquals("75% of 1RM", snatch.sets[1].metrics.first { it.kind == MetricKind.WEIGHT }.hint)
    }

    @Test
    fun `metricHint prefers the prescribed guidance`() {
        val hinted = MetricDef("weight", "Weight", MetricKind.WEIGHT, "kg", hint = "70% of 1RM")
        assertEquals("70% of 1RM", metricHint(hinted))

        val plain = MetricDef("weight", "Weight", MetricKind.WEIGHT, "kg")
        assertEquals("kg", metricHint(plain))

        val reps = MetricDef("reps", "Reps", MetricKind.REPS, "", hint = "5–8 reps")
        assertEquals("5–8 reps", metricHint(reps))
    }

    @Test
    fun `a null hint is treated as absent`() {
        val parsed = InteractivePlan.fromJson(
            """
            {"title":"T","sections":[{"title":"S","exercises":[{"name":"E","metrics":[
              {"key":"weight","label":"Weight","kind":"weight","unit":"kg","hint":null}]}]}]}
            """.trimIndent(),
        )!!
        val metric = parsed.sections.single().exercises.single().metrics.single()
        assertEquals("", metric.hint)
        assertEquals("kg", metricHint(metric))
    }
}
