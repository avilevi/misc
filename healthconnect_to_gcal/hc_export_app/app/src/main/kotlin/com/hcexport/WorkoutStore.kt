package com.hcexport

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local, append-only workout history. Rows are never deleted when the website
 * drops a date; first_seen is preserved across updates.
 */
object WorkoutStore {

    private const val DB_NAME = "workouts.db"
    private const val DB_VERSION = 1
    private const val TABLE = "workouts"

    @Volatile private var helper: Helper? = null

    private fun db(ctx: Context): SQLiteDatabase =
        synchronized(this) {
            val h = helper ?: Helper(ctx.applicationContext).also { helper = it }
            h.writableDatabase
        }

    data class UpsertResult(val created: Int, val updated: Int, val unchanged: Int)

    fun upsertAll(ctx: Context, workouts: List<Workout>, nowMs: Long = System.currentTimeMillis()): UpsertResult {
        if (workouts.isEmpty()) return UpsertResult(0, 0, 0)
        val db = db(ctx)
        var created = 0
        var updated = 0
        var unchanged = 0
        db.beginTransaction()
        try {
            for (w in workouts) {
                val existing = db.query(
                    TABLE, arrayOf("content", "type_label", "sort_order"),
                    "date = ? AND type_key = ?", arrayOf(w.date, w.typeKey),
                    null, null, null,
                ).use { c ->
                    if (c.moveToFirst()) {
                        Triple(c.getString(0), c.getString(1), c.getInt(2))
                    } else null
                }

                val values = ContentValues().apply {
                    put("date", w.date)
                    put("type_key", w.typeKey)
                    put("type_label", w.typeLabel)
                    put("content", w.content)
                    put("sort_order", w.sortOrder)
                    put("last_seen", nowMs)
                }
                if (existing != null) {
                    db.update(
                        TABLE, values, "date = ? AND type_key = ?",
                        arrayOf(w.date, w.typeKey),
                    )
                    if (existing.first == w.content &&
                        existing.second == w.typeLabel &&
                        existing.third == w.sortOrder
                    ) {
                        unchanged++
                    } else {
                        updated++
                    }
                } else {
                    values.put("first_seen", nowMs)
                    db.insertOrThrow(TABLE, null, values)
                    created++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return UpsertResult(created, updated, unchanged)
    }

    fun getAll(ctx: Context): List<Workout> {
        val out = mutableListOf<Workout>()
        db(ctx).query(
            TABLE, null, null, null, null, null,
            "date DESC, sort_order ASC, type_label ASC",
        ).use { c ->
            while (c.moveToNext()) out += read(c)
        }
        return out
    }

    fun getByDate(ctx: Context, date: String): List<Workout> {
        val out = mutableListOf<Workout>()
        db(ctx).query(
            TABLE, null, "date = ?", arrayOf(date), null, null,
            "sort_order ASC, type_label ASC",
        ).use { c ->
            while (c.moveToNext()) out += read(c)
        }
        return out
    }

    /** The workout that belongs in a calendar event titled "WOD". */
    fun getWod(ctx: Context, date: String): Workout? {
        getByDate(ctx, date).firstOrNull { it.typeKey == WorkoutTypes.WOD_KEY }?.let { return it }
        return getByDate(ctx, date).firstOrNull { "WOD" in it.typeLabel.uppercase() }
    }

    fun count(ctx: Context): Int =
        db(ctx).rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    fun latestDate(ctx: Context): String? =
        db(ctx).rawQuery("SELECT MAX(date) FROM $TABLE", null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }

    private fun read(c: android.database.Cursor) = Workout(
        date = c.getString(c.getColumnIndexOrThrow("date")),
        typeKey = c.getString(c.getColumnIndexOrThrow("type_key")),
        typeLabel = c.getString(c.getColumnIndexOrThrow("type_label")),
        content = c.getString(c.getColumnIndexOrThrow("content")),
        sortOrder = c.getInt(c.getColumnIndexOrThrow("sort_order")),
        firstSeenMs = c.getLong(c.getColumnIndexOrThrow("first_seen")),
        lastSeenMs = c.getLong(c.getColumnIndexOrThrow("last_seen")),
    )

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE(
                  _id INTEGER PRIMARY KEY AUTOINCREMENT,
                  date TEXT NOT NULL,
                  type_key TEXT NOT NULL,
                  type_label TEXT NOT NULL,
                  content TEXT NOT NULL,
                  sort_order INTEGER NOT NULL DEFAULT 0,
                  first_seen INTEGER NOT NULL,
                  last_seen INTEGER NOT NULL,
                  UNIQUE(date, type_key)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_workouts_date ON $TABLE(date)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Version 1 is the first schema; future migrations go here.
        }
    }
}
