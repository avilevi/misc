package com.hcexport

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale

/** A generated plan plus the provider/model that produced it. */
data class GeneratedPlan(
    val plan: InteractivePlan,
    val displayName: String,
)

class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Calls whichever AI endpoint the user configured. Only the workout text,
 * date and type are sent; the journal, health records and the user's identity
 * never leave the device. API keys are read from [AiKeyStore] and are never
 * logged.
 */
object AiClient {

    private const val API_VERSION = "2023-06-01"
    private const val MAX_TOKENS = 8192
    private const val TEST_TOKENS = 1024
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 300_000

    private const val MAX_TOKENS_MESSAGE =
        "The plan was too long for this model. Try a shorter workout or another model."
    private const val REFUSAL_MESSAGE =
        "The AI provider declined to build this plan. Try another model or regenerate."

    private val SYSTEM_PROMPT = """
        You are a workout-structure parser for a personal training app. Convert the
        supplied CrossFit-style workout text into ONE strict JSON object. Return JSON
        only — no prose, no markdown fences, no comments.

        Schema:
        {
          "title": "short workout title",
          "summary": "one sentence describing the session",
          "sections": [
            {
              "title": "section with isolated movements",
              "kind": "strength | metcon | accessory | rest | other",
              "notes": [
                "time cap, rounds, stimulus or explanation",
                "one entry per line of the original text"
              ],
              "metrics": [
                {"key": "total_time", "label": "Total time", "kind": "time", "unit": ""}
              ],
              "exercises": [
                {
                  "name": "exercise name",
                  "notes": "optional cue",
                  "metrics": [
                    {"key": "weight", "label": "Weight", "kind": "weight", "unit": "kg"},
                    {"key": "reps", "label": "Reps", "kind": "reps", "unit": ""}
                  ],
                  "sets": [
                    {"label": "Set 1", "target": "5 reps, build to heavy", "metrics": []}
                  ]
                }
              ]
            },
            {
              "title": "section built from rounds",
              "kind": "metcon",
              "notes": [],
              "metrics": [
                {"key": "total_time", "label": "Total time", "kind": "time", "unit": ""}
              ],
              "rounds": [
                {
                  "label": "Round 1",
                  "notes": "",
                  "movements": [
                    {
                      "name": "Power Snatch",
                      "target": "15 reps",
                      "metrics": [
                        {"key": "weight", "label": "Weight", "kind": "weight", "unit": "kg"},
                        {"key": "reps", "label": "Reps", "kind": "reps", "unit": ""}
                      ]
                    },
                    {
                      "name": "Air Squats",
                      "target": "15 reps",
                      "metrics": [{"key": "reps", "label": "Reps", "kind": "reps", "unit": ""}]
                    },
                    {
                      "name": "Dual KB Front Rack Hold",
                      "target": "40s",
                      "metrics": [{"key": "done", "label": "Done", "kind": "check", "unit": ""}]
                    }
                  ]
                }
              ]
            }
          ]
        }

        Rules:
        1. Split the workout into logical sections (Strength, Metcon, Accessory,
           Stimulus/Notes). Keep the workout's own section titles. `kind` must be one
           of strength, metcon, accessory, rest, other.
        2. Rounds. When the text repeats SEVERAL movements together in rounds — e.g.
           "5 rounds of A + B + C", "5 rounds for time: 15 A, 15 B, 15 C", "3 sets:
           A + B", or "do B and C after each round" — put them in the section's
           `rounds` array, one entry per prescribed round, with the movements in the
           order they are performed. Do NOT also list those movements as separate
           exercises; a round groups them, it does not repeat them sequentially.
           When the reps change per round (e.g. "15-12-9-6-3 Power Snatch" plus
           squats and push-ups after each round), each round's movement carries its
           own target ("15 reps", "12 reps", ...).
        3. If a round-based section has no fixed round count (AMRAP until the cap),
           use a single round labelled "Each round"; record the score (rounds
           completed, total time) as a section metric.
        4. An EMOM whose movements rotate through a numbered list (e.g. "9:00 EMOM"
           followed by "1) A 2) B 3) C") is rounds too: one round per full cycle,
           containing those movements in order. A fixed work interval such as
           "40s Work / 20s Rest" is the same work in every cycle and needs no
           number recorded — use a single `check` metric on each movement.
        5. Use `exercises` only for movements performed on their own, one movement at
           a time (strength sets, a single-movement metcon). Never emit a section
           with the same movement in both `rounds` and `exercises`.
        6. Every distinct movement keeps the workout's exact name.
        7. For exercises, create one set per prescribed set, round or minute. Examples
           of labels: "Set 1", "Round 2", "Min 1". Put the prescription in `target`
           exactly as written, e.g. "5 reps", "40s", "15 cal".
        8. Metrics are only what the user can record. Allowed kinds: weight, reps,
           time, distance, calories, rounds, check, text. Use unit "kg" for weight,
           "m" for distance, "cal" for calories. Time uses kind "time" and an empty
           unit, and the user picks minutes and seconds.
        9. Use kind "check" (label "Done", empty unit) when completion is the only
           thing worth recording: fixed timed intervals and holds, mobility and
           drills. Never use check for strength, load or rep work — there the input
           is the weight or the reps actually done.
        10. Section-level `metrics` are only for a section scored as a whole: a
           For Time piece, an AMRAP's rounds, or a single benchmark score.
           Strength and accessory sections are never scored on the clock — their
           results live on each set — so they get NO section metrics at all.
        11. `notes` is a list of rows, not a paragraph. Keep every line of the
           original workout as its own entry, in the original order: a line that
           names the scheme ("Every 1:30 x 6 sets") and the line that follows it
           ("Build to a heavy single", "Newer Athletes: 5-5-3-3-3-3 reps") are
           separate entries. Never join them with a full stop.
        12. Never invent results. The user fills those in later.
        13. If a movement has no prescribed sets, give it one set labelled "Result".
        14. Explanatory text with no sets belongs in `notes`; use kind "other".
        15. If a movement is ambiguous, give it a text metric rather than guessing.
        16. Keep labels concise and preserve the original target text.
    """.trimIndent()

