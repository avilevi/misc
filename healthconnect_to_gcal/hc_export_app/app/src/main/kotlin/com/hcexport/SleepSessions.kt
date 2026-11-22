package com.hcexport

import org.json.JSONObject

/**
 * Sleep arrives from several sources, and the same night can be recorded by
 * more than one of them (a watch and a strap). Two sessions that overlap in
 * time are that night seen twice, so the journal shows a night once.
 *
 * The session from the highest-priority source wins — the priority list is the
 * one in Settings → Calendar & sync — and a comment written against any of the
 * recordings stays with the night.
 */
object SleepSessions {

    fun startOf(entry: JournalEntry): Long = json(entry)?.optLong("sm", 0L) ?: 0L

    fun endOf(entry: JournalEntry): Long = json(entry)?.optLong("em", 0L) ?: 0L

    fun sourceOf(entry: JournalEntry): String =
        json(entry)?.optString("sp", "").orEmpty()

    /** Lower ranks beat higher ones; a source that is not listed ranks last. */
    fun sourceRank(source: String, priority: List<String>): Int =
        priority.indexOf(source).let { if (it < 0) Int.MAX_VALUE else it }

    /** Sessions grouped by night: entries that overlap in time share a group. */
    fun clusters(entries: List<JournalEntry>): List<List<JournalEntry>> {
        val sorted = entries
            .filter { startOf(it) > 0 && endOf(it) > startOf(it) }
            .sortedBy { startOf(it) }
        val groups = mutableListOf<MutableList<JournalEntry>>()
        for (entry in sorted) {
            val last = groups.lastOrNull()
            if (last != null && startOf(entry) < last.maxOf { endOf(it) }) {
                last.add(entry)
            } else {
                groups.add(mutableListOf(entry))
            }
        }
        return groups
    }

    /** The recording a night is shown with: highest-priority source, then earliest. */
    fun preferred(cluster: List<JournalEntry>, priority: List<String>): JournalEntry =
        cluster.minWith(
            compareBy<JournalEntry> { sourceRank(sourceOf(it), priority) }
                .thenBy { startOf(it) }
                .thenByDescending { endOf(it) - startOf(it) }
                .thenBy { it.id }
        )

    /**
     * A session may replace a stored night when it comes from the same source
     * (a correction) or from one higher in the priority list.
     */
    fun outranks(incoming: JournalEntry, stored: JournalEntry, priority: List<String>): Boolean {
        val incomingSource = sourceOf(incoming)
        return incomingSource == sourceOf(stored) ||
            sourceRank(incomingSource, priority) < sourceRank(sourceOf(stored), priority)
    }

    /** The comment for a night, wherever among its recordings it was written. */
    fun note(
        cluster: List<JournalEntry>,
        chosen: JournalEntry,
        notes: Map<String, String>,
    ): String {
        fun noteOf(entry: JournalEntry): String? =
            notes[JournalKeys.sleep(entry.id)]
                ?: entry.customNarrative?.takeIf { it.isNotBlank() }
        return noteOf(chosen)
            ?: cluster.asSequence().sortedBy { startOf(it) }.mapNotNull { noteOf(it) }.firstOrNull()
            ?: ""
    }

    private fun json(entry: JournalEntry): JSONObject? =
        runCatching { JSONObject(entry.originalDataJson) }.getOrNull()
}
