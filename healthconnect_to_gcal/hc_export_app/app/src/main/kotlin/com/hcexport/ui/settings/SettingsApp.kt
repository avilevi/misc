package com.hcexport.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hcexport.BuildConfig
import com.hcexport.R
import com.hcexport.ui.HcBackground
import com.hcexport.ui.HcCard
import com.hcexport.ui.IconAction
import com.hcexport.ui.PrimaryButton
import com.hcexport.ui.ScreenPadding
import com.hcexport.ui.SecondaryButton
import com.hcexport.ui.theme.Abyss
import com.hcexport.ui.theme.Amber
import com.hcexport.ui.theme.Card
import com.hcexport.ui.theme.CardHigh
import com.hcexport.ui.theme.Hairline
import com.hcexport.ui.theme.HairlineSoft
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Periwinkle
import com.hcexport.ui.theme.Slate
import com.hcexport.ui.theme.Snow
import com.hcexport.ui.theme.Teal
import kotlinx.coroutines.launch

enum class SettingsPage(
    val key: String,
    val title: String,
    val subtitle: String,
    val accent: Color,
) {
    CALENDAR("calendar", "Calendar", "Target and calendar actions", Teal),
    SYNC("sync", "Sync & reliability", "Schedules, ranges and background work", Periwinkle),
    WORKOUTS("workouts", "Workouts & watch", "Calendar default and watch snapshot", Periwinkle),
    AI("ai", "AI workout", "Provider, key, model and interactive plans", Teal),
    DIAGNOSTICS("diagnostics", "Diagnostics", "Sync log, updates and data", Amber),
    ABOUT("about", "About", "Version, privacy and credits", Mist);

    companion object {
        fun fromKey(key: String?): SettingsPage =
            entries.firstOrNull { it.key == key } ?: CALENDAR
    }
}

@Composable
fun SettingsApp(startPage: String?) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.fromKey(startPage)) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Card,
                drawerContentColor = Snow,
                drawerShape = RoundedCornerShape(0.dp, 28.dp, 28.dp, 0.dp),
            ) {
                SettingsDrawer(
                    current = page,
                    onSelect = {
                        page = it
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        HcBackground(accent = page.accent) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                SettingsTopBar(page) {
                    scope.launch { drawerState.open() }
                }
                when (page) {
                    SettingsPage.CALENDAR -> CalendarSettingsPage()
                    SettingsPage.SYNC -> SyncSettingsPage()
                    SettingsPage.WORKOUTS -> WorkoutSettingsPage()
                    SettingsPage.AI -> AiSettingsPage()
                    SettingsPage.DIAGNOSTICS -> DiagnosticsSettingsPage()
                    SettingsPage.ABOUT -> AboutSettingsPage()
                }
            }
        }
    }
}

@Composable
private fun SettingsTopBar(page: SettingsPage, onMenu: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(
            icon = {
                Icon(
                    Icons.Outlined.Menu,
                    contentDescription = "Settings menu",
                    tint = Snow,
                    modifier = Modifier.size(20.dp),
                )
            },
            onClick = onMenu,
            background = CardHigh.copy(alpha = 0.7f),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "SETTINGS",
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
            )
            Text(
                page.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Snow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SettingsPageIcon(page, page.accent.copy(alpha = 0.9f), 22.dp)
    }
}

@Composable
private fun SettingsDrawer(current: SettingsPage, onSelect: (SettingsPage) -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 14.dp, vertical = 20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Teal, Teal.copy(alpha = 0.55f)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "HC",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Abyss,
                )
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text("HC Sync", style = MaterialTheme.typography.titleLarge, color = Snow)
                Text("Settings", style = MaterialTheme.typography.bodySmall, color = Slate)
            }
        }
        Spacer(Modifier.height(24.dp))
        SettingsPage.entries.forEach { page ->
            val selected = page == current
            val shape = RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(shape)
                    .background(if (selected) page.accent.copy(alpha = 0.13f) else Color.Transparent)
                    .then(
                        if (selected) Modifier.border(1.dp, page.accent.copy(alpha = 0.25f), shape)
                        else Modifier
                    )
                    .clickable { onSelect(page) }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(page.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    SettingsPageIcon(page, page.accent, 19.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        page.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) Snow else Mist,
                    )
                    Text(
                        page.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = page.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "v${BuildConfig.VERSION_NAME} · local-first",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Slate,
        )
    }
}