    suspend fun generatePlan(ctx: Context, workout: Workout): Result<GeneratedPlan> =
        withContext(Dispatchers.IO) {
            val config = AiConfigStore.getActive(ctx)
            val key = AiKeyStore.getApiKey(ctx, config.providerKey)
                ?: return@withContext Result.failure(
                    AiException("Add an API key for ${config.provider.label} in Settings → AI.")
                )
            try {
                val raw = callModel(
                    config = config,
                    apiKey = key,
                    system = SYSTEM_PROMPT,
                    user = userPrompt(workout),
                    maxTokens = MAX_TOKENS,
                    jsonMode = true,
                )
                val json = extractJsonObject(raw)
                    ?: return@withContext Result.failure(
                        AiException("The AI provider returned a plan that could not be read. Try regenerating.")
                    )
                val plan = InteractivePlan.fromJson(json)
                    ?: return@withContext Result.failure(
                        AiException("The AI plan had no usable sections. Try regenerating.")
                    )
                if (plan.isEmpty) {
                    return@withContext Result.failure(
                        AiException("The AI provider could not find sets in this workout. Try regenerating.")
                    )
                }
                Result.success(GeneratedPlan(plan, config.displayName))
            } catch (e: AiException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(
                    AiException("Could not generate the plan: ${e.message ?: "unknown error"}", e)
                )
            }
        }

    suspend fun testConnection(ctx: Context): Result<String> = withContext(Dispatchers.IO) {
        val config = AiConfigStore.getActive(ctx)
        val key = AiKeyStore.getApiKey(ctx, config.providerKey)
            ?: return@withContext Result.failure(
                AiException("Add an API key for ${config.provider.label} first.")
            )
        try {
            val reply = callModel(
                config = config,
                apiKey = key,
                system = "You are a connectivity test.",
                user = "Reply with exactly: OK",
                maxTokens = TEST_TOKENS,
                jsonMode = false,
            )
            if (reply.isBlank()) {
                Result.failure(AiException("The AI provider replied with an empty message."))
            } else {
                Result.success(config.displayName)
            }
        } catch (e: AiException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(AiException("Connection failed: ${e.message ?: "unknown error"}", e))
        }
    }

    internal fun extractJsonObject(text: String): String? {
        var value = text.trim()
        if (value.startsWith("```")) {
            value = value
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .trim()
            if (value.endsWith("```")) value = value.dropLast(3).trim()
        }
        val start = value.indexOf('{')
        val end = value.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return value.substring(start, end + 1)
    }

