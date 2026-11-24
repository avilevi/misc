package com.hcexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitrWorkoutsTest {

    @Test
    fun `html becomes the stored plain text`() {
        val html = """
            <style>body { font-family: 'Montserrat'; color: #404040; }</style>
            <body>Strength:<br />Every 3:00 x 5 Sets<br />Bench Press<br /><br />
            Metcon:<br />AMRAP 15:<br />200m Run<br />10 Toes-to-Bar<br /></body>
        """.trimIndent()

        assertEquals(
            "Strength:\nEvery 3:00 x 5 Sets\nBench Press\n\n" +
                "Metcon:\nAMRAP 15:\n200m Run\n10 Toes-to-Bar",
            FitrWorkouts.htmlToText(html),
        )
    }

    @Test
    fun `entities are unescaped and paragraph tags break lines`() {
        assertEquals(
            "A & B < 3\n\nquoted \"yes\"",
            FitrWorkouts.htmlToText("<p>A &amp; B &lt; 3</p><p>quoted &quot;yes&quot;</p>"),
        )
    }

    @Test
    fun `sections map to the same type keys as the page`() {
        val rows = FitrWorkouts.rowsFor(
            "2026-11-23",
            listOf(
                FitrSection(1, "🔥WOD", "<style>x{}</style><body>AMRAP 15</body>", 1),
                FitrSection(2, "🫁 HYROX", "<body>1:15 Work</body>", 2),
                FitrSection(3, "💪 Pump", "<body>5 Sets</body>", 3),
                FitrSection(4, "🏋🏻‍♀️ Weightlifting", "<body>Snatch</body>", 4),
            ),
        )

        assertEquals(listOf("wod", "hyrox", "pump", "weightlifting"), rows.map { it.typeKey })
        assertEquals(listOf(1, 2, 3, 4), rows.map { it.sortOrder })
        assertEquals("🔥WOD", rows.first().typeLabel)
        assertEquals("AMRAP 15", rows.first().content)
        assertEquals("2026-11-23", rows.first().date)
    }

    @Test
    fun `an empty section is skipped and a repeated type keeps the first`() {
        val rows = FitrWorkouts.rowsFor(
            "2026-11-20",
            listOf(
                FitrSection(1, "🔥WOD", "<body>First</body>", 1),
                FitrSection(2, "🔥 WOD (second)", "<body>Second</body>", 2),
                FitrSection(3, "💪 Pump", "<style>only styles</style>", 3),
            ),
        )

        assertEquals(listOf("wod"), rows.map { it.typeKey })
        assertEquals("First", rows.single().content)
    }

    @Test
    fun `pump blend keeps its own key`() {
        assertTrue(
            FitrWorkouts.rowsFor(
                "2026-11-20",
                listOf(FitrSection(1, "💪 Pump Blend", "<body>x</body>", 1)),
            ).single().typeKey == "pump_blend",
        )
    }

    @Test
    fun `videos are grouped by workout type and deduplicated`() {
        val videos = FitrWorkouts.videosFor(
            listOf(
                FitrSection(
                    1, "🔥WOD", "", 1,
                    listOf(
                        FitrAttachment(
                            "Thruster demo", "youtube", "abc",
                            "https://youtu.be/abc", "https://cdn/abc.jpg", 0,
                        ),
                        FitrAttachment(
                            "Thruster again", "youtube", "abc", null, null, 1,
                        ),
                    ),
                ),
                FitrSection(
                    2, "💪 Pump", "", 2,
                    listOf(
                        FitrAttachment(
                            "Push Up demo", "youtube", "xyz", null, null, 0,
                        ),
                        FitrAttachment(
                            "No media", "video", null, null, null, 1,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(setOf("wod", "pump"), videos.keys)
        assertEquals(listOf("Thruster demo"), videos.getValue("wod").map { it.title })
        assertEquals("abc", videos.getValue("wod").single().videoId)
        assertEquals(listOf("Push Up demo"), videos.getValue("pump").map { it.title })
        assertEquals(0, videos.getValue("pump").single().position)
    }
}
