package com.hcexport

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class InteractiveSessionRecord(
    val date: String,
    val typeKey: String,
    val planJson: String,
    val resultsJson: String,
    val model: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

/**
 * Local cache of AI-generated interactive plans and the values the user has
 * entered. Keyed by workout date + type; a regenerate replaces the plan and
 * clears the values.
 */
object InteractiveStore {

    private const val DB_NAME = "interactive.db"
    private const val DB_VERSION = 1
    private const val TABLE = "interactive_sessions"

    @Volatile private var helper: Helper? = null

    private fun db(ctx: Context): SQLiteDatabase = synchronized(this) {
        val h = helper ?: Helper(ctx.applicationContext).also { helper = it }
        h.writableDatabase
    }

    /** Release the connection so a restore can replace the file underneath it. */
    fun close() = synchronized(this) {
        helper?.close()
        helper = null
    }

    fun get(ctx: Context, date: String, typeKey: String): InteractiveSessionRecord? {
        db(ctx).query(
            TABLE, null, "date = ? AND type_key = ?", arrayOf(date, typeKey),
            null, null, null,
        ).use { c ->
            if (c.moveToNext()) {
                return InteractiveSessionRecord(
                    date = c.getString(c.getColumnIndexOrThrow("date")),
                    typeKey = c.getString(c.getColumnIndexOrThrow("type_key")),
                    planJson = c.getString(c.getColumnIndexOrThrow("plan_json")),
                    resultsJson = c.getString(c.getColumnIndexOrThrow("results_json")),
                    model = c.getString(c.getColumnIndexOrThrow("model")),
                    createdAtMs = c.getLong(c.getColumnIndexOrThrow("created_at")),
                    updatedAtMs = c.getLong(c.getColumnIndexOrThrow("updated_at")),
                )
            }
        }
        return null
    }

    /** Inclusive ISO-date range, for the journal's month and day views. */
    fun getBetween(ctx: Context, fromIso: String, toIso: String): List<InteractiveSessionRecord> {
        val out = mutableListOf<InteractiveSessionRecord>()
        db(ctx).query(
            TABLE, null, "date BETWEEN ? AND ?", arrayOf(fromIso, toIso),
            null, null, "date ASC",
        ).use { c ->
            while (c.moveToNext()) {
                out += InteractiveSessionRecord(
                    date = c.getString(c.getColumnIndexOrThrow("date")),
                    typeKey = c.getString(c.getColumnIndexOrThrow("type_key")),
                    planJson = c.getString(c.getColumnIndexOrThrow("plan_json")),
                    resultsJson = c.getString(c.getColumnIndexOrThrow("results_json")),
                    model = c.getString(c.getColumnIndexOrThrow("model")),
                    createdAtMs = c.getLong(c.getColumnIndexOrThrow("created_at")),
                    updatedAtMs = c.getLong(c.getColumnIndexOrThrow("updated_at")),
                )
            }
        }
        return out
    }

    fun savePlan(ctx: Context, date: String, typeKey: String, planJson: String, model: String) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("date", date)
            put("type_key", typeKey)
            put("plan_json", planJson)
            put("results_json", "{}")
            put("model", model)
            put("created_at", now)
            put("updated_at", now)
        }
        db(ctx).insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun saveResults(ctx: Context, date: String, typeKey: String, resultsJson: String) {
        val values = ContentValues().apply {
            put("results_json", resultsJson)
            put("updated_at", System.currentTimeMillis())
        }
        db(ctx).update(TABLE, values, "date = ? AND type_key = ?", arrayOf(date, typeKey))
    }

    fun count(ctx: Context): Int =
        db(ctx).rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    fun delete(ctx: Context, date: String, typeKey: String) {
        db(ctx).delete(TABLE, "date = ? AND type_key = ?", arrayOf(date, typeKey))
    }

    fun deleteAll(ctx: Context) {
        db(ctx).delete(TABLE, null, null)
    }

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE(
                  _id INTEGER PRIMARY KEY AUTOINCREMENT,
                  date TEXT NOT NULL,
                  type_key TEXT NOT NULL,
                  plan_json TEXT NOT NULL,
                  results_json TEXT NOT NULL DEFAULT '{}',
                  model TEXT NOT NULL DEFAULT '',
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL,
                  UNIQUE(date, type_key)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_interactive_date ON $TABLE(date)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Version 1 is the first schema; future migrations go here.
        }
    }
}
