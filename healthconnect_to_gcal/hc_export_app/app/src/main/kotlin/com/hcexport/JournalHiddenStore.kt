package com.hcexport

import android.content.Context
import org.json.JSONArray

/**
 * Journal entries the athlete deleted. The journal is a merged view — a
 * workout reappears whenever a calendar event names it, and a watch session
 * whenever the sync sees it again — so removal is remembered here as well as
 * acted on in the underlying stores.
 *
 * Keys are the same stable identities [JournalItem] uses; they live on the
 * device with the journal, not in cloud backup.
 */
object JournalHiddenStore {

    private const val NAME = "hc_journal_hidden"
    private const val KEY = "keys"

    @Synchronized
    fun getAll(ctx: Context): Set<String> {
        val raw = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length())
                .map { array.optString(it) }
                .filter { it.isNotBlank() }
                .toSet()
        }.getOrDefault(emptySet())
    }

    @Synchronized
    fun hide(ctx: Context, key: String) {
        if (key.isBlank()) return
        write(ctx, getAll(ctx) + key)
    }

    /** Scheduling a workout again brings its entry back. */
    @Synchronized
    fun unhide(ctx: Context, key: String) {
        if (key.isBlank()) return
        write(ctx, getAll(ctx) - key)
    }

    private fun write(ctx: Context, keys: Set<String>) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, JSONArray(keys.toList()).toString())
            .apply()
    }
}
