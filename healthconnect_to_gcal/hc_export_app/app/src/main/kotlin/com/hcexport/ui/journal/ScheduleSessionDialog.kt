package com.hcexport.ui.journal

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hcexport.CalendarAccount
import com.hcexport.CalendarHelper
import com.hcexport.JournalHiddenStore
import com.hcexport.JournalKeys
import com.hcexport.Prefs
import com.hcexport.RecordLogic
import com.hcexport.ScheduledSession
import com.hcexport.ScheduledSessionStore
import com.hcexport.SettingsBackend
import com.hcexport.Workout
import com.hcexport.WorkoutText
import com.hcexport.ui.LoadingLine
import com.hcexport.ui.LocalMinimalDesign
import com.hcexport.ui.PrimaryButton
import com.hcexport.ui.settings.SettingsChoice
import com.hcexport.ui.settings.SettingsChoiceDialog
import com.hcexport.ui.settings.SettingsToggleRow
import com.hcexport.ui.theme.Card
import com.hcexport.ui.theme.CardHigh
import com.hcexport.ui.theme.Hairline
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Slate
import com.hcexport.ui.theme.Snow
import com.hcexport.ui.theme.Teal
import com.hcexport.ui.theme.workoutAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val SESSION_MINUTES = 60L
private val SessionMs = SESSION_MINUTES * 60_000L

/**
 * Turn one of the day's programmed workouts into a journal entry.
 *
 * The entry itself is local: the app records the schedule, so the workout is
 * in the journal before it has a calendar event, a watch session or an AI
 * plan. Adding a Google Calendar event is optional and picks its calendar.
 *
 * The scheduled window gives the entry its planned time. It does not by
 * itself make a watch session merge into the workout — only a calendar event
 * naming the workout accounts for a recording, so the journal's long-standing
 * rule is unchanged.
 */
