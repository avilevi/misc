package com.hcexport.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hcexport.Backup
import com.hcexport.ui.ConfirmDialog
import com.hcexport.ui.DataRefresh
import com.hcexport.ui.theme.CardHigh
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Snow
import com.hcexport.ui.theme.Teal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manual backup and restore, the no-server way: one zip through the system
 * file picker, which can save to Google Drive just as well as to the phone.
 */
@Composable
fun BackupSettingsPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<Uri?>(null) }
    var chosenInfo by remember { mutableStateOf<Backup.Info?>(null) }
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        exporting = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { Backup.export(context, uri) }
            }.onSuccess { info ->
                message = "Backup saved" to
                    "${info.databases} databases and ${info.prefFiles} settings files, " +
                    "from v${info.versionName} (${info.createdAt})."
            }.onFailure {
                message = "Backup failed" to (it.message ?: "Could not write the backup.")
            }
            exporting = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val info = runCatching {
                withContext(Dispatchers.IO) { Backup.inspect(context, uri) }
            }.getOrNull()
            if (info == null) {
                message = "Not a backup" to "That file is not an HC Sync backup."
            } else {
                chosen = uri
                chosenInfo = info
            }
        }
    }

    SettingsPageList {
        item {
            SettingsNote(
                "One file with everything this app owns: workouts, journal entries and notes, " +
                    "records, AI plans and values, the schedule and every setting. Save it to " +
                    "Google Drive and restore it on the next phone — or in the release build."
            )
        }

        item { SettingsSectionHeader("Backup") }
        item {
            SettingsCard {
                SettingsRow(
                    title = "Back up now",
                    subtitle = "Choose where the file goes — Google Drive works",
                    onClick = {
                        if (!exporting && !restoring) {
                            exportLauncher.launch(Backup.defaultFileName())
                        }
                    },
                    trailing = {
                        if (exporting) {
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

        item { SettingsSectionHeader("Restore") }
        item {
            SettingsCard {
                SettingsRow(
                    title = "Restore from a file",
                    subtitle = "Replaces this app's data and settings",
                    onClick = {
                        if (!exporting && !restoring) {
                            importLauncher.launch(
                                arrayOf("application/zip", "application/octet-stream"),
                            )
                        }
                    },
                    trailing = {
                        if (restoring) {
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

        item {
            SettingsNote(
                "Not included: AI API keys and the Fitr / BridgeAthletic logins. They are " +
                    "encrypted to this phone and are entered again after a restore. Health " +
                    "Connect data stays in Health Connect, and calendar events stay in Google " +
                    "Calendar."
            )
        }
    }

    val info = chosenInfo
    val uri = chosen
    if (info != null && uri != null) {
        ConfirmDialog(
            title = "Restore this backup?",
            message = "From v${info.versionName}, ${info.createdAt} " +
                "(${info.databases} databases, ${info.prefFiles} settings files).\n\n" +
                "Everything in this app — workouts, journal entries, records, AI plans and " +
                "settings — will be replaced. This cannot be undone.",
            confirmText = "Restore",
            destructive = true,
            onConfirm = {
                chosen = null
                chosenInfo = null
                restoring = true
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { Backup.restore(context, uri) }
                    }.onSuccess {
                        DataRefresh.bump()
                        message = "Restore complete" to
                            "Close and reopen HC Sync to load the restored data."
                    }.onFailure {
                        message = "Restore failed" to (it.message ?: "Could not read the backup.")
                    }
                    restoring = false
                }
            },
            onDismiss = {
                chosen = null
                chosenInfo = null
            },
        )
    }

    message?.let { (title, text) ->
        AlertDialog(
            onDismissRequest = { message = null },
            containerColor = CardHigh,
            title = { Text(title, style = MaterialTheme.typography.headlineSmall, color = Snow) },
            text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = Mist) },
            confirmButton = {
                TextButton(onClick = { message = null }) {
                    Text("OK", color = Teal)
                }
            },
        )
    }
}
