package com.hcexport

import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutExportTest {

    private fun workout(
        date: String,
        type: String = "wod",
        label: String = "WOD",
        content: String = "Work",
        order: Int = 0,
    ) = Workout(
        date = date,
        typeKey = type,
        typeLabel = label,
        content = content,
        sortOrder = order,
        firstSeenMs = 1_700_000_000_000,
        lastSeenMs = 1_700_000_001_000,
    )

    @Test
    fun `csv has a bom, header and escaped fields`() {
        val csv = WorkoutExport.toCsv(
            listOf(
                workout(
                    date = "2026-01-02",
                    label = "WOD, \"Heavy\"",
                    content = "Line 1\nLine 2, \"x\"",
                )
            )
        )
        assertTrue(csv.startsWith("﻿date,type_key,type_label,content"))
        assertTrue(csv.contains("\"WOD, \"\"Heavy\"\"\""))
        assertTrue(csv.contains("\"Line 1\nLine 2, \"\"x\"\"\""))
        assertTrue(csv.contains("\"2023-11-14T22:13:20Z\""))
    }

    @Test
    fun `csv is chronological`() {
        val csv = WorkoutExport.toCsv(
            listOf(
                workout("2026-01-02"),
                workout("2025-12-31"),
                workout("2026-01-02", type = "hyrox", label = "HYROX", order = 1),
            )
        )
        val first = csv.indexOf("\"2025-12-31\"")
        val second = csv.indexOf("\"2026-01-02\"")
        val third = csv.lastIndexOf("\"2026-01-02\"")
        assertTrue(first in 0 until second)
        assertTrue(second < third)
    }

    @Test
    fun `empty archive is just the header`() {
        val csv = WorkoutExport.toCsv(emptyList())
        assertTrue(csv.startsWith("﻿date,type_key,type_label,content"))
        assertTrue(csv.endsWith("\r\n"))
        assertTrue(!csv.contains("2026"))
    }
}