@Composable
fun ScheduleSessionDialog(
    date: String,
    workouts: List<Workout>,
    existingTypeKeys: Set<String>,
    suggestedStartMs: Long,
    /** Open on this workout instead of asking which one. */
    preselectedTypeKey: String? = null,
    loading: Boolean,
    onDismiss: () -> Unit,
    onScheduled: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val available = remember(workouts, existingTypeKeys) {
        workouts
            .filter { it.typeKey !in existingTypeKeys }
            .sortedWith(
                compareBy(
                    { it.sortOrder },
                    { WorkoutText.displayType(it.typeLabel).lowercase() },
                )
            )
    }

    var selected by remember {
        mutableStateOf(workouts.firstOrNull { it.typeKey == preselectedTypeKey })
    }
    var calendars by remember { mutableStateOf<List<CalendarAccount>?>(null) }
    var calendarId by remember { mutableStateOf<Long?>(null) }
    var addToCalendar by remember { mutableStateOf(true) }
    var showCalendarPicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var startMs by remember(date, suggestedStartMs) { mutableStateOf(suggestedStartMs) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { SettingsBackend.loadGoogleCalendars(context) }
                .getOrDefault(emptyList())
        }
        calendars = loaded
        val preferred = Prefs.getScheduledCalendarId(context) ?: Prefs.getCalendarId(context)
        calendarId = loaded.firstOrNull { it.id == preferred }?.id
            ?: loaded.firstOrNull()?.id
        if (loaded.isEmpty()) addToCalendar = false
    }

    val chosenCalendar = calendars?.firstOrNull { it.id == calendarId }

    fun schedule() {
        val workout = selected ?: return
        if (saving) return
        saving = true
        scope.launch {
            val calendar = if (addToCalendar) chosenCalendar else null
            val endMs = startMs + SessionMs
            var eventId: Long? = null
            var calendarFailed = false

            if (calendar != null) {
                eventId = withContext(Dispatchers.IO) {
                    runCatching {
                        CalendarHelper.insertEvent(
                            context = context,
                            calendarId = calendar.id,
                            title = WorkoutText.displayType(workout.typeLabel),
                            description = workout.content,
                            startMs = startMs,
                            endMs = endMs,
                        )
                    }.getOrNull()
                }
                if (eventId == null) {
                    calendarFailed = true
                } else {
                    Prefs.setScheduledCalendarId(context, calendar.id)
                }
            }

            withContext(Dispatchers.IO) {
                ScheduledSessionStore.put(
                    context,
                    ScheduledSession(
                        date = date,
                        typeKey = workout.typeKey,
                        startMs = startMs,
                        endMs = endMs,
                        calendarId = calendar?.id,
                        eventId = eventId,
                        createdAtMs = System.currentTimeMillis(),
                    ),
                )
                // Scheduling a workout brings it back if it was deleted before.
                JournalHiddenStore.unhide(context, JournalKeys.workout(date, workout.typeKey))
            }

            saving = false
            val name = WorkoutText.displayType(workout.typeLabel)
            val message = when {
                calendar != null && !calendarFailed ->
                    "$name scheduled and added to ${calendar.displayName}."
                calendarFailed ->
                    "$name added to the journal; the calendar event could not be created."
                else -> "$name added to the journal."
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onScheduled()
            onDismiss()
        }
    }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(CardHigh)
                .border(1.dp, Hairline, RoundedCornerShape(28.dp))
                .padding(20.dp)
        ) {
            Text(
                "SCHEDULE",
                style = MaterialTheme.typography.labelSmall,
                color = Teal,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                RecordLogic.formatDate(date),
                style = MaterialTheme.typography.headlineSmall,
                color = Snow,
            )
            if (!LocalMinimalDesign.current) {
                Text(
                    if (selected == null) {
                        "Choose one of the day's workouts."
                    } else {
                        "Set how it reaches your journal."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
            }
            Spacer(Modifier.height(14.dp))

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val choice = selected
                if (choice == null) {
                    WorkoutChoices(
                        workouts = workouts,
                        available = available,
                        loading = loading,
                        onPick = { selected = it },
                    )
                } else {
                    ScheduleOptions(
                        workout = choice,
                        calendars = calendars,
                        addToCalendar = addToCalendar,
                        calendarAccount = chosenCalendar,
                        startMs = startMs,
                        onChangeWorkout = { selected = null },
                        onToggleCalendar = { addToCalendar = it },
                        onPickCalendar = { showCalendarPicker = true },
                        onPickTime = { showTimePicker = true },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            if (selected == null) {
                TextButton(
                    onClick = { if (!saving) onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel", color = Slate, style = MaterialTheme.typography.titleSmall)
                }
            } else {
                PrimaryButton(
                    text = if (addToCalendar && chosenCalendar != null) {
                        "Schedule & add to calendar"
                    } else {
                        "Add to journal"
                    },
                    onClick = { schedule() },
                    accent = Teal,
                    enabled = !saving,
                )
                Spacer(Modifier.height(2.dp))
                TextButton(
                    onClick = { if (!saving) onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel", color = Slate, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }

    if (showCalendarPicker) {
        val options = calendars.orEmpty()
        if (options.isNotEmpty()) {
            SettingsChoiceDialog(
                title = "Calendar",
                options = options.map {
                    SettingsChoice(it.id, it.displayName, it.accountName)
                },
                selected = calendarId,
                accent = Teal,
                onSelect = { calendarId = it },
                onDismiss = { showCalendarPicker = false },
            )
        }
    }

    if (showTimePicker) {
        SessionTimeDialog(
            date = date,
            startMs = startMs,
            onConfirm = {
                startMs = it
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun WorkoutChoices(
    workouts: List<Workout>,
    available: List<Workout>,
    loading: Boolean,
    onPick: (Workout) -> Unit,
) {
    when {
        loading -> LoadingLine("Reading the day's workouts…", Teal)

        available.isEmpty() -> {
            Text(
                if (workouts.isEmpty()) {
                    "No workouts are stored for this day. Run a sync, then try again."
                } else {
                    "Every workout for this day is already in the journal."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Mist,
            )
        }

        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            available.forEach { workout ->
                WorkoutChoiceRow(workout, onPick = { onPick(workout) })
            }
        }
    }
}

@Composable
private fun WorkoutChoiceRow(workout: Workout, onPick: () -> Unit) {
    val accent = workoutAccent(workout.typeKey)
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Card)
            .border(1.dp, Hairline, shape)
            .clickable(onClick = onPick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(50))
                .background(accent)
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                WorkoutText.displayType(workout.typeLabel),
                style = MaterialTheme.typography.titleMedium,
                color = Snow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                WorkoutText.summary(workout.content),
                style = MaterialTheme.typography.bodySmall,
                color = Mist,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Slate,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ScheduleOptions(
    workout: Workout,
    calendars: List<CalendarAccount>?,
    addToCalendar: Boolean,
    calendarAccount: CalendarAccount?,
    startMs: Long,
    onChangeWorkout: () -> Unit,
    onToggleCalendar: (Boolean) -> Unit,
    onPickCalendar: () -> Unit,
    onPickTime: () -> Unit,
) {
    val accent = workoutAccent(workout.typeKey)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(accent.copy(alpha = 0.10f))
                .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SELECTED",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
                Text(
                    "Change",
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onChangeWorkout)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                )
            }
            Text(
                WorkoutText.displayType(workout.typeLabel),
                style = MaterialTheme.typography.titleLarge,
                color = Snow,
            )
            Text(
                WorkoutText.summary(workout.content),
                style = MaterialTheme.typography.bodySmall,
                color = Mist,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Card)
                .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp)
        ) {
            SettingsToggleRow(
                title = "Add to Google Calendar",
                subtitle = when {
                    calendars == null -> "Reading your calendars…"
                    calendars.isEmpty() -> "No Google calendars found on this device."
                    else -> "Creates an event with the workout in its description."
                },
                checked = addToCalendar && !calendars.isNullOrEmpty(),
                accent = Teal,
                enabled = !calendars.isNullOrEmpty(),
                onCheckedChange = onToggleCalendar,
            )
        }

        if (addToCalendar && !calendars.isNullOrEmpty()) {
            OptionRow(
                label = "CALENDAR",
                value = calendarAccount?.displayName ?: "Choose a calendar",
                subtitle = calendarAccount?.accountName,
                onClick = onPickCalendar,
            )
            OptionRow(
                label = "TIME",
                value = "${clockTime(startMs)} – ${clockTime(startMs + SessionMs)}",
                subtitle = "One hour, on this day",
                onClick = onPickTime,
            )
        }

        if (!LocalMinimalDesign.current) {
            Text(
                "The journal entry is added even without a calendar event.",
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
            )
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    value: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Card)
            .border(1.dp, Hairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                color = Snow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Slate,
            modifier = Modifier.size(18.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTimeDialog(
    date: String,
    startMs: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val local = remember(startMs) {
        Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).toLocalTime()
    }
    val state = rememberTimePickerState(
        initialHour = local.hour,
        initialMinute = local.minute,
        is24Hour = true,
    )
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(CardHigh)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Session time",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                color = Snow,
            )
            Spacer(Modifier.height(12.dp))
            TimePicker(state = state)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Slate, style = MaterialTheme.typography.titleSmall)
                }
                TextButton(
                    onClick = {
                        val millis = LocalDate.parse(date)
                            .atTime(state.hour, state.minute)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                        onConfirm(millis)
                    },
                ) {
                    Text("Set", color = Teal, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
