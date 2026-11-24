package com.hcexport

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val BASE_URL    = "https://downloads.nvlf.uk/hc-sync"
    private const val VERSION_URL = "$BASE_URL/version.json"

    /**
     * The APK URL carries the version code, so every build is a distinct URL
     * and a CDN cannot hand back the previous APK after a publish.
     */
    internal fun apkUrl(versionCode: Int): String = "$BASE_URL/app-release.apk?v=$versionCode"

    data class UpdateInfo(
        val remoteVersionCode: Int,
        val remoteVersionName: String,
        val buildDate: String,
    )

    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val json = fetchText(VERSION_URL) ?: return@withContext null
            val remoteCode = Regex(""""versionCode"\s*:\s*(\d+)""")
                .find(json)?.groupValues?.get(1)?.toIntOrNull() ?: return@withContext null
            if (remoteCode <= currentVersionCode) return@withContext null
            val remoteName = Regex(""""versionName"\s*:\s*"([^"]+)"""")
                .find(json)?.groupValues?.get(1) ?: "?"
            val buildDate = Regex(""""buildDate"\s*:\s*"([^"]+)"""")
                .find(json)?.groupValues?.get(1) ?: ""
            UpdateInfo(remoteCode, remoteName, buildDate)
        } catch (_: Exception) { null }
    }

    suspend fun downloadAndInstall(
        ctx: Context,
        versionCode: Int,
        onProgress: (Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val outDir  = File(ctx.cacheDir, "apk_download").apply { mkdirs() }
        val outFile = File(outDir, "hcsync-update.apk").apply { delete() }

        withConnection(apkUrl(versionCode)) { conn ->
            val total = conn.contentLengthLong
            var downloaded = 0L
            var lastReported = -1
            conn.inputStream.use { input ->
                outFile.outputStream().use { output ->
                    val buf = ByteArray(16_384)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) {
                            val pct = (downloaded * 100 / total).toInt()
                            if (pct != lastReported) {
                                lastReported = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
        }

        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", outFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    private fun fetchText(urlStr: String): String? =
        withConnection(urlStr) { it.inputStream.bufferedReader().readText() }

    private fun <T> withConnection(urlStr: String, block: (HttpURLConnection) -> T): T {
        val conn = openFollowingRedirects(urlStr)
        try { return block(conn) } finally { conn.disconnect() }
    }

    private fun openFollowingRedirects(urlStr: String): HttpURLConnection {
        var url = urlStr
        repeat(10) {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 10_000
            conn.readTimeout    = 30_000
            val code = conn.responseCode
            if (code in 300..399) {
                url = conn.getHeaderField("Location") ?: return conn
                conn.disconnect()
            } else {
                return conn
            }
        }
        return URL(url).openConnection() as HttpURLConnection
    }
}
