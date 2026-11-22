package com.hcexport

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.Base64

/**
 * The BridgeAthletic REST API, as the official Android app uses it.
 *
 * Auth is `Authorization: JWT <jwt>`; the JWT comes from a form login carrying the
 * app's fixed Android API key. The JWT lives 30 days, and the stored password lets
 * the app obtain a new one silently. Every response is JSON text.
 */
object BridgeApi {

    private const val BASE = "https://www.bridgeathletic.com"
    private const val APP_KEY =
        "029dafae18587d98acb0e8b16c3f3191aecc6a95f7f42162a5ac98dcf16c03506809a8ee81ae76da9d79e37d0990c7b5"

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 120_000

    class ApiException(message: String, val code: Int = 0) : Exception(message)
    class AuthException(message: String) : Exception(message)

    data class Session(val jwt: String, val userId: Int, val expiresAtMs: Long)

    data class TeamRef(val id: Int, val organizationId: Int, val name: String)

    data class Profile(
        val userId: Int,
        val fullName: String?,
        val email: String?,
        val organizations: List<Int>,
        val teams: List<TeamRef>,
    )

    fun login(email: String, password: String): Session {
        val body = postForm(
            "$BASE/auth/local/getjwt",
            listOf("email" to email, "password" to password, "apikey" to APP_KEY),
        )
        val jwt = try {
            JSONObject(body).optString("jwt")
        } catch (_: Exception) {
            ""
        }
        if (jwt.isBlank()) throw AuthException("BridgeAthletic did not return a token")
        val claims = jwtClaims(jwt)
        return Session(
            jwt = jwt,
            userId = claims.optInt("id"),
            expiresAtMs = claims.optLong("exp") * 1000L,
        )
    }

    fun profile(jwt: String, userId: Int): Profile {
        val root = JSONObject(getText("$BASE/api/v1/users/$userId/profile", jwt))
        val user = root.optJSONObject("user") ?: JSONObject()
        val linked = root.optJSONObject("linked") ?: JSONObject()

        val organizations = linked.optJSONObject("organizations")?.keys()?.asSequence()
            ?.mapNotNull { it.toIntOrNull() }?.toList().orEmpty()
        val teams = linked.optJSONObject("teams")?.let { obj ->
            obj.keys().asSequence().mapNotNull { key ->
                val t = obj.optJSONObject(key) ?: return@mapNotNull null
                TeamRef(
                    id = t.optInt("id"),
                    organizationId = t.optInt("organizationId"),
                    name = t.optString("name").trim(),
                )
            }.toList()
        }.orEmpty()

        return Profile(
            userId = user.optInt("id", userId),
            fullName = user.optString("fullName").trim().ifBlank { null },
            email = user.optString("email").trim().ifBlank { null },
            organizations = organizations,
            teams = teams,
        )
    }

    /** Every workout assignment of the athlete in one team, roster included. */
    fun teamAssignments(jwt: String, organizationId: Int, teamId: Int, userId: Int): String =
        getText("$BASE/api/v1/organizations/$organizationId/teams/$teamId/users/$userId/assignments", jwt)

    /** The whole program: phases, workouts, blocks, prescribed sets, exercises. */
    fun program(jwt: String, organizationId: Int, programId: Int): String =
        getText("$BASE/api/v1/organizations/$organizationId/programs/$programId", jwt)

    // ── HTTP ───────────────────────────────────────────────────────────────

    private fun postForm(url: String, fields: List<Pair<String, String>>): String {
        val body = fields.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }.toByteArray(Charsets.UTF_8)
        val conn = open(url, jwt = null)
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

    private fun getText(url: String, jwt: String): String {
        val conn = open(url, jwt)
        return try {
            read(conn)
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, jwt: String?): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            jwt?.let {
                setRequestProperty("Authorization", "JWT $it")
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
            JSONObject(text).optString("message").ifBlank { text.take(200) }
        } catch (_: Exception) {
            text.take(200).ifBlank { "HTTP $code" }
        }
        if (code == 401) throw AuthException(message.ifBlank { "HTTP 401" })
        if (code == 200) throw IOException("HTTP 200 with unreadable body")
        throw ApiException(message.ifBlank { "HTTP $code" }, code)
    }

    // ── JWT ────────────────────────────────────────────────────────────────

    fun jwtClaims(jwt: String): JSONObject = try {
        val payload = jwt.split(".").getOrNull(1) ?: ""
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        JSONObject(String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8))
    } catch (_: Exception) {
        JSONObject()
    }
}