@Composable
fun SettingsPageIcon(page: SettingsPage, tint: Color, size: androidx.compose.ui.unit.Dp) {
    when (page) {
        SettingsPage.CALENDAR -> Icon(painterResource(R.drawable.ic_calendar), null, tint = tint, modifier = Modifier.size(size))
        SettingsPage.SYNC -> Icon(Icons.Outlined.Refresh, null, tint = tint, modifier = Modifier.size(size))
        SettingsPage.WORKOUTS -> Icon(painterResource(R.drawable.ic_dumbbell), null, tint = tint, modifier = Modifier.size(size))
        SettingsPage.AI -> Icon(painterResource(R.drawable.ic_ai), null, tint = tint, modifier = Modifier.size(size))
        SettingsPage.DIAGNOSTICS -> Icon(Icons.Outlined.Build, null, tint = tint, modifier = Modifier.size(size))
        SettingsPage.ABOUT -> Icon(Icons.Outlined.Info, null, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
fun SettingsPageList(content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ScreenPadding,
            end = ScreenPadding,
            top = 2.dp,
            bottom = 36.dp,
        ),
        content = content,
    )
}

@Composable
fun SettingsSectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Slate,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    HcCard(
        modifier = modifier.fillMaxWidth(),
        accent = accent,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
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
        if (value != null) {
            Text(
                value,
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Mist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        } else if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Slate,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineSoft))
}

@Composable
fun SettingsPillButton(
    text: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) color.copy(alpha = 0.13f) else CardHigh)
            .border(
                1.dp,
                if (enabled) color.copy(alpha = 0.28f) else Hairline,
                RoundedCornerShape(50),
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) color else Slate,
    )
}

@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier, color: Color = Slate) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

@Composable
fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Snow),
        label = { Text(label, style = MaterialTheme.typography.bodySmall, color = Slate) },
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = Hairline,
            focusedContainerColor = CardHigh,
            unfocusedContainerColor = CardHigh,
            cursorColor = accent,
            focusedLabelColor = accent,
            unfocusedLabelColor = Slate,
            focusedTextColor = Snow,
            unfocusedTextColor = Snow,
        ),
    )
}

data class SettingsChoice<T>(val value: T, val label: String, val subtitle: String? = null)

@Composable
fun <T> SettingsChoiceDialog(
    title: String,
    options: List<SettingsChoice<T>>,
    selected: T?,
    accent: Color,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardHigh,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, color = Snow) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options) { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelect(option.value)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(option.label, style = MaterialTheme.typography.titleSmall, color = Snow)
                            if (!option.subtitle.isNullOrBlank()) {
                                Text(option.subtitle, style = MaterialTheme.typography.bodySmall, color = Slate)
                            }
                        }
                        if (option.value == selected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Slate, style = MaterialTheme.typography.titleSmall)
            }
        },
    )
}

@Composable
fun SettingsTextDialog(
    title: String,
    label: String,
    initial: String,
    accent: Color,
    confirmText: String = "Save",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardHigh,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, color = Snow) },
        text = {
            SettingsTextField(
                value = value,
                onValueChange = { value = it },
                label = label,
                accent = accent,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }) {
                Text(confirmText, color = accent, style = MaterialTheme.typography.titleSmall)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Slate, style = MaterialTheme.typography.titleSmall)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTimeDialog(
    initialHour: Int,
    initialMinute: Int,
    accent: Color,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
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
                "Sync time",
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
                SettingsPillButton("Cancel", Slate, onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                SettingsPillButton("Set", accent) {
                    onConfirm(state.hour, state.minute)
                }
            }
        }
    }
}

@Composable
fun SettingsPrimaryAction(
    text: String,
    accent: Color,
    onClick: () -> Unit,
) {
    PrimaryButton(text, onClick = onClick, accent = accent)
}

@Composable
fun SettingsSecondaryAction(
    text: String,
    accent: Color,
    onClick: () -> Unit,
) {
    SecondaryButton(text, onClick = onClick, accent = accent)
}
