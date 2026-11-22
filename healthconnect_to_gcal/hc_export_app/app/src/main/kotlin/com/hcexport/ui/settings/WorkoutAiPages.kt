package com.hcexport.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hcexport.AiClient
import com.hcexport.AiKeyStore
import com.hcexport.BridgePull
import com.hcexport.BridgeSecretStore
import com.hcexport.InteractiveStore
import com.hcexport.Prefs
import com.hcexport.R
import com.hcexport.RecordLogic
import com.hcexport.SettingsBackend
import com.hcexport.WearPushWorker
import com.hcexport.WeekStart
import com.hcexport.WorkoutFetchWorker
import com.hcexport.WorkoutSettingsStatus
import com.hcexport.WorkoutStore
import com.hcexport.ArchiveSummary
import com.hcexport.ui.DataRefresh
import com.hcexport.ui.SegmentedControl
import com.hcexport.ui.theme.CardHigh
import com.hcexport.ui.theme.Danger
import com.hcexport.ui.theme.Hairline
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Periwinkle
import com.hcexport.ui.theme.Slate
import com.hcexport.ui.theme.Snow
import com.hcexport.ui.theme.Success
import com.hcexport.ui.theme.Teal
import com.hcexport.ui.workouts.CalendarView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

@Composable
fun WorkoutSettingsPage() {
    val context = LocalContext.current
    var viewKey by remember { mutableStateOf(Prefs.getDefaultWorkoutView(context)) }
    var journalViewKey by remember { mutableStateOf(Prefs.getJournalView(context)) }
    var firstDay by remember { mutableStateOf(Prefs.getFirstDayOfWeek(context)) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var bridgeEmail by remember { mutableStateOf(BridgeSecretStore.getEmail(context).orEmpty()) }
    var bridgePassword by remember { mutableStateOf("") }
    var bridgeReveal by remember { mutableStateOf(false) }
    var bridgeBusy by remember { mutableStateOf(false) }
    var bridgeError by remember { mutableStateOf<String?>(null) }
    val bridge by produceState<BridgePull.Status?>(null, refreshKey) {
        value = withContext(Dispatchers.IO) { BridgePull.status(context) }
    }
    val status by produceState<WorkoutSettingsStatus?>(null, refreshKey) {
        value = withContext(Dispatchers.IO) { SettingsBackend.workoutStatus(context) }
    }
    val archive by produceState<ArchiveSummary?>(null, refreshKey) {
        value = withContext(Dispatchers.IO) { WorkoutStore.archiveSummary(context) }
    }

    LifecycleResumeEffect(Unit) {
        refreshKey += 1
        onPauseOrDispose { }
    }

    SettingsPageList {
        item {
            SettingsNote("How the workout calendar behaves and what is stored for the watch.")
        }

        item { SettingsSectionHeader("Calendar default") }
        item {
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                Text(
                    "The view Workouts opens in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
                Spacer(Modifier.height(10.dp))
                SegmentedControl(
                    options = CalendarView.entries,
                    selected = CalendarView.fromKey(viewKey),
                    label = { it.label },
                    onSelect = {
                        viewKey = it.key
                        Prefs.setDefaultWorkoutView(context, it.key)
                    },
                    accent = Periwinkle,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "The view the Journal opens in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
                Spacer(Modifier.height(10.dp))
                SegmentedControl(
                    options = CalendarView.entries,
                    selected = CalendarView.fromKey(journalViewKey),
                    label = { it.label },
                    onSelect = {
                        journalViewKey = it.key
                        Prefs.setJournalView(context, it.key)
                    },
                    accent = Periwinkle,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "The first day of the week in both calendars.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
                Spacer(Modifier.height(10.dp))
                SegmentedControl(
                    options = WeekStart.selectable,
                    selected = firstDay,
                    label = { day -> day.getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
                    onSelect = {
                        firstDay = it
                        Prefs.setFirstDayOfWeek(context, it)
                    },
                    accent = Periwinkle,
                )
            }
        }

        item { SettingsSectionHeader("Workout database") }
        item {
            val current = status
            if (current == null) {
                com.hcexport.ui.LoadingLine("Reading workout status…", Periwinkle)
            } else {
                SettingsCard {
                    SettingsRow(
                        title = "Workouts stored",
                        subtitle = current.lastPullSummary,
                        value = "${current.count}",
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Latest workout",
                        value = current.latestDate ?: "—",
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Last pull",
                        value = formatSettingsTime(current.lastPullMs) ?: "Never",
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Last watch push",
                        value = formatSettingsTime(current.watchPushMs) ?: "Not yet",
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp)) {
                SettingsPrimaryAction("Refresh workouts now", Periwinkle) {
                    WorkoutFetchWorker.enqueue(context)
                    Toast.makeText(context, "Refreshing workouts…", Toast.LENGTH_SHORT).show()
                }
                Spacer(Modifier.height(8.dp))
                SettingsSecondaryAction("Push snapshot to watch", Periwinkle) {
                    WearPushWorker.enqueue(context)
                    Toast.makeText(context, "Sending snapshot to the watch…", Toast.LENGTH_SHORT).show()
                }
            }
        }

        item { SettingsSectionHeader("BridgeAthletic") }
        item {
            val current = bridge
            when {
                current == null -> com.hcexport.ui.LoadingLine("Reading BridgeAthletic status…", Periwinkle)
                !current.connected -> SettingsCard(contentPadding = PaddingValues(16.dp)) {
                    Text(
                        "Pull the weightlifting workouts assigned to you in BridgeAthletic. " +
                            "The login is kept encrypted on this phone and is only used to read your workouts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                    )
                    Spacer(Modifier.height(12.dp))
                    SettingsTextField(
                        value = bridgeEmail,
                        onValueChange = {
                            bridgeEmail = it
                            bridgeError = null
                        },
                        label = "BridgeAthletic email",
                        accent = Periwinkle,
                        keyboardType = KeyboardType.Email,
                    )
                    Spacer(Modifier.height(8.dp))
                    SettingsTextField(
                        value = bridgePassword,
                        onValueChange = {
                            bridgePassword = it
                            bridgeError = null
                        },
                        label = "Password",
                        accent = Periwinkle,
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (bridgeReveal) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            Text(
                                if (bridgeReveal) "HIDE" else "SHOW",
                                modifier = Modifier
                                    .clickable { bridgeReveal = !bridgeReveal }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Periwinkle,
                            )
                        },
                    )
                    bridgeError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Danger)
                    }
                }
                else -> SettingsCard {
                    SettingsRow(
                        title = "Account",
                        subtitle = current.profileName ?: current.email,
                        value = null,
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Workouts from BridgeAthletic",
                        value = "${current.workouts}",
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Last pull",
                        subtitle = current.lastSummary,
                        value = formatSettingsTime(current.lastPullMs) ?: "Never",
                    )
                    current.lastError?.let {
                        SettingsDivider()
                        SettingsRow(title = "Last error", subtitle = it)
                    }
                }
            }
        }
        item {
            val connected = bridge?.connected == true
            Column(Modifier.padding(top = 10.dp)) {
                if (!connected) {
                    SettingsPrimaryAction(
                        if (bridgeBusy) "Connecting…" else "Connect BridgeAthletic",
                        Periwinkle,
                    ) {
                        if (bridgeBusy) return@SettingsPrimaryAction
                        if (bridgeEmail.isBlank() || bridgePassword.isBlank()) {
                            bridgeError = "Enter your BridgeAthletic email and password"
                            return@SettingsPrimaryAction
                        }
                        bridgeBusy = true
                        bridgeError = null
                        scope.launch {
                            val error = BridgePull.connect(context, bridgeEmail, bridgePassword)
                            bridgeBusy = false
                            if (error == null) {
                                bridgePassword = ""
                                Toast.makeText(
                                    context,
                                    "Connected to BridgeAthletic",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                WorkoutFetchWorker.enqueue(context)
                                refreshKey += 1
                            } else {
                                bridgeError = error
                            }
                        }
                    }
                } else {
                    SettingsPrimaryAction("Pull BridgeAthletic now", Periwinkle) {
                        WorkoutFetchWorker.enqueue(context)
                        Toast.makeText(context, "Pulling BridgeAthletic…", Toast.LENGTH_SHORT).show()
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingsSecondaryAction("Disconnect BridgeAthletic", Periwinkle) {
                        BridgeSecretStore.clearCredentials(context)
                        bridgePassword = ""
                        Toast.makeText(context, "BridgeAthletic disconnected", Toast.LENGTH_SHORT).show()
                        refreshKey += 1
                    }
                }
            }
        }

        item { SettingsSectionHeader("History") }
        item {
            val summary = archive
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                SettingsRow(
                    title = "Workout archive",
                    subtitle = "Every pulled workout is kept. HYPR dropping a date never deletes it.",
                    value = summary?.let { "${it.workouts}" } ?: "—",
                )
                SettingsDivider()
                SettingsRow(
                    title = "Pulled since",
                    value = summary?.firstDate?.let { RecordLogic.formatDate(it) } ?: "—",
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Browse the archive in Workouts → History. Export a CSV copy from the History screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
            }
        }

        item { SettingsSectionHeader("Watch") }
        item {
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                Text(
                    "The phone pushes a gzipped snapshot of workouts and journal entries. " +
                        "The watch app must share this app's applicationId and signing key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    title = "Wear OS snapshot",
                    value = formatSettingsTime(status?.watchPushMs) ?: "Not yet",
                )
            }
        }
    }
}

private fun formatSettingsTime(ms: Long?): String? {
    if (ms == null) return null
    return SimpleDateFormat("EEE, dd MMM HH:mm", Locale.getDefault()).format(Date(ms))
}
