package com.hcexport.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hcexport.AiClient
import com.hcexport.AiConfigStore
import com.hcexport.AiKeyStore
import com.hcexport.AiProtocol
import com.hcexport.AiProvider
import com.hcexport.AiProviders
import com.hcexport.InteractiveStore
import com.hcexport.ui.ConfirmDialog
import com.hcexport.ui.DataRefresh
import com.hcexport.ui.SegmentedControl
import com.hcexport.ui.Tag
import com.hcexport.ui.theme.CardHigh
import com.hcexport.ui.theme.Danger
import com.hcexport.ui.theme.Hairline
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Periwinkle
import com.hcexport.ui.theme.Slate
import com.hcexport.ui.theme.Snow
import com.hcexport.ui.theme.Success
import com.hcexport.ui.theme.Teal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface AiTestState {
    data object Idle : AiTestState
    data object Testing : AiTestState
    data class Success(val label: String) : AiTestState
    data class Failed(val message: String) : AiTestState
}

@Composable
fun AiSettingsPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var providerKey by remember { mutableStateOf(AiConfigStore.activeProviderKey(context)) }
    var endpoint by remember { mutableStateOf(AiConfigStore.endpoint(context, providerKey)) }
    var model by remember { mutableStateOf(AiConfigStore.model(context, providerKey)) }
    var protocol by remember { mutableStateOf(AiConfigStore.protocol(context, providerKey)) }
    var configured by remember { mutableStateOf(AiKeyStore.hasKey(context, providerKey)) }
    var replacing by remember { mutableStateOf(!configured) }
    var keyText by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var testState by remember { mutableStateOf<AiTestState>(AiTestState.Idle) }
    var interactiveCount by remember { mutableIntStateOf(0) }
    var showClear by remember { mutableStateOf(false) }

    val provider = AiProviders.fromKey(providerKey)
    val accent = if (providerKey == AiProviders.CUSTOM) Periwinkle else Teal

    fun applyProvider(key: String) {
        providerKey = key
        AiConfigStore.setActiveProvider(context, key)
        endpoint = AiConfigStore.endpoint(context, key)
        model = AiConfigStore.model(context, key)
        protocol = AiConfigStore.protocol(context, key)
        configured = AiKeyStore.hasKey(context, key)
        replacing = !configured
        keyText = ""
        reveal = false
        testState = AiTestState.Idle
    }

    LaunchedEffect(Unit) {
        interactiveCount = withContext(Dispatchers.IO) { InteractiveStore.count(context) }
    }

    if (showClear) {
        ConfirmDialog(
            title = "Clear interactive workouts?",
            message = "Every AI-generated plan and the values you entered will be removed. Workouts and records are untouched.",
            confirmText = "Clear",
            onConfirm = {
                showClear = false
                scope.launch {
                    withContext(Dispatchers.IO) { InteractiveStore.deleteAll(context) }
                    interactiveCount = 0
                    DataRefresh.bump()
                    Toast.makeText(context, "Interactive plans cleared", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showClear = false },
        )
    }

    SettingsPageList {
        item {
            SettingsNote(
                "Turn a workout's text into exercises, sets and metrics with the AI provider you choose. Only the workout's date, type and text are sent.",
            )
        }

        item { SettingsSectionHeader("Provider") }
        item {
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                ProviderFlow(selected = providerKey) { applyProvider(it) }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Choosing a provider fills its endpoint and a default model. The endpoint, model and key all stay editable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
            }
        }

        item { SettingsSectionHeader("Endpoint") }
        item {
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("API style", style = MaterialTheme.typography.titleSmall, color = Snow)
                        Text(
                            provider.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate,
                        )
                    }
                    Tag(protocol.label, accent)
                }
                Spacer(Modifier.height(10.dp))
                SettingsTextField(
                    value = endpoint,
                    onValueChange = {
                        endpoint = it
                        AiConfigStore.setEndpoint(context, providerKey, it)
                        testState = AiTestState.Idle
                    },
                    label = "Endpoint",
                    accent = accent,
                    singleLine = false,
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    endpointHint(provider, protocol),
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
                if (providerKey == AiProviders.CUSTOM) {
                    Spacer(Modifier.height(12.dp))
                    Text("Protocol", style = MaterialTheme.typography.titleSmall, color = Snow)
                    Spacer(Modifier.height(8.dp))
                    SegmentedControl(
                        options = AiProtocol.entries,
                        selected = protocol,
                        label = { protocolShortLabel(it) },
                        onSelect = {
                            protocol = it
                            AiConfigStore.setProtocol(context, providerKey, it)
                            testState = AiTestState.Idle
                        },
                        accent = Periwinkle,
                    )
                }
                Spacer(Modifier.height(12.dp))
                SettingsPillButton("Reset defaults", Slate) {
                    AiConfigStore.reset(context, providerKey)
                    endpoint = AiConfigStore.endpoint(context, providerKey)
                    model = AiConfigStore.model(context, providerKey)
                    protocol = AiConfigStore.protocol(context, providerKey)
                    testState = AiTestState.Idle
                }
            }
        }

        item { SettingsSectionHeader("Model") }
        item {
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                SettingsTextField(
                    value = model,
                    onValueChange = {
                        model = it
                        AiConfigStore.setModel(context, providerKey, it)
                        testState = AiTestState.Idle
                    },
                    label = "Model",
                    accent = accent,
                    keyboardType = KeyboardType.Text,
                )
                if (provider.modelSuggestions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Suggested",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate,
                    )
                    Spacer(Modifier.height(7.dp))
                    SuggestionFlow(
                        suggestions = provider.modelSuggestions,
                        selected = model,
                        accent = accent,
                    ) { suggestion ->
                        model = suggestion
                        AiConfigStore.setModel(context, providerKey, suggestion)
                        testState = AiTestState.Idle
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    modelHint(provider),
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
            }
        }

        item { SettingsSectionHeader("API key") }
        item {
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                if (configured && !replacing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Teal.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = Teal,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Configured", style = MaterialTheme.typography.titleSmall, color = Snow)
                            Text(
                                "Encrypted with the Android Keystore.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsPillButton("Replace key", Periwinkle) {
                            replacing = true
                            keyText = ""
                            reveal = false
                            testState = AiTestState.Idle
                        }
                        SettingsPillButton("Remove", Danger) {
                            AiKeyStore.clear(context, providerKey)
                            configured = false
                            replacing = true
                            keyText = ""
                            testState = AiTestState.Idle
                        }
                    }
                } else {
                    SettingsTextField(
                        value = keyText,
                        onValueChange = { keyText = it },
                        label = "${provider.label} API key",
                        accent = accent,
                        keyboardType = KeyboardType.Text,
                        visualTransformation = if (reveal) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            Text(
                                if (reveal) "HIDE" else "SHOW",
                                modifier = Modifier
                                    .clickable { reveal = !reveal }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                            )
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsPillButton("Save key", Teal, enabled = keyText.isNotBlank()) {
                            AiKeyStore.setApiKey(context, providerKey, keyText)
                            keyText = ""
                            configured = true
                            replacing = false
                            testState = AiTestState.Idle
                            Toast.makeText(
                                context,
                                "API key saved for ${provider.label}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        if (configured) {
                            SettingsPillButton("Cancel", Slate) {
                                replacing = false
                                keyText = ""
                                testState = AiTestState.Idle
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    provider.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
                provider.keyUrl?.let { url ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Get an API key",
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { openUrl(context, url) }
                            .padding(vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = Teal,
                    )
                }
            }
        }

        item { SettingsSectionHeader("Connection") }
        item {
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                SettingsPrimaryAction("Test connection", accent) {
                    if (!configured) {
                        testState = AiTestState.Failed("Save an API key for ${provider.label} first.")
                    } else {
                        testState = AiTestState.Testing
                        scope.launch {
                            val result = AiClient.testConnection(context)
                            testState = result.fold(
                                onSuccess = { AiTestState.Success(it) },
                                onFailure = { AiTestState.Failed(it.message ?: "Connection failed") },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                when (val state = testState) {
                    AiTestState.Idle -> Text(
                        "A test sends a tiny request to verify the endpoint, key and model.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                    )
                    AiTestState.Testing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = accent,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Talking to ${provider.label}…",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate,
                        )
                    }
                    is AiTestState.Success -> Text(
                        "Connected to ${state.label}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Success,
                    )
                    is AiTestState.Failed -> Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Danger,
                    )
                }
            }
        }

        item { SettingsSectionHeader("Interactive workouts") }
        item {
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                SettingsRow(
                    title = "Saved AI plans",
                    subtitle = "Plans and your entered values live on this device.",
                    value = "$interactiveCount",
                )
                Spacer(Modifier.height(6.dp))
                SettingsSecondaryAction("Clear interactive plans", Danger) {
                    showClear = true
                }
            }
        }

        item { SettingsSectionHeader("Privacy") }
        item {
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                Text(
                    "Only the workout's date, type and text are sent to the AI provider you choose. Your journal, sleep data, " +
                        "records and calendar account details never leave the device. API keys are encrypted with the Android " +
                        "Keystore and are never logged; generated plans are included in Android backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderFlow(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AiProviders.presets.forEach { provider ->
            val isSelected = provider.key == selected
            Text(
                provider.label,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Teal.copy(alpha = 0.16f) else CardHigh)
                    .border(
                        1.dp,
                        if (isSelected) Teal.copy(alpha = 0.5f) else Hairline,
                        RoundedCornerShape(50),
                    )
                    .clickable { onSelect(provider.key) }
                    .padding(horizontal = 13.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) Teal else Mist,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionFlow(
    suggestions: List<String>,
    selected: String,
    accent: androidx.compose.ui.graphics.Color,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        suggestions.forEach { suggestion ->
            val isSelected = suggestion == selected
            Text(
                suggestion,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) accent.copy(alpha = 0.14f) else CardHigh)
                    .border(
                        1.dp,
                        if (isSelected) accent.copy(alpha = 0.42f) else Hairline,
                        RoundedCornerShape(50),
                    )
                    .clickable { onSelect(suggestion) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) accent else Mist,
            )
        }
    }
}

private fun protocolShortLabel(protocol: AiProtocol): String = when (protocol) {
    AiProtocol.ANTHROPIC -> "Anthropic"
    AiProtocol.OPENAI -> "OpenAI"
    AiProtocol.GEMINI -> "Gemini"
}

private fun endpointHint(provider: AiProvider, protocol: AiProtocol): String = when {
    provider.key == AiProviders.CUSTOM -> when (protocol) {
        AiProtocol.ANTHROPIC -> "A full Messages URL, e.g. https://host/v1/messages."
        AiProtocol.GEMINI -> "The Gemini API base, e.g. https://host/v1beta. The model is appended."
        AiProtocol.OPENAI -> "A full endpoint URL, usually ending in /chat/completions."
    }
    provider.key == AiProviders.CLAUDE ->
        "The full Messages endpoint. A bare API base is completed automatically."
    provider.key == AiProviders.OPENAI ->
        "Usually https://api.openai.com/v1/chat/completions."
    provider.key == AiProviders.DEEPSEEK ->
        "Usually https://api.deepseek.com/chat/completions."
    provider.key == AiProviders.KIMI ->
        "International: https://api.moonshot.ai/v1/chat/completions. Mainland China uses api.moonshot.cn."
    provider.key == AiProviders.GEMINI ->
        "The API base. The model is appended, e.g. https://generativelanguage.googleapis.com/v1beta."
    else ->
        "A full endpoint URL. OpenAI-compatible servers usually end in /chat/completions."
}

private fun modelHint(provider: AiProvider): String = when (provider.key) {
    AiProviders.CLAUDE -> "Anthropic model ID, e.g. claude-opus-5-5."
    AiProviders.OPENAI -> "The exact model ID your key can access. Newer OpenAI models are handled automatically."
    AiProviders.DEEPSEEK -> "deepseek-flash is fast and cheap; deepseek-v4-pro is stronger. Thinking mode is on by default."
    AiProviders.KIMI -> "kimi-latest follows Moonshot's current model."
    AiProviders.GEMINI -> "The model name is inserted into the Gemini URL."
    else -> "The exact model ID your endpoint expects."
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }
}
