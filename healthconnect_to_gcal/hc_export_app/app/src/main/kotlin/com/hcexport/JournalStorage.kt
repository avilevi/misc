package com.hcexport

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.util.Calendar

object JournalStorage {

    private const val PREFS_NAME = "journal_prefs"
    private const val KEY_ENTRIES = "entries"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Core read / write ────────────────────────────────────────────────────

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
        readAll(ctx).sortedByDescending { it.originalStartMs }

    fun getEntry(ctx: Context, id: String): JournalEntry? =
        readAll(ctx).find { it.id == id }

    fun getEntriesForDay(ctx: Context, dayStartMs: Long): List<JournalEntry> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = dayStartMs
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH)
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return readAll(ctx).filter {
            val ec = Calendar.getInstance().also { it.timeInMillis = it.originalStartMs }
            ec.get(Calendar.YEAR) == y && ec.get(Calendar.MONTH) == m && ec.get(Calendar.DAY_OF_MONTH) == d
        }.sortedBy { it.originalStartMs }
    }

    fun getEntriesForMonth(ctx: Context, year: Int, month: Int): List<JournalEntry> =
        readAll(ctx).filter {
            val c = Calendar.getInstance().also { c -> c.timeInMillis = it.originalStartMs }
            c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) == month
        }

    fun addEntryIfAbsent(ctx: Context, entry: JournalEntry): Boolean {
        val all = readAll(ctx).toMutableList()
        val exists = all.any { it.originalStartMs == entry.originalStartMs && it.originalSourcePkg == entry.originalSourcePkg }
        if (exists) return false
        all.add(entry)
        writeAll(ctx, all)
        return true
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

    fun buildDayMap(entries: List<JournalEntry>): Map<Int, List<JournalEntry>> {
        val map = mutableMapOf<Int, MutableList<JournalEntry>>()
        for (e in entries) {
            val c = Calendar.getInstance().also { it.timeInMillis = e.originalStartMs }
            val day = c.get(Calendar.DAY_OF_MONTH)
            map.getOrPut(day) { mutableListOf() }.add(e)
        }
        return map
    }
}
