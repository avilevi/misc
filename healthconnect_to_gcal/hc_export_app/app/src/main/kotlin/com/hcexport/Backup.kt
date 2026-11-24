package com.hcexport

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manual backup and restore, with no server: one zip holding every database
 * and every settings file, written through the system file picker — so it can
 * land in Google Drive, on a computer, or anywhere else the phone can write.
 *
 * Secrets are deliberately left out. AI API keys and the Fitr and
 * BridgeAthletic logins are encrypted with an Android Keystore key that never
 * leaves this phone, so a restored copy could not read them anyway; they are
 * re-entered by hand after a restore.
 */
object Backup {

    private const val FORMAT = 1
    private const val MANIFEST = "manifest.json"
    private const val DB_DIR = "databases/"
    private const val PREFS_DIR = "prefs/"

    /** Settings files that hold Keystore-encrypted credentials — never copied. */
    private val SECRET_PREFS = setOf("hc_ai", "hc_fitr", "hc_bridge")

    /** What a backup file says about itself. */
    data class Info(
        val versionName: String,
        val createdAt: String,
        val databases: Int,
        val prefFiles: Int,
    )

    fun defaultFileName(): String =
        "hc-sync-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".zip"

    /** Reads just the manifest, to show what a chosen file holds before restoring. */
    suspend fun inspect(ctx: Context, uri: Uri): Info? = withContext(Dispatchers.IO) {
        val manifest = ctx.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == MANIFEST) return@use zip.readBytes()
                }
                null
            }
        }
        manifest?.let { parseManifest(it) }
    }

    /**
     * Writes every database and settings file into [uri]. Each database is
     * checkpointed first so its WAL is folded into the main file; the sidecars
     * are copied too in case the checkpoint could not complete.
     */
    suspend fun export(ctx: Context, uri: Uri): Info = withContext(Dispatchers.IO) {
        val databases = ctx.databaseList().toList().sorted()
        val prefs = prefsFiles(ctx)
        val created = timestamp()
        val tmp = File(ctx.cacheDir, "backup").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            ZipOutputStream(stream.buffered()).use { zip ->
                val manifest = JSONObject()
                    .put("format", FORMAT)
                    .put("app", ctx.packageName)
                    .put("versionName", BuildConfig.VERSION_NAME)
                    .put("versionCode", BuildConfig.VERSION_CODE)
                    .put("createdAt", created)
                    .put("databases", JSONArray(databases))
                    .put("prefs", JSONArray(prefs))
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                databases.forEach { name ->
                    // A VACUUM INTO copy is a transactionally consistent
                    // snapshot; the file copy is the fallback on older SQLite.
                    val snap = File(tmp, name)
                    if (snapshot(ctx, name, snap)) {
                        addFile(zip, snap, DB_DIR + name)
                    } else {
                        checkpoint(ctx, name)
                        val file = ctx.getDatabasePath(name)
                        if (!file.exists()) return@forEach
                        addFile(zip, file, DB_DIR + name)
                        listOf("-wal", "-shm", "-journal").forEach { suffix ->
                            val sidecar = File(file.path + suffix)
                            if (sidecar.exists()) addFile(zip, sidecar, DB_DIR + name + suffix)
                        }
                    }
                }

                prefs.forEach { name ->
                    val values = JSONObject()
                    shared(ctx, name).all.forEach { (key, value) -> values.put(key, encode(value)) }
                    zip.putNextEntry(ZipEntry(PREFS_DIR + name + ".json"))
                    zip.write(values.toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        } ?: error("Could not open the chosen file for writing")
        } finally {
            tmp.deleteRecursively()
        }
        Info(BuildConfig.VERSION_NAME, created, databases.size, prefs.size)
    }

    /**
     * Replaces this app's data and settings with the backup's. The database
     * connections are closed first, then the files are swapped; settings go
     * through the SharedPreferences API, so they are live immediately.
     */
    suspend fun restore(ctx: Context, uri: Uri): Info = withContext(Dispatchers.IO) {
        val info = inspect(ctx, uri) ?: error("That file is not an HC Sync backup")
        val tmp = File(ctx.cacheDir, "restore").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            ctx.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        if (entry.name == MANIFEST) continue
                        if (!entry.name.startsWith(DB_DIR) && !entry.name.startsWith(PREFS_DIR)) {
                            continue
                        }
                        val target = File(tmp, entry.name.substringAfterLast('/'))
                        target.outputStream().use { out -> zip.copyTo(out) }
                    }
                }
            } ?: error("Could not open the chosen file")

            val extracted = tmp.listFiles().orEmpty()
            val dbFiles = extracted.filter { !it.name.endsWith(".json") }
            val prefFiles = extracted.filter { it.name.endsWith(".json") }
            if (dbFiles.isEmpty()) error("The backup holds no databases")

            // Close the live connections, then swap the files underneath.
            WorkoutStore.close()
            RecordStore.close()
            InteractiveStore.close()
            JournalNotesStore.close()
            val dbDir = ctx.getDatabasePath(dbFiles.first().name).parentFile
            dbDir?.mkdirs()
            val bases = dbFiles
                .map {
                    it.name.substringBefore("-wal")
                        .substringBefore("-shm")
                        .substringBefore("-journal")
                }
                .toSet()
            bases.forEach { name ->
                val target = ctx.getDatabasePath(name)
                listOf("", "-wal", "-shm").forEach { suffix -> File(target.path + suffix).delete() }
            }
            dbFiles.forEach { file ->
                file.copyTo(File(dbDir, file.name), overwrite = true)
            }

            prefFiles.forEach { file ->
                val name = file.name.removeSuffix(".json")
                if (name in SECRET_PREFS) return@forEach
                val values = JSONObject(file.readText())
                val editor = shared(ctx, name).edit().clear()
                values.keys().forEach { key ->
                    put(editor, key, decode(values.getJSONObject(key)))
                }
                editor.commit()
            }
        } finally {
            tmp.deleteRecursively()
        }
        info
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private fun parseManifest(bytes: ByteArray): Info? = runCatching {
        val o = JSONObject(bytes.toString(Charsets.UTF_8))
        if (o.optInt("format") != FORMAT) return null
        Info(
            versionName = o.optString("versionName", "?"),
            createdAt = o.optString("createdAt", ""),
            databases = o.optJSONArray("databases")?.length() ?: 0,
            prefFiles = o.optJSONArray("prefs")?.length() ?: 0,
        )
    }.getOrNull()

    private fun shared(ctx: Context, name: String): SharedPreferences =
        ctx.getSharedPreferences(name, Context.MODE_PRIVATE)

    /**
     * The app's own settings files, minus the credential stores. The prefix
     * filter keeps system files that share the directory — WebView's and the
     * framework's — out of the backup; a future `hc_` settings file still
     * comes along without a code change.
     */
    private fun prefsFiles(ctx: Context): List<String> =
        File(ctx.applicationInfo.dataDir, "shared_prefs")
            .listFiles { f -> f.isFile && f.name.endsWith(".xml") }
            ?.map { it.name.removeSuffix(".xml") }
            .orEmpty()
            .filter {
                (it.startsWith("hc_") || it == "journal_prefs") && it !in SECRET_PREFS
            }
            .sorted()

    /** A consistent copy of a database, when the SQLite version allows one. */
    private fun snapshot(ctx: Context, name: String, dest: File): Boolean = runCatching {
        val path = ctx.getDatabasePath(name)
        if (!path.exists()) return false
        dest.delete()
        SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("VACUUM INTO ?", arrayOf(dest.path))
        }
        dest.exists() && dest.length() > 0
    }.getOrDefault(false)

    /** Fold a database's WAL into its main file, so the copy is complete. */
    private fun checkpoint(ctx: Context, name: String) {
        runCatching {
            val path = ctx.getDatabasePath(name)
            if (!path.exists()) return
            SQLiteDatabase.openDatabase(
                path.path,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            ).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            }
        }
    }

    private fun addFile(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

    private fun encode(value: Any?): JSONObject = when (value) {
        is String -> JSONObject().put("t", "string").put("v", value)
        is Int -> JSONObject().put("t", "int").put("v", value)
        is Long -> JSONObject().put("t", "long").put("v", value)
        is Float -> JSONObject().put("t", "float").put("v", value.toDouble())
        is Boolean -> JSONObject().put("t", "boolean").put("v", value)
        is Set<*> -> JSONObject().put("t", "stringSet")
            .put("v", JSONArray(value.map { it.toString() }))
        else -> JSONObject().put("t", "string").put("v", value.toString())
    }

    private fun decode(json: JSONObject): Any? = runCatching {
        when (json.getString("t")) {
            "string" -> json.getString("v")
            "int" -> json.getInt("v")
            "long" -> json.getLong("v")
            "float" -> json.getDouble("v").toFloat()
            "boolean" -> json.getBoolean("v")
            "stringSet" -> {
                val array = json.getJSONArray("v")
                (0 until array.length()).map { array.getString(it) }.toSet()
            }
            else -> null
        }
    }.getOrNull()

    @Suppress("UNCHECKED_CAST")
    private fun put(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Set<*> -> editor.putStringSet(key, value as Set<String>)
        }
    }
}
