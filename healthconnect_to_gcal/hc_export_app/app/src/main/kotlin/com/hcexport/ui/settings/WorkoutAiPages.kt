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
import com.hcexport.InteractiveStore
import com.hcexport.Prefs
import com.hcexport.R
import com.hcexport.RecordLogic
import com.hcexport.SettingsBackend
import com.hcexport.WearPushWorker
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
import java.util.Date
import java.util.Locale

@Composable
fun WorkoutSettingsPage() {
    val context = LocalContext.current
    var viewKey by remember { mutableStateOf(Prefs.getDefaultWorkoutView(context)) }
    var refreshKey by remember { mutableIntStateOf(0) }
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