    private fun userPrompt(workout: Workout): String = buildString {
        append("Workout date: ").append(workout.date).append('\n')
        append("Workout type: ").append(WorkoutText.displayType(workout.typeLabel)).append('\n')
        append("Workout text:\n\"\"\"\n")
        append(workout.content)
        append("\n\"\"\"\n\n")
        append("Return the JSON plan.")
    }

    internal fun callModel(
        config: AiConfig,
        apiKey: String,
        system: String,
        user: String,
        maxTokens: Int,
        jsonMode: Boolean,
    ): String {
        if (config.endpoint.isBlank()) {
            throw AiException("Set an AI endpoint in Settings → AI.")
        }
        if (config.model.isBlank()) {
            throw AiException("Set a model in Settings → AI.")
        }
        return when (config.protocol) {
            AiProtocol.ANTHROPIC -> callAnthropic(config, apiKey, system, user, maxTokens)
            AiProtocol.OPENAI -> callOpenAi(config, apiKey, system, user, maxTokens, jsonMode)
            AiProtocol.GEMINI -> callGemini(config, apiKey, system, user, maxTokens, jsonMode)
        }
    }

    private fun callAnthropic(
        config: AiConfig,
        apiKey: String,
        system: String,
        user: String,
        maxTokens: Int,
    ): String {
        val endpoint = AiEndpoints.anthropicMessages(config.endpoint)
        val body = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", maxTokens)
            put("system", system)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", user)
                    }
                ),
            )
        }
        val reply = postJson(
            url = endpoint,
            headers = mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to API_VERSION,
            ),
            body = body.toString(),
        )
        if (reply.code !in 200..299) {
            throw AiException(httpErrorMessage(reply.code, reply.body))
        }
        return parseAnthropicReply(reply.body)
    }

    /**
     * OpenAI-compatible chat completions, used by OpenAI, DeepSeek, Kimi,
     * OpenRouter and most local/proxy servers. Retries the common schema
     * differences (max_completion_tokens, response_format, system role).
     */
    private fun callOpenAi(
        config: AiConfig,
        apiKey: String,
        system: String,
        user: String,
        initialMaxTokens: Int,
        jsonMode: Boolean,
    ): String {
        val endpoint = AiEndpoints.openAiChat(config.endpoint)
        var maxTokens = initialMaxTokens
        var tokenParam = "max_tokens"
        var useJsonMode = jsonMode
        var mergeSystem = false
        var lastReply: HttpReply? = null

        for (attempt in 0 until 4) {
            val body = JSONObject().apply {
                put("model", config.model)
                put(tokenParam, maxTokens)
                put(
                    "messages",
                    JSONArray().apply {
                        if (!mergeSystem) {
                            put(JSONObject().put("role", "system").put("content", system))
                        }
                        put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", if (mergeSystem) "$system\n\n$user" else user)
                            }
                        )
                    },
                )
                if (useJsonMode) {
                    put("response_format", JSONObject().put("type", "json_object"))
                }
            }
            val reply = postJson(
                url = endpoint,
                headers = mapOf("Authorization" to "Bearer $apiKey"),
                body = body.toString(),
            )
            if (reply.code in 200..299) return parseOpenAiReply(reply.body)
            lastReply = reply
            val message = extractErrorMessage(reply.body).orEmpty().lowercase(Locale.US)

            var changed = false
            if (tokenParam == "max_tokens" &&
                (message.contains("max_completion_tokens") ||
                    ((message.contains("max_tokens") || message.contains("max tokens")) &&
                        (message.contains("unsupported") ||
                            message.contains("not supported") ||
                            message.contains("unknown") ||
                            message.contains("invalid"))))
            ) {
                tokenParam = "max_completion_tokens"
                changed = true
            }
            if (useJsonMode &&
                (message.contains("response_format") ||
                    message.contains("response format") ||
                    message.contains("json mode") ||
                    message.contains("json_object")) &&
                (message.contains("unsupported") ||
                    message.contains("not supported") ||
                    message.contains("does not support") ||
                    message.contains("unknown") ||
                    message.contains("invalid"))
            ) {
                useJsonMode = false
                changed = true
            }
            if (!mergeSystem &&
                message.contains("system") &&
                (message.contains("role") ||
                    message.contains("unsupported") ||
                    message.contains("not supported") ||
                    message.contains("unknown"))
            ) {
                mergeSystem = true
                changed = true
            }
            if (maxTokens > 4096 &&
                (message.contains("max_tokens") || message.contains("max tokens")) &&
                (message.contains("too large") ||
                    message.contains("maximum") ||
                    message.contains("exceed") ||
                    message.contains("limit"))
            ) {
                maxTokens = 4096
                changed = true
            }
            if (!changed) break
        }

        val reply = lastReply ?: throw AiException("The AI provider returned no response.")
        throw AiException(httpErrorMessage(reply.code, reply.body))
    }

    private fun callGemini(
        config: AiConfig,
        apiKey: String,
        system: String,
        user: String,
        maxTokens: Int,
        jsonMode: Boolean,
    ): String {
        val endpoint = AiEndpoints.geminiGenerate(config.endpoint, config.model)
        var useJsonMime = jsonMode
        var mergeSystem = false
        var lastReply: HttpReply? = null

        for (attempt in 0 until 3) {
            val generation = JSONObject().apply {
                put("maxOutputTokens", maxTokens)
                if (useJsonMime) put("responseMimeType", "application/json")
            }
            val body = JSONObject().apply {
                if (!mergeSystem) {
                    put(
                        "systemInstruction",
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", system)),
                        ),
                    )
                }
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().put(
                                        "text",
                                        if (mergeSystem) "$system\n\n$user" else user,
                                    )
                                ),
                            )
                        }
                    ),
                )
                put("generationConfig", generation)
            }
            val reply = postJson(
                url = endpoint,
                headers = mapOf("x-goog-api-key" to apiKey),
                body = body.toString(),
            )
            if (reply.code in 200..299) return parseGeminiReply(reply.body)
            lastReply = reply
            val message = extractErrorMessage(reply.body).orEmpty().lowercase(Locale.US)

            var changed = false
            if (useJsonMime &&
                (message.contains("responsemimetype") ||
                    message.contains("response_mime_type") ||
                    message.contains("json")) &&
                (message.contains("unsupported") ||
                    message.contains("not supported") ||
                    message.contains("unknown") ||
                    message.contains("invalid"))
            ) {
                useJsonMime = false
                changed = true
            }
            if (!mergeSystem &&
                (message.contains("systeminstruction") || message.contains("system_instruction")) &&
                (message.contains("unsupported") ||
                    message.contains("not supported") ||
                    message.contains("unknown") ||
                    message.contains("invalid"))
            ) {
                mergeSystem = true
                changed = true
            }
            if (!changed) break
        }

        val reply = lastReply ?: throw AiException("The AI provider returned no response.")
        throw AiException(httpErrorMessage(reply.code, reply.body))
    }

    private data class HttpReply(val code: Int, val body: String)

    private fun postJson(
        url: String,
        headers: Map<String, String>,
        body: String,
    ): HttpReply {
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("content-type", "application/json")
                setRequestProperty("accept", "application/json")
                setRequestProperty("user-agent", "hc-sync-android/3.0")
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
        } catch (e: MalformedURLException) {
            throw AiException("The AI endpoint is not a valid URL. Check Settings → AI.", e)
        } catch (e: IOException) {
            throw AiException("Could not reach the AI provider. Check the endpoint and network.", e)
        }

        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpReply(code, text)
        } catch (e: SocketTimeoutException) {
            throw AiException("The AI provider took too long to respond. Try again.", e)
        } catch (e: IOException) {
            throw AiException("Could not reach the AI provider. Check the endpoint and network.", e)
        } finally {
            connection.disconnect()
        }
    }

    // ── Response parsing ───────────────────────────────────────────────────

    internal fun parseAnthropicReply(body: String): String {
        val json = parseObject(body)
        if (json.has("error") && json.optJSONArray("content") == null) {
            throw AiException(extractErrorMessage(body) ?: "The AI provider returned an error.")
        }
        when (json.optString("stop_reason")) {
            "refusal" -> throw AiException(REFUSAL_MESSAGE)
            "max_tokens" -> throw AiException(MAX_TOKENS_MESSAGE)
        }
        val content = json.optJSONArray("content")
            ?: throw AiException("The AI provider returned no text.")
        val text = buildString {
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }.trim()
        if (text.isBlank()) throw AiException("The AI provider returned an empty response.")
        return text
    }

    internal fun parseOpenAiReply(body: String): String {
        val json = parseObject(body)
        if (json.has("error") && json.optJSONArray("choices") == null) {
            throw AiException(extractErrorMessage(body) ?: "The AI provider returned an error.")
        }
        val choices = json.optJSONArray("choices")
            ?: throw AiException("The AI provider returned no choices.")
        val choice = choices.optJSONObject(0)
            ?: throw AiException("The AI provider returned an empty response.")
        when (choice.optString("finish_reason")) {
            "length" -> throw AiException(MAX_TOKENS_MESSAGE)
            "content_filter" -> throw AiException(REFUSAL_MESSAGE)
            "tool_calls", "function_call" ->
                throw AiException("The model tried to call a tool instead of returning a plan. Try another model.")
        }
        val message = choice.optJSONObject("message")
        val text = when (val content = message?.opt("content")) {
            is String -> content
            is JSONArray -> buildString {
                for (i in 0 until content.length()) {
                    when (val part = content.opt(i)) {
                        is String -> append(part)
                        is JSONObject -> append(part.optString("text"))
                    }
                }
            }
            is JSONObject -> content.optString("text")
            else -> ""
        }.trim()
        if (text.isNotBlank()) return text

        val legacy = choice.optString("text").trim()
        if (legacy.isNotBlank()) return legacy

        val reasoning = message?.optString("reasoning_content").orEmpty()
        if (reasoning.isNotBlank()) {
            throw AiException(
                "The model returned reasoning but no final answer. Try disabling reasoning or use another model."
            )
        }
        throw AiException("The AI provider returned an empty response.")
    }

    internal fun parseGeminiReply(body: String): String {
        val json = parseObject(body)
        if (json.has("error") && json.optJSONArray("candidates") == null) {
            throw AiException(extractErrorMessage(body) ?: "The AI provider returned an error.")
        }
        val blocked = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
        if (blocked.isNotBlank()) {
            throw AiException("The AI provider blocked this request ($blocked). Try another model.")
        }
        val candidates = json.optJSONArray("candidates")
            ?: throw AiException("The AI provider returned no response.")
        val candidate = candidates.optJSONObject(0)
            ?: throw AiException("The AI provider returned an empty response.")
        when (candidate.optString("finishReason").uppercase(Locale.US)) {
            "MAX_TOKENS" -> throw AiException(MAX_TOKENS_MESSAGE)
            "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII" ->
                throw AiException(REFUSAL_MESSAGE)
        }
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
            ?: throw AiException("The AI provider returned no text.")
        val text = buildString {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (part.optBoolean("thought", false)) continue
                append(part.optString("text"))
            }
        }.trim()
        if (text.isBlank()) throw AiException("The AI provider returned an empty response.")
        return text
    }

    private fun parseObject(body: String): JSONObject =
        runCatching { JSONObject(body) }.getOrElse {
            throw AiException("The AI provider returned an unreadable response. Try again.")
        }

    internal fun extractErrorMessage(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        val json = runCatching { JSONObject(trimmed) }.getOrNull()
        if (json != null) {
            json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?.let { return it }
            json.optString("message").takeIf { it.isNotBlank() }?.let { return it }
            json.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                ?.takeIf { it.isNotBlank() }?.let { return it }
            (json.opt("error") as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return trimmed.takeIf { it.length <= 300 && !it.contains("<") }
    }

    internal fun httpErrorMessage(code: Int, body: String): String {
        val providerMessage = extractErrorMessage(body)
        val lower = providerMessage.orEmpty().lowercase(Locale.US)
        return when {
            code == 401 || code == 403 ->
                "The AI provider rejected the API key. Check Settings → AI."
            code == 404 ->
                "The endpoint or model was not found. Check the endpoint and model in Settings → AI."
            code == 429 ->
                "The AI provider is rate-limiting requests. Try again in a moment."
            code in 500..599 ->
                "The AI provider is busy right now. Try again shortly."
            lower.contains("api key") || lower.contains("api_key") ->
                "The AI provider rejected the API key. Check Settings → AI."
            lower.contains("quota") || lower.contains("billing") ->
                "The AI provider quota is exhausted or billing is not set up."
            lower.contains("model") &&
                (lower.contains("not found") || lower.contains("does not exist") || lower.contains("invalid")) ->
                "The model was not found. Check the model in Settings → AI."
            else ->
                providerMessage?.let { "The AI provider could not process this workout: ${it.take(300)}" }
                    ?: "The AI provider returned HTTP $code."
        }
    }
}
