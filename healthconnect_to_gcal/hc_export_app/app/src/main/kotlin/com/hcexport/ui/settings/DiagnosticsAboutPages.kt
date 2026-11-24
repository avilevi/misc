package com.hcexport.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hcexport.BuildConfig
import com.hcexport.Prefs
import com.hcexport.R
import com.hcexport.SettingsBackend
import com.hcexport.SyncLogger
import com.hcexport.SyncStatus
import com.hcexport.UpdateChecker
import com.hcexport.ui.theme.Abyss
import com.hcexport.ui.theme.Amber
import com.hcexport.ui.theme.CardHigh
import com.hcexport.ui.theme.Danger
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Periwinkle
import com.hcexport.ui.theme.Slate
import com.hcexport.ui.theme.Snow
import com.hcexport.ui.theme.Success
import com.hcexport.ui.theme.Teal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsSettingsPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by produceState<SyncStatus?>(null) {
        value = withContext(Dispatchers.IO) { SettingsBackend.syncStatus(context) }
    }
    var showLog by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var downloadPct by remember { mutableIntStateOf(0) }
    var updateError by remember { mutableStateOf<String?>(null) }

    SettingsPageList {
        item {
            SettingsNote("A read-only view of what the app is doing, plus the tools to fix it.")
        }

        item { SettingsSectionHeader("Sync status") }
        item {
            val current = status
            if (current == null) {
                com.hcexport.ui.LoadingLine("Reading sync status…", Teal)
            } else {
                SettingsCard {
                    SettingsRow(
                        title = "Last sync",
                        subtitle = current.lastSyncSummary,
                        value = formatDiagTime(current.lastSyncMs) ?: "Never",
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Workout database",
                        subtitle = current.lastPullSummary,
                        value = "${current.workoutCount}",
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Next auto-sync",
                        value = formatDiagTime(current.nextSyncMs) ?: "No schedule",
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Next workout refresh",
                        value = formatDiagTime(current.nextPullMs) ?: "—",
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Last watch push",
                        value = formatDiagTime(current.watchPushMs) ?: "Not yet",
                    )
                }
            }
        }

        item { SettingsSectionHeader("Sync log") }
        item {
            SettingsCard {
                SettingsRow(
                    title = "View sync log",
                    subtitle = "The last sync and workout pull messages",
                    onClick = {
                        logText = runCatching { SyncLogger.read(context) }.getOrDefault("")
                        showLog = true
                    },
                )
            }
        }

        item { SettingsSectionHeader("Updates") }
        item {
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                SettingsRow(
                    title = "Installed version",
                    value = "v${BuildConfig.VERSION_NAME}" + if (BuildConfig.DEBUG) " · debug" else "",
                )
                SettingsDivider()
                SettingsRow(
                    title = "Check for updates",
                    subtitle = if (BuildConfig.DEBUG) {
                        "Debug build — the release APK is a separate app"
                    } else {
                        "Downloads and installs from the project release"
                    },
                    onClick = {
                        if (checking || downloading) return@SettingsRow
                        checking = true
                        updateError = null
                        scope.launch {
                            val info = UpdateChecker.checkForUpdate(BuildConfig.VERSION_CODE)
                            checking = false
                            if (info == null) {
                                Toast.makeText(context, "No newer build found", Toast.LENGTH_SHORT).show()
                            } else {
                                updateInfo = info
                            }
                        }
                    },
                    trailing = {
                        if (checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Teal,
                                strokeWidth = 2.dp,
                            )
                        }
                    },
                )
            }
        }

    }

    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            containerColor = CardHigh,
            title = { Text("Sync log", style = MaterialTheme.typography.headlineSmall, color = Snow) },
            text = {
                Text(
                    logText.ifBlank { "No log entries yet." },
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = Mist,
                )
            },
            confirmButton = {
                TextButton(onClick = { showLog = false }) {
                    Text("Close", color = Teal, style = MaterialTheme.typography.titleSmall)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        SyncLogger.clear(context)
                        logText = ""
                    }
                ) {
                    Text("Clear", color = Danger, style = MaterialTheme.typography.titleSmall)
                }
            },
        )
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { if (!downloading) updateInfo = null },
            containerColor = CardHigh,
            title = { Text("Update available", style = MaterialTheme.typography.headlineSmall, color = Snow) },
            text = {
                Column {
                    Text(
                        "v${info.remoteVersionName} is available (you have v${BuildConfig.VERSION_NAME}).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mist,
                    )
                    if (BuildConfig.DEBUG) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "This is the debug build. The release APK installs as a separate " +
                                "app with its own data: back up from Settings → Backup & restore " +
                                "first, then restore that file in the release app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Amber,
                        )
                    }
                    if (info.buildDate.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Built ${info.buildDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate,
                        )
                    }
                    if (downloading) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Downloading… $downloadPct%",
                            style = MaterialTheme.typography.bodySmall,
                            color = Teal,
                        )
                    }
                    if (updateError != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            updateError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Danger,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !downloading,
                    onClick = {
                        downloading = true
                        downloadPct = 0
                        updateError = null
                        scope.launch {
                            runCatching {
                                UpdateChecker.downloadAndInstall(
                                    context,
                                    info.remoteVersionCode,
                                ) { pct ->
                                    scope.launch { downloadPct = pct }
                                }
                            }.onFailure {
                                updateError = it.message ?: "Download failed"
                            }
                            downloading = false
                        }
                    },
                ) {
                    Text(if (downloading) "Downloading…" else "Install", color = Teal)
                }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }, enabled = !downloading) {
                    Text("Later", color = Slate)
                }
            },
        )
    }
}

