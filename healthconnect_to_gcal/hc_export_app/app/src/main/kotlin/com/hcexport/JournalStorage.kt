package com.hcexport

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object JournalStorage {

    private const val PREFS_NAME = "journal_prefs"
    private const val KEY_ENTRIES = "entries"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun readAll(ctx: Context): List<JournalEntry> {
        val json = prefs(ctx).getString(KEY_ENTRIES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull {
                runCatching { JournalEntry.fromJson(arr.getJSONObject(it)) }.getOrNull()
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun writeAll(ctx: Context, entries: List<JournalEntry>) {
        prefs(ctx).edit()
            .putString(KEY_ENTRIES, JSONArray(entries.map { it.toJson() }).toString())
            .apply()
    }

    // ── Public API ───────────────────────────────────────────────────────────

    fun getEntries(ctx: Context): List<JournalEntry> =
        readAll(ctx).sortedByDescending { it.date }

    fun getEntry(ctx: Context, id: String): JournalEntry? =
        readAll(ctx).find { it.id == id }

    fun getEntriesForDay(ctx: Context, dayDate: String): List<JournalEntry> =
        readAll(ctx).filter { it.date == dayDate }
            .sortedBy { extractStartMs(it) }

    fun getEntriesForMonth(ctx: Context, year: Int, month: Int): List<JournalEntry> {
        val prefix = "%04d-%02d".format(year, month + 1)
        return readAll(ctx).filter { it.date.startsWith(prefix) }
    }

    fun addEntryIfAbsent(ctx: Context, entry: JournalEntry): Boolean {
        val all = readAll(ctx).toMutableList()
        val entryStartMs = extractStartMs(entry)
        val entrySrcPkg  = extractSourcePkg(entry)
        val idx = all.indexOfFirst {
            extractStartMs(it) == entryStartMs && extractSourcePkg(it) == entrySrcPkg
        }
        if (idx >= 0) return false
        all.add(entry)
        writeAll(ctx, all)
        return true
    }

    /** Insert or replace an entry matching by start time + source package.
     *  Preserves user customizations (customNarrative, customDataJson) from the
     *  existing entry when replacing. */
    fun upsertEntry(ctx: Context, entry: JournalEntry) {
        val all = readAll(ctx).toMutableList()
        val entryStartMs = extractStartMs(entry)
        val entrySrcPkg  = extractSourcePkg(entry)
        val idx = all.indexOfFirst {
            extractStartMs(it) == entryStartMs && extractSourcePkg(it) == entrySrcPkg
        }
        if (idx >= 0) {
            val existing = all[idx]
            all[idx] = entry.copy(
                id = existing.id,
                customDataJson = existing.customDataJson,
                customNarrative = existing.customNarrative,
            )
        } else {
            all.add(entry)
        }
        writeAll(ctx, all)
    }

    fun updateEntry(ctx: Context, entry: JournalEntry) {
        val all = readAll(ctx).toMutableList()
        val idx = all.indexOfFirst { it.id == entry.id }
        if (idx >= 0) {
            all[idx] = entry
            writeAll(ctx, all)
        }
    }

    fun deleteEntry(ctx: Context, id: String) {
        writeAll(ctx, readAll(ctx).filter { it.id != id })
    }

    /** Find WOD entries whose time range overlaps with [startMs, endMs]. */
    fun findOverlappingWod(ctx: Context, startMs: Long, endMs: Long): List<JournalEntry> =
        readAll(ctx).filter { entry ->
            entry.entryType == "wod" && try {
                val j = org.json.JSONObject(entry.originalDataJson)
                val ws = j.getLong("sm")
                val we = j.getLong("em")
                ws < endMs && we > startMs
            } catch (_: Exception) { false }
        }

    /** True if any non-WOD entry overlaps the given time range. */
    fun hasOverlappingEntry(ctx: Context, startMs: Long, endMs: Long): Boolean =
        readAll(ctx).any { entry ->
            entry.entryType != "wod" && try {
                val j = org.json.JSONObject(entry.originalDataJson)
                val es = j.getLong("sm")
                val ee = j.getLong("em")
                es < endMs && ee > startMs
            } catch (_: Exception) { false }
        }

    fun buildDayMap(entries: List<JournalEntry>): Map<Int, List<JournalEntry>> {
        val map = mutableMapOf<Int, MutableList<JournalEntry>>()
        for (e in entries) {
            val day = e.date.substringAfterLast('-').toIntOrNull() ?: continue
            map.getOrPut(day) { mutableListOf() }.add(e)
        }
        return map
    }

    // ── Display filtering ────────────────────────────────────────────────────

    /** Return entries for a day, excluding WOD entries that overlap with non-WOD entries. */
    fun getVisibleEntriesForDay(ctx: Context, dayDate: String): List<JournalEntry> =
        filterCoveredWods(getEntriesForDay(ctx, dayDate))

    /** Return entries for a month, excluding WOD entries that overlap with non-WOD entries. */
    fun getVisibleEntriesForMonth(ctx: Context, year: Int, month: Int): List<JournalEntry> =
        filterCoveredWods(getEntriesForMonth(ctx, year, month))

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun extractStartMs(entry: JournalEntry): Long =
        try { JSONObject(entry.originalDataJson).getLong("sm") }
        catch (_: Exception) { 0L }

    private fun extractSourcePkg(entry: JournalEntry): String =
        try { JSONObject(entry.originalDataJson).optString("sp", "") }
        catch (_: Exception) { "" }

    private fun extractEndMs(entry: JournalEntry): Long =
        try { JSONObject(entry.originalDataJson).getLong("em") }
        catch (_: Exception) { 0L }

    /** True if entry's time range overlaps [startMs, endMs]. */
    private fun overlapsEntry(entry: JournalEntry, startMs: Long, endMs: Long): Boolean = try {
        val j = JSONObject(entry.originalDataJson)
        j.getLong("sm") < endMs && j.getLong("em") > startMs
    } catch (_: Exception) { false }

    /** Exclude WOD entries whose time range is covered by any non-WOD entry. */
    private fun filterCoveredWods(entries: List<JournalEntry>): List<JournalEntry> {
        val nonWod = entries.filter { it.entryType != "wod" }
        return entries.filter { entry ->
            if (entry.entryType != "wod") return@filter true
            val ws = extractStartMs(entry)
            val we = extractEndMs(entry)
            nonWod.none { overlapsEntry(it, ws, we) }
        }
    }
}
