package com.hcexport

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object WodSync {

    private const val TAG = "WodSync"
    private const val WORKOUTS_URL = "https://hypr-workouts.pages.dev/"
    private const val HANDLED_MARKER = "# populated by wod_sync"
    private const val DAYS_AHEAD = 7

    suspend fun sync(context: Context) = withContext(Dispatchers.IO) {
        val events = CalendarHelper.findWodEvents(context, DAYS_AHEAD)
        if (events.isEmpty()) {
            Log.i(TAG, "No WOD events found in next $DAYS_AHEAD days")
            return@withContext
        }

        Log.i(TAG, "Found ${events.size} WOD event(s), fetching workouts page")
        val pageHtml = fetchPage(WORKOUTS_URL)

        var updated = 0
        for (event in events) {
            if (HANDLED_MARKER in (event.description ?: "")) {
                Log.d(TAG, "  ${event.dateStr}: already populated, skipping")
                continue
            }
            val wod = extractWodForDate(pageHtml, event.dateStr)
            if (wod == null) {
                Log.i(TAG, "  ${event.dateStr}: no WOD found on website")
                continue
            }
            CalendarHelper.updateEventDescription(context, event.id, "$wod\n\n$HANDLED_MARKER")
            Log.i(TAG, "  ${event.dateStr}: updated")
            updated++
        }
        Log.i(TAG, "WOD sync complete: $updated updated")
    }

    private fun fetchPage(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "wod-sync-android/1.0")
        conn.connectTimeout = 15_000
        conn.readTimeout    = 30_000
        return try {
            conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun extractWodForDate(html: String, dateStr: String): String? {
        val pattern = Regex(
            """<details[^>]+name="${Regex.escape(dateStr)}"[^>]*>.*?<summary[^>]*>.*?🔥WOD.*?</summary>.*?<p class="workout-summary">(.*?)</p>""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        val raw = pattern.find(html)?.groupValues?.get(1)?.trim() ?: return null
        return unescapeHtml(raw)
    }

    private fun unescapeHtml(text: String): String =
        text
            .replace("&amp;",  "&")
            .replace("&lt;",   "<")
            .replace("&gt;",   ">")
            .replace("&quot;", "\"")
            .replace("&#39;",  "'")
            .replace("&nbsp;", " ")
}