@Composable
fun AboutSettingsPage() {
    val context = LocalContext.current
    SettingsPageList {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(66.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(Teal, Teal.copy(alpha = 0.55f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "HC",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Abyss,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("HC Sync", style = MaterialTheme.typography.displaySmall, color = Snow)
                Text(
                    "v${BuildConfig.VERSION_NAME}" + if (BuildConfig.DEBUG) " · debug build" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
            }
        }

        item { SettingsSectionHeader("What it does") }
        item {
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                Text(
                    "Reads exercise and sleep sessions from Health Connect, writes them to the phone's Google Calendar, " +
                        "keeps a permanent workout archive, and tracks personal bests and benchmarks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist,
                )
            }
        }

        item { SettingsSectionHeader("Data & privacy") }
        item {
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                Text(
                    "Workouts, weight records, benchmarks and AI plans are stored locally and included in Android backup. " +
                        "Journal entries contain health data and are deliberately excluded from backup and from every AI request.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist,
                )
            }
        }

        item { SettingsSectionHeader("AI workout") }
        item {
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                Text(
                    "AI workout sends only the workout's date, type and text to the AI provider you configure " +
                        "(Claude, OpenAI, DeepSeek, Kimi, Gemini or a custom endpoint). API keys are encrypted with the " +
                        "Android Keystore and are never included in logs or backups.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mist,
                )
            }
        }

        item { SettingsSectionHeader("Credits") }
        item {
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                Text(
                    "Workout programming: HYPR Workouts. Typeface: Barlow by the Barlow Project Authors, SIL Open Font License. " +
                        "Workout parsing: Claude, OpenAI, DeepSeek, Kimi, Gemini or your own compatible endpoint.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    title = "HYPR Workouts",
                    subtitle = "hypr-workouts.pages.dev",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://hypr-workouts.pages.dev/"),
                                )
                            )
                        }
                    },
                )
            }
        }

        item {
            Text(
                "Made for personal training tracking. No account, no analytics, no ads.",
                modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun formatDiagTime(ms: Long?): String? {
    if (ms == null) return null
    return SimpleDateFormat("EEE, dd MMM HH:mm", Locale.getDefault()).format(Date(ms))
}
