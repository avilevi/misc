package com.hcexport.ui.settings

import android.content.Intent
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hcexport.AlarmScheduler
import com.hcexport.BatteryOptimizationHelper
import com.hcexport.BuildConfig
import com.hcexport.CalendarAccount
import com.hcexport.CalendarHelper
import com.hcexport.Prefs
import com.hcexport.R
import com.hcexport.ReorderActivity
import com.hcexport.SettingsBackend
import com.hcexport.SyncSchedule
import com.hcexport.SyncStatus
import com.hcexport.SyncWorker
import com.hcexport.ui.LoadingLine
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CAL_RANGE_DAYS = listOf(7L, 14L, 30L, 60L, 90L, 180L, 365L)
private val RANGE_LABELS = listOf("7 days", "14 days", "30 days", "60 days", "90 days", "180 days", "1 year")

@Composable
fun CalendarSettingsPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var calendars by remember { mutableStateOf<List<CalendarAccount>>(emptyList()) }
    var selectedId by remember { mutableStateOf(Prefs.getCalendarId(context)) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showPicker by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        calendars = withContext(Dispatchers.IO) {
            SettingsBackend.loadGoogleCalendars(context)
        }
        if (selectedId == null && calendars.isNotEmpty()) {
            selectedId = calendars.first().id
            Prefs.setCalendarId(context, calendars.first().id)
        }
    }

    val selected = calendars.firstOrNull { it.id == selectedId }

    SettingsPageList {
        item {
            SettingsNote(
                "Choose the Google Calendar HC Sync writes to. The account is already on this device — no OAuth.",
            )
        }
        item { SettingsSectionHeader("Calendar") }
        item {
            SettingsCard {
                SettingsRow(
                    title = "Target calendar",
                    subtitle = selected?.accountName ?: "No calendar selected",
                    value = selected?.displayName ?: "Not selected",
                    onClick = { showPicker = true },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Refresh calendar list",
                    value = "${calendars.size} found",
                    onClick = { refreshKey += 1 },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Create a new calendar",
                    subtitle = "On the selected Google account",
                    onClick = { showCreate = true },
                )
            }
        }
        item { SettingsSectionHeader("Calendar actions") }
        item {
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                SettingsPrimaryAction("Full re-sync (calendar + journal)", Teal) {
                    SyncWorker.enqueue(context, forceCalendar = true, forceJournal = true)
                    Toast.makeText(context, "Full re-sync started", Toast.LENGTH_SHORT).show()
                }
                Spacer(Modifier.height(8.dp))
                SettingsNote(
                    "Rebuilds calendar events and journal entries from the last synced data. It can take a minute.",
                )
            }
        }
    }

    if (showPicker) {
        SettingsChoiceDialog(
            title = "Target calendar",
            options = calendars.map {
                SettingsChoice(it.id, it.displayName, it.accountName)
            },
            selected = selectedId,
            accent = Teal,
            onSelect = {
                selectedId = it
                Prefs.setCalendarId(context, it)
            },
            onDismiss = { showPicker = false },
        )
    }

    if (showCreate) {
        SettingsTextDialog(
            title = "Create a new calendar",
            label = "Calendar name",
            initial = "Health Connect",
            accent = Teal,
            confirmText = "Create",
            onConfirm = { name ->
                showCreate = false
                val account = selected ?: calendars.firstOrNull()
                scope.launch {
                    val id = withContext(Dispatchers.IO) {
                        CalendarHelper.createCalendar(
                            context,
                            name.ifBlank { "Health Connect" },
                            account?.accountName ?: "",
                            account?.accountType ?: "com.google",
                        )
                    }
                    if (id != null) {
                        Prefs.setCalendarId(context, id)
                        selectedId = id
                        refreshKey += 1
                        Toast.makeText(context, "Calendar \"$name\" created", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Could not create the calendar", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
fun SyncSettingsPage() {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var schedules by remember { mutableStateOf(Prefs.getSyncSchedules(context)) }
    var showAddSchedule by remember { mutableStateOf(false) }
    var showCalendarRange by remember { mutableStateOf(false) }
    var showJournalRange by remember { mutableStateOf(false) }
    var sleepSources by remember { mutableStateOf(Prefs.getSleepSourcePriority(context)) }
    var exerciseSources by remember { mutableStateOf(Prefs.getExerciseSourcePriority(context)) }
    var calendarDays by remember { mutableLongStateOf(Prefs.getSyncDaysBack(context)) }
    var journalDays by remember { mutableLongStateOf(Prefs.getJournalSyncDaysBack(context)) }
    val status by produceState<SyncStatus?>(null, refreshKey) {
        value = withContext(Dispatchers.IO) { SettingsBackend.syncStatus(context) }
    }

    LifecycleResumeEffect(Unit) {
        sleepSources = Prefs.getSleepSourcePriority(context)
        exerciseSources = Prefs.getExerciseSourcePriority(context)
        calendarDays = Prefs.getSyncDaysBack(context)
        journalDays = Prefs.getJournalSyncDaysBack(context)
        refreshKey += 1
        onPauseOrDispose { }
    }

    SettingsPageList {
        item { SettingsNote("How far back HC Sync reads, writes to the calendar, and keeps in the journal.") }
        item { SettingsSectionHeader("Sync ranges") }
        item {
            SettingsCard {
                SettingsRow(
                    title = "Calendar history",
                    subtitle = "How far back sessions are written to Google Calendar",
                    value = "$calendarDays days",
                    onClick = { showCalendarRange = true },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Journal history",
                    subtitle = "How far back exercise and sleep entries are merged",
                    value = "$journalDays days",
                    onClick = { showJournalRange = true },
                )
            }
        }

        item { SettingsSectionHeader("Auto-sync") }
        item {
            SettingsCard {
                if (schedules.isEmpty()) {
                    Text(
                        "No schedules yet.",
                        modifier = Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                    )
                } else {
                    schedules.forEachIndexed { index, schedule ->
                        if (index > 0) SettingsDivider()
                        SettingsRow(
                            title = schedule.displayString(),
                            subtitle = if (schedule.type == "daily") "Every day" else "Weekly",
                            trailing = {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete schedule",
                                    tint = Danger,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable {
                                            val updated = schedules.filter { it.id != schedule.id }
                                            schedules = updated
                                            Prefs.setSyncSchedules(context, updated)
                                            AlarmScheduler.scheduleNextSync(context)
                                        },
                                )
                            },
                        )
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(top = 10.dp)) {
                SettingsSecondaryAction("+ Add schedule", Periwinkle) {
                    showAddSchedule = true
                }
            }
        }

        item { SettingsSectionHeader("Source priority") }
        item {
            SettingsCard {
                SettingsRow(
                    title = "Sleep sources",
                    subtitle = SettingsBackend.sourceSummary(sleepSources),
                    trailing = {
                        SettingsPillButton("Reorder", Periwinkle) {
                            context.startActivity(
                                Intent(context, ReorderActivity::class.java).putExtra(
                                    ReorderActivity.EXTRA_TYPE,
                                    ReorderActivity.TYPE_SLEEP,
                                )
                            )
                        }
                    },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Exercise sources",
                    subtitle = SettingsBackend.sourceSummary(exerciseSources),
                    trailing = {
                        SettingsPillButton("Reorder", Periwinkle) {
                            context.startActivity(
                                Intent(context, ReorderActivity::class.java).putExtra(
                                    ReorderActivity.EXTRA_TYPE,
                                    ReorderActivity.TYPE_EXERCISE,
                                )
                            )
                        }
                    },
                )
            }
        }

        item { SettingsSectionHeader("Background reliability") }
        item {
            SettingsCard {
                SettingsRow(
                    title = "Battery optimisation",
                    subtitle = BatteryOptimizationHelper.statusLine(context),
                    trailing = {
                        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                            SettingsPillButton("Allow", Success) {
                                runCatching {
                                    context.startActivity(
                                        BatteryOptimizationHelper.requestExemptionIntent(context)
                                    )
                                }
                            }
                        }
                    },
                )
                SettingsDivider()
                SettingsRow(
                    title = "Exact alarms",
                    subtitle = if (AlarmScheduler.canScheduleExact(context))
                        "Allowed — schedules fire on time."
                    else
                        "Not allowed — a sync can drift up to an hour.",
                    trailing = {
                        if (!AlarmScheduler.canScheduleExact(context)) {
                            SettingsPillButton("Allow", Success) {
                                runCatching {
                                    context.startActivity(AlarmScheduler.exactAlarmSettingsIntent(context))
                                }
                            }
                        }
                    },
                )
                SettingsDivider()
                Text(
                    "On Samsung, also open Battery → Background usage limits and remove HC Sync from Sleeping apps.",
                    modifier = Modifier.padding(vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
                if (BuildConfig.DEBUG) {
                    SettingsDivider()
                    SettingsRow(
                        title = "Test alarm",
                        subtitle = "Fires in one minute",
                        trailing = {
                            SettingsPillButton("Test", Periwinkle) {
                                AlarmScheduler.scheduleTestAlarm(context, 60_000L)
                                Toast.makeText(context, "Test alarm armed", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }

        item { SettingsSectionHeader("Sync status") }
        item {
            val current = status
            if (current == null) {
                LoadingLine("Reading sync status…", Periwinkle)
            } else {
                SettingsCard {
                    SettingsRow(
                        title = "Last sync",
                        subtitle = current.lastSyncSummary,
                        value = formatTime(current.lastSyncMs) ?: "Never",
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Workouts in the database",
                        subtitle = current.lastPullSummary,
                        value = "${current.workoutCount}",
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Next auto-sync",
                        value = formatTime(current.nextSyncMs) ?: "No schedule",
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Next workout refresh",
                        value = formatTime(current.nextPullMs) ?: "—",
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Last watch push",
                        value = formatTime(current.watchPushMs) ?: "Not yet",
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(top = 12.dp)) {
                SettingsPrimaryAction("Sync now", Periwinkle) {
                    SyncWorker.enqueue(context)
                    Toast.makeText(context, "Sync started", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showCalendarRange) {
        SettingsChoiceDialog(
            title = "Calendar history",
            options = CAL_RANGE_DAYS.zip(RANGE_LABELS).map { (days, label) ->
                SettingsChoice(days, label)
            },
            selected = calendarDays,
            accent = Teal,
            onSelect = {
                calendarDays = it
                Prefs.setSyncDaysBack(context, it)
            },
            onDismiss = { showCalendarRange = false },
        )
    }
    if (showJournalRange) {
        SettingsChoiceDialog(
            title = "Journal history",
            options = CAL_RANGE_DAYS.zip(RANGE_LABELS).map { (days, label) ->
                SettingsChoice(days, label)
            },
            selected = journalDays,
            accent = Teal,
            onSelect = {
                journalDays = it
                Prefs.setJournalSyncDaysBack(context, it)
            },
            onDismiss = { showJournalRange = false },
        )
    }
    if (showAddSchedule) {
        ScheduleDialog(
            onAdd = { schedule ->
                val updated = schedules + schedule
                schedules = updated
                Prefs.setSyncSchedules(context, updated)
                AlarmScheduler.scheduleNextSync(context)
                showAddSchedule = false
            },
            onDismiss = { showAddSchedule = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleDialog(
    onAdd: (SyncSchedule) -> Unit,
    onDismiss: () -> Unit,
) {
    var weekly by remember { mutableStateOf(false) }
    var hour by remember { mutableIntStateOf(8) }
    var minute by remember { mutableIntStateOf(0) }
    var days by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showTime by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardHigh,
        title = { Text("Add sync schedule", style = MaterialTheme.typography.headlineSmall, color = Snow) },
        text = {
            Column {
                SegmentedControl(
                    options = listOf("Daily", "Weekly"),
                    selected = if (weekly) "Weekly" else "Daily",
                    label = { it },
                    onSelect = {
                        weekly = it == "Weekly"
                        error = null
                    },
                    accent = Periwinkle,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Time", style = MaterialTheme.typography.titleSmall, color = Snow)
                        Text(
                            "%02d:%02d".format(hour, minute),
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate,
                        )
                    }
                    SettingsPillButton("Change", Periwinkle) { showTime = true }
                }
                if (weekly) {
                    Spacer(Modifier.height(14.dp))
                    Text("Days", style = MaterialTheme.typography.titleSmall, color = Snow)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                            .forEachIndexed { index, label ->
                                val day = index + 1
                                DayChip(
                                    label = label,
                                    selected = day in days,
                                    onClick = {
                                        days = if (day in days) days - day else days + day
                                        error = null
                                    },
                                )
                            }
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Danger)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (weekly && days.isEmpty()) {
                        error = "Select at least one day."
                    } else {
                        onAdd(
                            SyncSchedule(
                                type = if (weekly) "weekly" else "daily",
                                hour = hour,
                                minute = minute,
                                days = if (weekly) days else emptySet(),
                            )
                        )
                    }
                }
            ) { Text("Add", color = Periwinkle, style = MaterialTheme.typography.titleSmall) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Slate, style = MaterialTheme.typography.titleSmall)
            }
        },
    )

    if (showTime) {
        SettingsTimeDialog(
            initialHour = hour,
            initialMinute = minute,
            accent = Periwinkle,
            onConfirm = { h, m ->
                hour = h
                minute = m
                showTime = false
            },
            onDismiss = { showTime = false },
        )
    }
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Periwinkle.copy(alpha = 0.18f) else CardHigh)
            .border(
                1.dp,
                if (selected) Periwinkle.copy(alpha = 0.5f) else Hairline,
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Periwinkle else Mist,
    )
}

private fun formatTime(ms: Long?): String? {
    if (ms == null) return null
    return SimpleDateFormat("EEE, dd MMM HH:mm", Locale.getDefault()).format(Date(ms))
}
