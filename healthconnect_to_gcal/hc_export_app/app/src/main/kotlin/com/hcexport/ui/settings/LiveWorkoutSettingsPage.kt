package com.hcexport.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hcexport.Prefs
import com.hcexport.ui.SegmentedControl
import com.hcexport.ui.theme.Abyss
import com.hcexport.ui.theme.CardLift
import com.hcexport.ui.theme.Coral
import com.hcexport.ui.theme.Hairline
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Slate
import com.hcexport.ui.theme.Snow

/**
 * Settings for the live workout: how long the get-ready countdown runs, the
 * fallback window between strength sets, and the cues during a session.
 *
 * The workout's own words win — "Every 1:30" or "Time cap: 14:00" is used
 * whenever the plan states it. These settings only fill the gaps.
 */
@Composable
fun LiveWorkoutSettingsPage() {
    val context = LocalContext.current
    var leadIn by remember { mutableIntStateOf(Prefs.getLiveLeadInSeconds(context)) }
    var interval by remember { mutableIntStateOf(Prefs.getLiveSetIntervalSeconds(context)) }
    var sound by remember { mutableStateOf(Prefs.getLiveSound(context)) }
    var vibration by remember { mutableStateOf(Prefs.getLiveVibration(context)) }
    var keepOn by remember { mutableStateOf(Prefs.getLiveKeepScreenOn(context)) }

    SettingsPageList {
        item {
            SettingsNote(
                "Live workout plays the AI plan one set or movement at a time. " +
                    "Any timing the workout states — every 1:30, a time cap, an AMRAP — " +
                    "is used automatically."
            )
        }

        item { SettingsSectionHeader("Get ready", "The countdown after you tap Start.") }
        item {
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                SegmentedControl(
                    options = listOf(5, 10, 15, 20),
                    selected = leadIn.coerceIn(5, 20),
                    label = { "$it s" },
                    onSelect = {
                        leadIn = it
                        Prefs.setLiveLeadInSeconds(context, it)
                    },
                    accent = Coral,
                )
                Spacer(Modifier.height(8.dp))
                SettingsNote("Ten seconds gives you time to chalk up and set your feet.")
            }
        }

        item {
            SettingsSectionHeader(
                "Strength sets",
                "Used only when the workout does not say how often a set starts.",
            )
        }
        item {
            SettingsCard(contentPadding = PaddingValues(16.dp)) {
                SegmentedControl(
                    options = listOf(60, 90, 120, 180),
                    selected = interval,
                    label = { seconds ->
                        val minutes = seconds / 60
                        val rest = seconds % 60
                        if (rest == 0) "$minutes:00" else "$minutes:${rest.toString().padStart(2, '0')}"
                    },
                    onSelect = {
                        interval = it
                        Prefs.setLiveSetIntervalSeconds(context, it)
                    },
                    accent = Coral,
                )
                Spacer(Modifier.height(8.dp))
                SettingsNote(
                    "Each set gets its own window and the next set starts when it ends. " +
                        "Pause any time, or tap Next set to move on early."
                )
            }
        }

        item { SettingsSectionHeader("During the workout") }
        item {
            SettingsCard {
                SettingsToggleRow(
                    title = "Sound cues",
                    subtitle = "A beep for the last three seconds and the end of a timer.",
                    checked = sound,
                    accent = Coral,
                    onCheckedChange = {
                        sound = it
                        Prefs.setLiveSound(context, it)
                    },
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = "Vibration",
                    subtitle = "A short buzz to match the countdown, without looking at the screen.",
                    checked = vibration,
                    accent = Coral,
                    onCheckedChange = {
                        vibration = it
                        Prefs.setLiveVibration(context, it)
                    },
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = "Keep the screen on",
                    subtitle = "The display stays awake while a live workout is open.",
                    checked = keepOn,
                    accent = Coral,
                    onCheckedChange = {
                        keepOn = it
                        Prefs.setLiveKeepScreenOn(context, it)
                    },
                )
            }
        }

        item {
            SettingsNote(
                "Ending a live workout early keeps every value you entered and marks the " +
                    "last set you reached, so the journal never counts work you did not do."
            )
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    accent: Color,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Snow)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Abyss,
                checkedTrackColor = accent,
                checkedBorderColor = accent,
                uncheckedThumbColor = Mist,
                uncheckedTrackColor = CardLift,
                uncheckedBorderColor = Hairline,
            ),
        )
    }
}
