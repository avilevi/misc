package com.hcexport

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** A month with at least one stored workout. Used by the archive index. */
data class WorkoutMonth(
    val year: Int,
    val month: Int,
    val workouts: Int,
    val days: Int,
    val firstDate: String,
    val lastDate: String,
)

/** Coverage of the whole append-only workout archive. */
data class ArchiveSummary(
    val workouts: Int,
    val days: Int,
    val firstDate: String?,
    val lastDate: String?,
)

/**
 * Local, append-only workout history. Rows are never deleted when the website
 * drops a date; first_seen is preserved across updates. This database is the
 * only permanent copy of workouts the site has rotated out, so no code may
 * prune it.
 */
object WorkoutStore {

    private const val DB_NAME = "workouts.db"
    private const val DB_VERSION = 2
    private const val TABLE = "workouts"
    private const val VIDEO_TABLE = "workout_videos"

    @Volatile private var helper: Helper? = null

    private fun db(ctx: Context): SQLiteDatabase =
        synchronized(this) {
            val h = helper ?: Helper(ctx.applicationContext).also { helper = it }
            h.writableDatabase
        }

    /** Release the connection so a restore can replace the file underneath it. */
    fun close() = synchronized(this) {
        helper?.close()
        helper = null
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

    // ── Movement demo videos (Fitr section attachments) ───────────────────

    /** Replace one workout's demo videos with what the latest pull returned. */
    fun replaceVideos(ctx: Context, date: String, typeKey: String, videos: List<WorkoutVideo>) {
        val db = db(ctx)
        db.beginTransaction()
        try {
            db.delete(VIDEO_TABLE, "date = ? AND type_key = ?", arrayOf(date, typeKey))
            videos.forEachIndexed { index, video ->
                db.insertOrThrow(VIDEO_TABLE, null, ContentValues().apply {
                    put("date", date)
                    put("type_key", typeKey)
                    put("position", index)
                    put("title", video.title)
                    put("video_id", video.videoId)
                    put("url", video.url)
                    put("thumb", video.thumbUrl)
                    put("kind", video.kind)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getVideos(ctx: Context, date: String, typeKey: String): List<WorkoutVideo> {
        val out = mutableListOf<WorkoutVideo>()
        db(ctx).query(
            VIDEO_TABLE, null, "date = ? AND type_key = ?", arrayOf(date, typeKey),
            null, null, "position ASC",
        ).use { c ->
            while (c.moveToNext()) {
                out += WorkoutVideo(
                    title = c.getString(c.getColumnIndexOrThrow("title")),
                    videoId = c.getString(c.getColumnIndexOrThrow("video_id")),
                    url = c.getString(c.getColumnIndexOrThrow("url")),
                    thumbUrl = c.getString(c.getColumnIndexOrThrow("thumb")),
                    kind = c.getString(c.getColumnIndexOrThrow("kind")),
                    position = c.getInt(c.getColumnIndexOrThrow("position")),
                )
            }
        }
        return out
    }

    fun getAll(ctx: Context): List<Workout> {        val out = mutableListOf<Workout>()
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

    /** Inclusive ISO-date range, oldest first. Used by the calendar views. */
    fun getBetween(ctx: Context, fromIso: String, toIso: String): List<Workout> {
        val out = mutableListOf<Workout>()
        db(ctx).query(
            TABLE, null, "date BETWEEN ? AND ?", arrayOf(fromIso, toIso), null, null,
            "date ASC, sort_order ASC, type_label ASC",
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

    fun countType(ctx: Context, typeKey: String): Int =
        db(ctx).rawQuery("SELECT COUNT(*) FROM $TABLE WHERE type_key = ?", arrayOf(typeKey)).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    fun latestDate(ctx: Context): String? =
        db(ctx).rawQuery("SELECT MAX(date) FROM $TABLE", null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }

    /**
     * One row per month that has stored workouts, newest first. This is the
     * archive index: it never loads workout content, so a decade of history is
     * cheap to browse.
     */
    fun getMonthCounts(ctx: Context): List<WorkoutMonth> {
        val out = mutableListOf<WorkoutMonth>()
        db(ctx).rawQuery(
            """
            SELECT CAST(substr(date,1,4) AS INTEGER) AS y,
                   CAST(substr(date,6,2) AS INTEGER) AS m,
                   COUNT(*) AS n,
                   COUNT(DISTINCT date) AS d,
                   MIN(date) AS first_date,
                   MAX(date) AS last_date
            FROM $TABLE
            GROUP BY y, m
            ORDER BY y DESC, m DESC
            """.trimIndent(),
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out += WorkoutMonth(
                    year = c.getInt(0),
                    month = c.getInt(1),
                    workouts = c.getInt(2),
                    days = c.getInt(3),
                    firstDate = c.getString(4),
                    lastDate = c.getString(5),
                )
            }
        }
        return out
    }

    /** Totals and coverage of the whole archive. */
    fun archiveSummary(ctx: Context): ArchiveSummary =
        db(ctx).rawQuery(
            "SELECT COUNT(*), COUNT(DISTINCT date), MIN(date), MAX(date) FROM $TABLE",
            null,
        ).use { c ->
            if (c.moveToFirst()) {
                ArchiveSummary(
                    workouts = c.getInt(0),
                    days = c.getInt(1),
                    firstDate = if (c.isNull(2)) null else c.getString(2),
                    lastDate = if (c.isNull(3)) null else c.getString(3),
                )
            } else {
                ArchiveSummary(0, 0, null, null)
            }
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
            createVideos(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) createVideos(db)
        }

        private fun createVideos(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $VIDEO_TABLE(
                  _id INTEGER PRIMARY KEY AUTOINCREMENT,
                  date TEXT NOT NULL,
                  type_key TEXT NOT NULL,
                  position INTEGER NOT NULL DEFAULT 0,
                  title TEXT NOT NULL,
                  video_id TEXT,
                  url TEXT,
                  thumb TEXT,
                  kind TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_workout_videos_key ON $VIDEO_TABLE(date, type_key)"
            )
        }
    }
}
