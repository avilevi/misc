package com.hcexport

import android.content.Context

/** The wire protocol a provider speaks. */
enum class AiProtocol(val key: String, val label: String) {
    ANTHROPIC("anthropic", "Anthropic"),
    OPENAI("openai", "OpenAI-compatible"),
    GEMINI("gemini", "Gemini");

    companion object {
        fun fromKey(key: String?): AiProtocol =
            entries.firstOrNull { it.key == key } ?: OPENAI
    }
}

/**
 * A provider preset. It only fills the endpoint and model fields; the user can
 * edit both, and the API key is stored separately for every preset.
 */
data class AiProvider(
    val key: String,
    val label: String,
    val protocol: AiProtocol,
    val defaultEndpoint: String,
    val defaultModel: String,
    val hint: String,
    val keyUrl: String? = null,
    val modelSuggestions: List<String> = emptyList(),
)

object AiProviders {
    const val CLAUDE = "claude"
    const val OPENAI = "openai"
    const val DEEPSEEK = "deepseek"
    const val KIMI = "kimi"
    const val GEMINI = "gemini"
    const val CUSTOM = "custom"

    val claude = AiProvider(
        key = CLAUDE,
        label = "Claude",
        protocol = AiProtocol.ANTHROPIC,
        defaultEndpoint = "https://api.anthropic.com/v1/messages",
        defaultModel = "claude-opus-5-5",
        hint = "Anthropic API key. Models: Opus 5.5, Sonnet 5.5 and Haiku 5.5.",
        keyUrl = "https://console.anthropic.com/settings/keys",
        modelSuggestions = listOf(
            "claude-opus-5-5",
            "claude-sonnet-5-5",
            "claude-haiku-5-5",
        ),
    )

    val openai = AiProvider(
        key = OPENAI,
        label = "OpenAI",
        protocol = AiProtocol.OPENAI,
        defaultEndpoint = "https://api.openai.com/v1/chat/completions",
        defaultModel = "gpt-4o",
        hint = "OpenAI platform key. Enter a model your account can access.",
        keyUrl = "https://platform.openai.com/api-keys",
        modelSuggestions = listOf("gpt-4o", "gpt-4o-mini"),
    )

    val deepseek = AiProvider(
        key = DEEPSEEK,
        label = "DeepSeek",
        protocol = AiProtocol.OPENAI,
        defaultEndpoint = "https://api.deepseek.com/chat/completions",
        defaultModel = "deepseek-flash",
        hint = "DeepSeek V4 API key. Flash is fast and cheap; Pro is stronger. Thinking is on by default.",
        keyUrl = "https://platform.deepseek.com/api_keys",
        modelSuggestions = listOf("deepseek-flash", "deepseek-v4-pro"),
    )

    val kimi = AiProvider(
        key = KIMI,
        label = "Kimi",
        protocol = AiProtocol.OPENAI,
        defaultEndpoint = "https://api.moonshot.ai/v1/chat/completions",
        defaultModel = "kimi-latest",
        hint = "Moonshot AI key. International: api.moonshot.ai; mainland China: api.moonshot.cn.",
        keyUrl = "https://platform.moonshot.ai/console/api-keys",
        modelSuggestions = listOf(
            "kimi-latest",
            "kimi-k2-0905-preview",
            "moonshot-v1-8k",
        ),
    )

    val gemini = AiProvider(
        key = GEMINI,
        label = "Gemini",
        protocol = AiProtocol.GEMINI,
        defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta",
        defaultModel = "gemini-2.5-flash",
        hint = "Google AI Studio key. The native Gemini API is used; set the model below.",
        keyUrl = "https://aistudio.google.com/app/apikey",
        modelSuggestions = listOf("gemini-2.5-flash", "gemini-2.5-pro"),
    )

    val custom = AiProvider(
        key = CUSTOM,
        label = "Custom",
        protocol = AiProtocol.OPENAI,
        defaultEndpoint = "",
        defaultModel = "",
        hint = "Any OpenAI-compatible, Anthropic or Gemini endpoint. Paste the key that endpoint expects.",
    )

    val presets = listOf(claude, openai, deepseek, kimi, gemini, custom)

    fun fromKey(key: String?): AiProvider =
        presets.firstOrNull { it.key == key } ?: claude
}

data class AiConfig(
    val providerKey: String,
    val protocol: AiProtocol,
    val endpoint: String,
    val model: String,
) {
    val provider: AiProvider
        get() = AiProviders.fromKey(providerKey)

    /** Shown with a saved plan, e.g. "DeepSeek · deepseek-chat". */
    val displayName: String
        get() = if (model.isBlank()) provider.label else "${provider.label} · $model"
}

/** Friendly names for plans saved before providers were configurable. */
object AiDisplay {
    private val knownModels = mapOf(
        "claude-opus-5-5" to "Claude Opus 5.5",
        "claude-sonnet-5-5" to "Claude Sonnet 5.5",
        "claude-haiku-5-5" to "Claude Haiku 5.5",
    )

