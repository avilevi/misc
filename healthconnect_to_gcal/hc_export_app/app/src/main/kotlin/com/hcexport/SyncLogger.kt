package com.hcexport

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SyncLogger {

    private const val LOG_FILE = "sync_log.txt"
    private const val MAX_BYTES = 256 * 1024

    private val tsFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private fun file(ctx: Context): File = File(ctx.filesDir, LOG_FILE)

    fun log(ctx: Context, msg: String) {
        val line = "[${tsFmt.format(Date())}] $msg\n"
        val f = file(ctx)
        f.appendText(line, Charsets.UTF_8)
        trimIfNeeded(f)
    }

    fun read(ctx: Context): String {
        val f = file(ctx)
        return if (f.exists()) f.readText(Charsets.UTF_8) else "(no log yet)"
    }

    fun clear(ctx: Context) = file(ctx).delete()

    private fun trimIfNeeded(f: File) {
        if (f.length() <= MAX_BYTES) return
        val text = f.readText(Charsets.UTF_8)
        val trimmed = text.drop(text.length / 2)
        val firstNewline = trimmed.indexOf('\n')
        f.writeText(if (firstNewline >= 0) trimmed.drop(firstNewline + 1) else trimmed, Charsets.UTF_8)
    }
}
