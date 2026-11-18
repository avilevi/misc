package com.hcexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiConfigTest {

    @Test
    fun `anthropic endpoints accept a base or a full url`() {
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            AiEndpoints.anthropicMessages("https://api.anthropic.com"),
        )
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            AiEndpoints.anthropicMessages("https://api.anthropic.com/v1"),
        )
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            AiEndpoints.anthropicMessages("https://api.anthropic.com/v1/messages"),
        )
    }

    @Test
    fun `openai endpoints accept the usual base forms`() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            AiEndpoints.openAiChat("https://api.openai.com"),
        )
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            AiEndpoints.openAiChat("https://api.openai.com/v1"),
        )
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            AiEndpoints.openAiChat("https://api.deepseek.com"),
        )
        assertEquals(
            "https://api.moonshot.ai/v1/chat/completions",
            AiEndpoints.openAiChat("https://api.moonshot.ai/v1"),
        )
        assertEquals(
            "https://example.test/v1/chat/completions",
            AiEndpoints.openAiChat("https://example.test/v1/chat/completions"),
        )
    }

    @Test
    fun `gemini endpoints append the model when missing`() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            AiEndpoints.geminiGenerate(
                "https://generativelanguage.googleapis.com/v1beta",
                "gemini-2.5-flash",
            ),
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent",
            AiEndpoints.geminiGenerate(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro",
                "ignored",
            ),
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/x:generateContent",
            AiEndpoints.geminiGenerate(
                "https://generativelanguage.googleapis.com/v1beta/models/x:generateContent",
                "ignored",
            ),
        )
    }

    @Test
    fun `provider presets use the intended protocols`() {
        assertEquals(AiProtocol.ANTHROPIC, AiProviders.fromKey("claude").protocol)
        assertEquals(AiProtocol.OPENAI, AiProviders.fromKey("openai").protocol)
        assertEquals(AiProtocol.OPENAI, AiProviders.fromKey("deepseek").protocol)
        assertEquals("deepseek-flash", AiProviders.fromKey("deepseek").defaultModel)
        assertEquals(
            listOf("deepseek-flash", "deepseek-v4-pro"),
            AiProviders.fromKey("deepseek").modelSuggestions,
        )
        assertEquals(AiProtocol.OPENAI, AiProviders.fromKey("kimi").protocol)
        assertEquals(AiProtocol.GEMINI, AiProviders.fromKey("gemini").protocol)
        assertEquals(AiProviders.claude, AiProviders.fromKey("unknown"))
        assertEquals("DeepSeek · deepseek-chat", AiConfig(
            "deepseek", AiProtocol.OPENAI, "https://api.deepseek.com", "deepseek-chat",
        ).displayName)
    }

    @Test
    fun `old plan model ids get friendly names`() {
        assertEquals("Claude Sonnet 5.5", AiDisplay.pretty("claude-sonnet-5-5"))
        assertEquals("DeepSeek · deepseek-chat", AiDisplay.pretty("DeepSeek · deepseek-chat"))
    }

    @Test
    fun `anthropic replies parse text and stop reasons`() {
        assertEquals(
            "hello",
            AiClient.parseAnthropicReply(
                """{"content":[{"type":"text","text":"hello"}],"stop_reason":"end_turn"}"""
            ),
        )
        val refusal = assertThrows(AiException::class.java) {
            AiClient.parseAnthropicReply("""{"content":[],"stop_reason":"refusal"}""")
        }
        assertTrue(refusal.message!!.contains("declined"))
    }

    @Test
    fun `openai replies parse string and part-array content`() {
        assertEquals(
            "hello",
            AiClient.parseOpenAiReply(
                """{"choices":[{"message":{"role":"assistant","content":"hello"},"finish_reason":"stop"}]}"""
            ),
        )
        assertEquals(
            "hello",
            AiClient.parseOpenAiReply(
                """{"choices":[{"message":{"content":[{"type":"text","text":"he"},{"type":"text","text":"llo"}]},"finish_reason":"stop"}]}"""
            ),
        )
        val truncated = assertThrows(AiException::class.java) {
            AiClient.parseOpenAiReply(
                """{"choices":[{"message":{"content":"x"},"finish_reason":"length"}]}"""
            )
        }
        assertTrue(truncated.message!!.contains("too long"))
    }

    @Test
    fun `gemini replies parse parts and skip thoughts`() {
        assertEquals(
            "hello world",
            AiClient.parseGeminiReply(
                """{"candidates":[{"content":{"parts":[{"text":"hello"},{"text":" world"}]},"finishReason":"STOP"}]}"""
            ),
        )
        assertEquals(
            "visible",
            AiClient.parseGeminiReply(
                """{"candidates":[{"content":{"parts":[{"text":"secret","thought":true},{"text":"visible"}]},"finishReason":"STOP"}]}"""
            ),
        )
        val blocked = assertThrows(AiException::class.java) {
            AiClient.parseGeminiReply("""{"candidates":[{"finishReason":"SAFETY"}]}""")
        }
        assertTrue(blocked.message!!.contains("declined"))
    }

    @Test
    fun `http errors are mapped to actionable messages`() {
        assertTrue(AiClient.httpErrorMessage(401, "{}").contains("API key"))
        assertTrue(AiClient.httpErrorMessage(403, "{}").contains("API key"))
        assertTrue(AiClient.httpErrorMessage(404, "{}").contains("endpoint or model"))
        assertTrue(AiClient.httpErrorMessage(429, "{}").contains("rate-limiting"))
        assertTrue(AiClient.httpErrorMessage(500, "{}").contains("busy"))
        assertTrue(
            AiClient.httpErrorMessage(
                400,
                """{"error":{"message":"model not found"}}""",
            ).contains("model was not found")
        )
        assertEquals(
            "The AI provider could not process this workout: invalid request",
            AiClient.httpErrorMessage(
                400,
                """{"error":{"message":"invalid request"}}""",
            ),
        )
    }

    @Test
    fun `provider error text is extracted from common shapes`() {
        assertEquals(
            "bad key",
            AiClient.extractErrorMessage("""{"error":{"message":"bad key"}}"""),
        )
        assertEquals(
            "bad key",
            AiClient.extractErrorMessage("""{"message":"bad key"}"""),
        )
        assertEquals(
            "bad key",
            AiClient.extractErrorMessage("""{"errors":[{"message":"bad key"}]}"""),
        )
        assertEquals("plain failure", AiClient.extractErrorMessage("plain failure"))
        assertEquals(null, AiClient.extractErrorMessage("<html>nope</html>"))
    }
}