    fun pretty(value: String): String = knownModels[value.trim()] ?: value
}

/**
 * Normalises user-entered endpoints. Presets use the full URL; these helpers
 * accept a bare base too (for example https://api.deepseek.com).
 */
object AiEndpoints {

    fun anthropicMessages(raw: String): String {
        val value = raw.trim().trimEnd('/')
        if (value.isEmpty()) return ""
        return when {
            value.endsWith("/v1/messages") -> value
            value.endsWith("/v1") -> "$value/messages"
            else -> "$value/v1/messages"
        }
    }

    fun openAiChat(raw: String): String {
        val value = raw.trim().trimEnd('/')
        if (value.isEmpty()) return ""
        return when {
            value.endsWith("/chat/completions") -> value
            value == "https://api.openai.com" ||
                value == "https://api.moonshot.ai" ||
                value == "https://api.moonshot.cn" -> "$value/v1/chat/completions"
            value == "https://api.deepseek.com" -> "$value/chat/completions"
            else -> "$value/chat/completions"
        }
    }

    fun geminiGenerate(raw: String, model: String): String {
        val value = raw.trim().trimEnd('/')
        if (value.isEmpty()) return ""
        if (value.endsWith(":generateContent")) return value
        val name = model.trim().ifBlank { "gemini-2.5-flash" }
        return when {
            value.endsWith("/models") -> "$value/$name:generateContent"
            value.contains("/models/") -> "$value:generateContent"
            else -> "$value/models/$name:generateContent"
        }
    }
}

/**
 * Persists the active provider and its per-provider endpoint and model. The
 * API key lives in [AiKeyStore] under the same provider key.
 */
object AiConfigStore {
    private const val PREFS = "hc_ai_config"
    private const val ACTIVE = "active_provider"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun activeProviderKey(ctx: Context): String {
        val stored = prefs(ctx).getString(ACTIVE, null)
        return if (stored != null && AiProviders.presets.any { it.key == stored }) {
            stored
        } else {
            AiProviders.CLAUDE
        }
    }

    fun setActiveProvider(ctx: Context, key: String) {
        if (AiProviders.presets.none { it.key == key }) return
        prefs(ctx).edit().putString(ACTIVE, key).apply()
    }

    fun endpoint(ctx: Context, providerKey: String): String {
        val stored = prefs(ctx).getString("endpoint_$providerKey", null)
        if (stored != null) return stored
        return AiProviders.fromKey(providerKey).defaultEndpoint
    }

    fun model(ctx: Context, providerKey: String): String {
        var stored = prefs(ctx).getString("model_$providerKey", null)
        if (providerKey == AiProviders.DEEPSEEK &&
            (stored == "deepseek-chat" || stored == "deepseek-reasoner")
        ) {
            // These aliases were retired on 24 July 2026.
            stored = "deepseek-flash"
            prefs(ctx).edit().putString("model_$providerKey", stored).apply()
        }
        if (stored != null) return stored
        if (providerKey == AiProviders.CLAUDE) {
            Prefs.legacyAiModel(ctx)?.let { legacy ->
                // Migrate once, so Reset defaults really restores the preset.
                prefs(ctx).edit().putString("model_$providerKey", legacy).apply()
                Prefs.clearLegacyAiModel(ctx)
                return legacy
            }
        }
        return AiProviders.fromKey(providerKey).defaultModel
    }

    fun protocol(ctx: Context, providerKey: String): AiProtocol =
        if (providerKey == AiProviders.CUSTOM) {
            AiProtocol.fromKey(prefs(ctx).getString("protocol_$providerKey", null))
        } else {
            AiProviders.fromKey(providerKey).protocol
        }

    fun setEndpoint(ctx: Context, providerKey: String, value: String) {
        prefs(ctx).edit().putString("endpoint_$providerKey", value.trim()).apply()
    }

    fun setModel(ctx: Context, providerKey: String, value: String) {
        prefs(ctx).edit().putString("model_$providerKey", value.trim()).apply()
    }

    fun setProtocol(ctx: Context, providerKey: String, protocol: AiProtocol) {
        prefs(ctx).edit().putString("protocol_$providerKey", protocol.key).apply()
    }

    fun reset(ctx: Context, providerKey: String) {
        prefs(ctx).edit().apply {
            remove("endpoint_$providerKey")
            remove("model_$providerKey")
            if (providerKey == AiProviders.CUSTOM) remove("protocol_$providerKey")
        }.apply()
    }

    fun get(ctx: Context, providerKey: String = activeProviderKey(ctx)): AiConfig =
        AiConfig(
            providerKey = providerKey,
            protocol = protocol(ctx, providerKey),
            endpoint = endpoint(ctx, providerKey),
            model = model(ctx, providerKey),
        )

    fun getActive(ctx: Context): AiConfig = get(ctx, activeProviderKey(ctx))
}
