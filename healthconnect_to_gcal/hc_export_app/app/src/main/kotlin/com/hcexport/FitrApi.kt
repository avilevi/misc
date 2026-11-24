package com.hcexport

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * Fitr's mobile REST API, as the official Fitr client app uses it.
 *
 * Auth is an OAuth2 password grant: a form POST carrying the Android app's own
 * client_id/client_secret plus the user's email and password. The response has an
 * access token with no published expiry; the stored password lets the app mint a
 * new one when the API answers 401. Every call sends `Authorization: <type> <token>`
 * and lives under https://app.fitr.training/mobile/api/v15/.
 */
object FitrApi {

    private const val BASE = "https://app.fitr.training/mobile/api/v15/"

    // The official Fitr Android client's embedded OAuth client.
    const val CLIENT_ID = "r4QOoCIXlpRsI0HSSsk4MNgYtH2e6AxZqkuASTGkli0"
    const val CLIENT_SECRET = "SBQDsewDnDtaFMFx9Xf995iMlnM750-0PFRzGIn2AJI"

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 40_000

    class ApiException(message: String, val code: Int = 0) : Exception(message)
    class AuthException(message: String) : Exception(message)

    data class Session(
        val auth: String,
        val userId: Int,
        val name: String?,
        val email: String?,
    )

    fun login(email: String, password: String): Session {
        val text = postForm(
            BASE + "users/sign_in",
            listOf(
                "client_id" to CLIENT_ID,
                "client_secret" to CLIENT_SECRET,
                "email" to email,
                "password" to password,
            ),
        )
        val root = JSONObject(text)
        val token = root.optString("access_token")
        val type = root.optString("token_type").ifBlank { "Bearer" }
        if (token.isBlank()) throw AuthException("Fitr did not return a token")
        val current = root.optJSONObject("current") ?: JSONObject()
        return Session(
            auth = "$type $token",
            userId = current.optInt("id"),
            name = current.optString("full_name").trim().ifBlank { null },
            email = current.optString("email").trim().ifBlank { null },
        )
    }

    fun me(auth: String): JSONObject =
        JSONObject(getText(BASE + "users/me", auth))

    fun schedules(auth: String, from: String, to: String): String =
        getText(BASE + "schedules?from=$from&to=$to", auth)

    fun dayDetails(auth: String, date: String, planId: Int, trackId: Int): String {
        val track = if (trackId > 0) "?plan_track_id=$trackId" else ""
        return getText(BASE + "athlete/schedules/$date/plans/$planId/details$track", auth)
    }

    // ── HTTP ───────────────────────────────────────────────────────────────

    private fun postForm(url: String, fields: List<Pair<String, String>>): String {
        val body = fields.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }.toByteArray(Charsets.UTF_8)
        val conn = open(url, auth = null)
        return try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.outputStream.use { it.write(body) }
            read(conn)
        } finally {
            conn.disconnect()
        }
    }

    private fun getText(url: String, auth: String): String {
        val conn = open(url, auth)
        return try {
            read(conn)
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, auth: String?): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            auth?.let {
                setRequestProperty("Authorization", it)
                setRequestProperty("Accept", "application/json")
            }
            setRequestProperty("User-Agent", "hc-sync-android/2.0")
        }

    private fun read(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code in 200..299) return text

        val message = try {
            val errors = JSONObject(text).optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                val first = errors.optJSONObject(0)
                first?.optString("detail")?.ifBlank { null }
                    ?: first?.optString("title")?.ifBlank { null }
                    ?: "HTTP $code"
            } else {
                text.take(200).ifBlank { "HTTP $code" }
            }
        } catch (_: Exception) {
            text.take(200).ifBlank { "HTTP $code" }
        }
        if (code == 401) throw AuthException(message.ifBlank { "HTTP 401" })
        if (code == 200) throw IOException("HTTP 200 with unreadable body")
        throw ApiException(message.ifBlank { "HTTP $code" }, code)
    }
}
