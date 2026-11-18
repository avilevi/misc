package com.hcexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Exercises the real HTTP clients against a local OpenAI/Anthropic/Gemini
 * compatible server. No external service is contacted.
 */
class AiClientHttpTest {

    private data class RecordedRequest(
        val path: String,
        val body: String,
        val authorization: String?,
        val apiKey: String?,
        val googleKey: String?,
        val anthropicVersion: String?,
    )

    /**
     * A deliberately tiny HTTP/1.1 server. com.sun.net.httpserver is not on
     * the Android unit-test compile classpath, so this uses a plain socket.
     */
    private class LocalServer(responses: List<Pair<Int, String>>) : AutoCloseable {
        val requests: MutableList<RecordedRequest> =
            Collections.synchronizedList(mutableListOf<RecordedRequest>())
        private val queue = ConcurrentLinkedQueue(responses)
        private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

        val port: Int
            get() = server.localPort

        private val thread = Thread {
            try {
                while (!server.isClosed) {
                    val socket = try {
                        server.accept()
                    } catch (_: Exception) {
                        break
                    }
                    socket.use { client ->
                        val input = client.getInputStream().bufferedReader()
                        val requestLine = input.readLine() ?: return@use
                        val headers = mutableMapOf<String, String>()
                        while (true) {
                            val line = input.readLine() ?: break
                            if (line.isEmpty()) break
                            val colon = line.indexOf(':')
                            if (colon > 0) {
                                headers[line.substring(0, colon).trim().lowercase()] =
                                    line.substring(colon + 1).trim()
                            }
                        }
                        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                        val body = if (contentLength > 0) {
                            val chars = CharArray(contentLength)
                            var read = 0
                            while (read < contentLength) {
                                val n = input.read(chars, read, contentLength - read)
                                if (n < 0) break
                                read += n
                            }
                            String(chars, 0, read)
                        } else {
                            ""
                        }
                        requests += RecordedRequest(
                            path = requestLine.split(" ").getOrNull(1)?.substringBefore("?") ?: "/",
                            body = body,
                            authorization = headers["authorization"],
                            apiKey = headers["x-api-key"],
                            googleKey = headers["x-goog-api-key"],
                            anthropicVersion = headers["anthropic-version"],
                        )
                        val response = queue.poll()
                            ?: (500 to """{"error":{"message":"no response queued"}}""")
                        val bytes = response.second.toByteArray(Charsets.UTF_8)
                        val out = client.getOutputStream()
                        out.write("HTTP/1.1 ${response.first} Response\r\n".toByteArray(Charsets.UTF_8))
                        out.write("Content-Type: application/json\r\n".toByteArray(Charsets.UTF_8))
                        out.write("Content-Length: ${bytes.size}\r\n".toByteArray(Charsets.UTF_8))
                        out.write("Connection: close\r\n\r\n".toByteArray(Charsets.UTF_8))
                        out.write(bytes)
                        out.flush()
                    }
                }
            } catch (_: Exception) {
                // Server stopped.
            }
        }.apply {
            isDaemon = true
            start()
        }

        override fun close() {
            server.close()
            thread.interrupt()
        }
    }

    private fun config(protocol: AiProtocol, endpoint: String, model: String = "test-model") =
        AiConfig("custom", protocol, endpoint, model)

    @Test
    fun `openai compatible request sends auth, model and json mode`() {
        LocalServer(
            listOf(
                200 to """{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}"""
            )
        ).use { server ->
            val text = AiClient.callModel(
                config = config(
                    AiProtocol.OPENAI,
                    "http://127.0.0.1:${server.port}/v1/chat/completions",
                ),
                apiKey = "sk-test",
                system = "sys",
                user = "user",
                maxTokens = 8192,
                jsonMode = true,
            )
            assertEquals("ok", text)
            val request = server.requests.single()
            assertEquals("/v1/chat/completions", request.path)
            assertEquals("Bearer sk-test", request.authorization)
            assertTrue(request.body.contains("\"model\":\"test-model\""))
            assertTrue(request.body.contains("\"max_tokens\":8192"))
            assertTrue(request.body.contains("\"response_format\":{\"type\":\"json_object\"}"))
            assertTrue(request.body.contains("\"role\":\"system\""))
            assertTrue(request.body.contains("\"role\":\"user\""))
        }
    }

