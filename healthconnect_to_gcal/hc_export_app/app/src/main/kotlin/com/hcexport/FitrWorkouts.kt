package com.hcexport

/** A movement demo attached to a Fitr section (YouTube or an uploaded video). */
data class FitrAttachment(
    val title: String,
    val kind: String,
    val videoId: String?,
    val url: String?,
    val thumbUrl: String?,
    val position: Int,
)

/** A section of one Fitr schedule day, as the client sees it. */
data class FitrSection(
    val id: Int,
    val title: String,
    val description: String,
    val position: Int,
    val attachments: List<FitrAttachment> = emptyList(),
)

/**
 * Turns Fitr sections into the same workout rows the website scraper produces.
 *
 * The section title carries the type ("🔥WOD", "🫁 HYROX", "💪 Pump") and the
 * description is HTML; the app stores the type key and the plain text. Pure
 * Kotlin on purpose, so it can be unit-tested on the JVM.
 */
object FitrWorkouts {

    private val STYLE = Regex(
        "<style.*?</style>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val BREAK = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val BLOCK_END = Regex(
        "</?(?:p|div|body|li|ul|ol|h[1-6])\\b[^>]*>",
        RegexOption.IGNORE_CASE,
    )
    private val TAG = Regex("<[^>]+>")
    private val SPACES = Regex("[ \\t]+")

    /** Fitr's section HTML -> the plain workout text the app stores. */
    fun htmlToText(raw: String): String {
        var text = STYLE.replace(raw, " ")
        text = BREAK.replace(text, "\n")
        text = BLOCK_END.replace(text, "\n")
        text = TAG.replace(text, "")
        text = WorkoutParser.unescape(text)
        val lines = text.replace("\r\n", "\n").replace('\r', '\n')
            .split('\n')
            .map { SPACES.replace(it, " ").trim() }

        val out = mutableListOf<String>()
        for (line in lines) {
            if (line.isEmpty()) {
                if (out.isNotEmpty() && out.last().isNotEmpty()) out += ""
            } else {
                out += line
            }
        }
        return out.joinToString("\n").trim()
    }

    /**
     * One day's sections -> workout rows. A type appears once per day (the first
     * section wins), and a section with no text is skipped.
     */
    fun rowsFor(date: String, sections: List<FitrSection>): List<Workout> {
        val byKey = LinkedHashMap<String, Workout>()
        for (section in sections.sortedBy { it.position }) {
            val label = section.title.trim()
            if (label.isEmpty()) continue
            val key = WorkoutTypes.keyFor(label)
            if (key in byKey) continue
            val content = htmlToText(section.description)
            if (content.isEmpty()) continue
            byKey[key] = Workout(
                date = date,
                typeKey = key,
                typeLabel = label,
                content = content,
                sortOrder = section.position,
            )
        }
        return byKey.values.toList()
    }

    /**
     * The demo videos of a day, by workout type. Duplicate videos (the same
     * movement demo attached more than once) keep the first occurrence.
     */
    fun videosFor(sections: List<FitrSection>): Map<String, List<WorkoutVideo>> {
        val out = LinkedHashMap<String, MutableList<WorkoutVideo>>()
        for (section in sections.sortedBy { it.position }) {
            val label = section.title.trim()
            if (label.isEmpty()) continue
            val key = WorkoutTypes.keyFor(label)
            val list = out.getOrPut(key) { mutableListOf() }
            for (attachment in section.attachments.sortedBy { it.position }) {
                val videoId = attachment.videoId?.trim()?.ifBlank { null }
                val url = attachment.url?.trim()?.ifBlank { null }
                if (videoId == null && url == null) continue
                if (videoId != null && list.any { it.videoId == videoId }) continue
                list += WorkoutVideo(
                    title = attachment.title.trim().ifBlank { "Movement demo" },
                    videoId = videoId,
                    url = url,
                    thumbUrl = attachment.thumbUrl?.trim()?.ifBlank { null },
                    kind = attachment.kind,
                    position = list.size,
                )
            }
        }
        return out.filterValues { it.isNotEmpty() }
    }
}
