package com.hcexport

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Free-form comments attached to a journal entry. Keyed by the stable identity
 * in [JournalKeys], so a workout's note follows the workout even when the watch
 * data is re-synced or the AI plan is regenerated.
 *
 * Notes are kept on the device with the rest of the journal; they are
 * deliberately excluded from cloud backup.
 */
object JournalNotesStore {

    private const val DB_NAME = "journal.db"
    private const val DB_VERSION = 1
    private const val TABLE = "journal_notes"

    @Volatile private var helper: Helper? = null

    private fun db(ctx: Context): SQLiteDatabase = synchronized(this) {
        val h = helper ?: Helper(ctx.applicationContext).also { helper = it }
        h.writableDatabase
    }

    fun get(ctx: Context, key: String): String =
        db(ctx).query(
            TABLE, arrayOf("note"), "entry_key = ?", arrayOf(key),
            null, null, null,
        ).use { c -> if (c.moveToNext()) c.getString(0).orEmpty() else "" }

    /** Every non-empty note, for the journal's "has a note" calendar dots. */
    fun getAll(ctx: Context): Map<String, String> {
        val out = mutableMapOf<String, String>()
        db(ctx).query(TABLE, arrayOf("entry_key", "note"), null, null, null, null, null)
            .use { c ->
                while (c.moveToNext()) {
                    val key = c.getString(0) ?: continue
                    val note = c.getString(1).orEmpty()
                    if (note.isNotBlank()) out[key] = note
                }
            }
        return out
    }

    /** Saves [note]; a blank note removes the row. */
    fun set(ctx: Context, key: String, note: String) {
        val trimmed = note.trim()
        if (trimmed.isEmpty()) {
            db(ctx).delete(TABLE, "entry_key = ?", arrayOf(key))
            WearPushWorker.enqueue(ctx, delayMs = 1_500L)
            return
        }
        val values = ContentValues().apply {
            put("entry_key", key)
            put("note", trimmed)
            put("updated_at", System.currentTimeMillis())
        }
        db(ctx).insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        WearPushWorker.enqueue(ctx, delayMs = 1_500L)
    }

    /** Remove the comment attached to [key]. */
    fun remove(ctx: Context, key: String) {
        db(ctx).delete(TABLE, "entry_key = ?", arrayOf(key))
    }

    fun count(ctx: Context): Int =
        db(ctx).rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE(
                  entry_key TEXT PRIMARY KEY,
                  note TEXT NOT NULL,
                  updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Version 1 is the first schema; future migrations go here.
        }
    }
}