    @Test
    fun `openai retries without response_format when unsupported`() {
        LocalServer(
            listOf(
                400 to """{"error":{"message":"response_format is not supported for this model"}}""",
                200 to """{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}""",
            )
        ).use { server ->
            val text = AiClient.callModel(
                config = config(
                    AiProtocol.OPENAI,
                    "http://127.0.0.1:${server.port}/v1/chat/completions",
                ),
                apiKey = "sk-test",
                system = "sys",
                user = "user",
                maxTokens = 8192,
                jsonMode = true,
            )
            assertEquals("ok", text)
            assertEquals(2, server.requests.size)
            assertTrue(server.requests[0].body.contains("response_format"))
            assertFalse(server.requests[1].body.contains("response_format"))
        }
    }

    @Test
    fun `openai retries with max_completion_tokens when required`() {
        LocalServer(
            listOf(
                400 to """{"error":{"message":"max_tokens is not supported; use max_completion_tokens"}}""",
                200 to """{"choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}""",
            )
        ).use { server ->
            val text = AiClient.callModel(
                config = config(
                    AiProtocol.OPENAI,
                    "http://127.0.0.1:${server.port}/v1/chat/completions",
                ),
                apiKey = "sk-test",
                system = "sys",
                user = "user",
                maxTokens = 8192,
                jsonMode = false,
            )
            assertEquals("ok", text)
            assertEquals(2, server.requests.size)
            assertTrue(server.requests[0].body.contains("\"max_tokens\""))
            assertTrue(server.requests[1].body.contains("\"max_completion_tokens\""))
        }
    }

    @Test
    fun `anthropic request sends its headers and parses text`() {
        LocalServer(
            listOf(
                200 to """{"content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn"}"""
            )
        ).use { server ->
            val text = AiClient.callModel(
                config = config(
                    AiProtocol.ANTHROPIC,
                    "http://127.0.0.1:${server.port}/v1/messages",
                ),
                apiKey = "sk-ant-test",
                system = "sys",
                user = "user",
                maxTokens = 8192,
                jsonMode = true,
            )
            assertEquals("ok", text)
            val request = server.requests.single()
            assertEquals("/v1/messages", request.path)
            assertEquals("sk-ant-test", request.apiKey)
            assertEquals("2023-06-01", request.anthropicVersion)
            assertTrue(request.body.contains("\"max_tokens\":8192"))
            assertTrue(request.body.contains("\"system\":\"sys\""))
        }
    }

    @Test
    fun `gemini request sends its key, derives the path and parses parts`() {
        LocalServer(
            listOf(
                200 to """{"candidates":[{"content":{"parts":[{"text":"ok"}]},"finishReason":"STOP"}]}"""
            )
        ).use { server ->
            val text = AiClient.callModel(
                config = config(
                    AiProtocol.GEMINI,
                    "http://127.0.0.1:${server.port}/v1beta",
                    "gemini-2.5-flash",
                ),
                apiKey = "google-test",
                system = "sys",
                user = "user",
                maxTokens = 8192,
                jsonMode = true,
            )
            assertEquals("ok", text)
            val request = server.requests.single()
            assertEquals(
                "/v1beta/models/gemini-2.5-flash:generateContent",
                request.path,
            )
            assertEquals("google-test", request.googleKey)
            assertTrue(request.body.contains("\"systemInstruction\""))
            assertTrue(request.body.contains("\"contents\""))
            assertTrue(request.body.contains("\"responseMimeType\":\"application/json\""))
        }
    }

    @Test
    fun `a 401 becomes an api key message`() {
        LocalServer(
            listOf(401 to """{"error":{"message":"invalid key"}}""")
        ).use { server ->
            val error = assertThrows(AiException::class.java) {
                AiClient.callModel(
                    config = config(
                        AiProtocol.OPENAI,
                        "http://127.0.0.1:${server.port}/v1/chat/completions",
                    ),
                    apiKey = "bad",
                    system = "sys",
                    user = "user",
                    maxTokens = 8192,
                    jsonMode = false,
                )
            }
            assertTrue(error.message!!.contains("API key"))
        }
    }
}
