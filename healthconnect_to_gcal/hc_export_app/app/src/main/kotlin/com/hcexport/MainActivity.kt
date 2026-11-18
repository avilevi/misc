package com.hcexport

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.hcexport.ui.DataRefresh
import com.hcexport.ui.HcApp
import com.hcexport.ui.theme.Danger
import com.hcexport.ui.theme.HcTheme
import com.hcexport.ui.theme.Success
import com.hcexport.ui.theme.Teal
import com.hcexport.ui.theme.Warning
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var statusTitle by mutableStateOf("Checking…")
    private var statusSubtitle by mutableStateOf("")
    private var statusAccent by mutableStateOf(Teal)
    private var syncing by mutableStateOf(false)
    private var permissionsReady by mutableStateOf(false)
    private var hcPermissionsLaunched = false

    private val hcPermissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
    )

    private val calendarPermissions = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* non-blocking: notification is optional */ }

    private val requestHcPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(hcPermissions)) {
            hcPermissionsLaunched = false
            checkAndSetup()
        } else {
            setStatus(
                Warning,
                "Permissions incomplete",
                "Allow all Health Connect permissions for HC Sync",
            )
        }
    }

    private val requestCalendarPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) checkAndSetup()
        else setStatus(Warning, "Calendar permission needed", "HC Sync needs calendar access")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            HcTheme {
                HcApp(
                    statusTitle = statusTitle,
                    statusSubtitle = statusSubtitle,
                    statusAccent = statusAccent,
                    syncing = syncing,
                    onSync = ::triggerSync,
                    onOpenSettings = {
                        startActivity(SettingsActivity.intent(this))
                    },
                    onOpenAiSettings = {
                        startActivity(SettingsActivity.intent(this, SettingsActivity.PAGE_AI))
                    },
                )
            }
        }
        checkAndSetup()
    }

    override fun onResume() {
        super.onResume()
        if (!hcPermissionsLaunched) checkAndSetup()
    }

    private fun checkAndSetup() {
        permissionsReady = false
        if (calendarPermissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) {
            setStatus(
                Warning,
                "Calendar permission needed",
                "Step 1 of 2 — grant calendar access",
            )
            requestCalendarPermissions.launch(calendarPermissions)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) {
            setStatus(
                Danger,
                "Health Connect unavailable",
                "Requires Android 9+ with Health Connect installed",
            )
            return
        }

        lifecycleScope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(this@MainActivity)
                val granted = client.permissionController.getGrantedPermissions()
                if (!granted.containsAll(hcPermissions)) {
                    if (!hcPermissionsLaunched) {
                        hcPermissionsLaunched = true
                        setStatus(
                            Warning,
                            "Health Connect permissions required",
                            "Step 2 of 2 — grant data access",
                        )
                        try {
                            requestHcPermissions.launch(hcPermissions)
                        } catch (_: Exception) {
                            hcPermissionsLaunched = false
                            setStatus(
                                Danger,
                                "Could not open permissions",
                                "Health Connect → App permissions → HC Sync, then refresh",
                            )
                        }
                    }
                    return@launch
                }
            } catch (e: Exception) {
                setStatus(
                    Danger,
                    "Health Connect error",
                    e.message ?: "Unknown error",
                )
                return@launch
            }

            val calId = Prefs.getCalendarId(this@MainActivity)
                ?: CalendarHelper.findCalendarId(this@MainActivity)
            if (calId == null) {
                setStatus(
                    Warning,
                    "No calendar found",
                    "Make sure a Google account is set up on this device",
                )
                return@launch
            }

            if (Prefs.getCalendarId(this@MainActivity) == null)
                Prefs.setCalendarId(this@MainActivity, calId)

            ensureArmedAndCatchUp()

            val lastSync = Prefs.getLastSyncTime(this@MainActivity)
            val lastLine = if (lastSync == null) "No sync yet" else "Last sync ${relativeTime(lastSync)}"
            val exact = AlarmScheduler.canScheduleExact(this@MainActivity)
            val subtitle = if (exact) lastLine else "$lastLine  ·  exact alarms off"
            permissionsReady = true
            setStatus(if (exact) Success else Warning, "Ready", subtitle)
        }
    }

    /** Re-arm the alarms and make up a run that was missed while the app was closed. */
    private fun ensureArmedAndCatchUp() {
        AlarmScheduler.ensureArmed(this)
        WorkManager.getInstance(this).cancelAllWorkByTag("scheduled_sync")

        val now = System.currentTimeMillis()
        val mostRecent = AlarmScheduler.mostRecentScheduleMs(this)
        val lastSync = Prefs.getLastSyncTime(this)
        val lastCatchup = Prefs.getLastCatchupMs(this)
        val catchupDue = mostRecent != null &&
            (lastSync == null || lastSync < mostRecent) &&
            now - lastCatchup > 60 * 60 * 1000L
        val pullDue = (Prefs.getLastWorkoutPullMs(this) ?: 0L) < now - 24 * 60 * 60 * 1000L

        if (catchupDue) {
            Prefs.setLastCatchupMs(this, now)
            SyncWorker.enqueue(this)
        } else if (pullDue) {
            WorkoutFetchWorker.enqueue(this)
        }
    }

    private fun triggerSync() {
        if (!permissionsReady) {
            // A tap on Sync is also the natural retry for a permission/setup problem.
            hcPermissionsLaunched = false
            checkAndSetup()
            return
        }
        syncing = true
        setStatus(Teal, "Syncing…", "Workouts → calendar → journal → watch")
        val id = SyncWorker.enqueue(this)
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(id).observe(this) { info ->
            when (info?.state) {
                WorkInfo.State.SUCCEEDED -> {
                    syncing = false
                    DataRefresh.bump()
                    WodWidgetProvider.notifyDataChanged(this)
                    val last = Prefs.getLastSyncTime(this)
                    setStatus(
                        Success,
                        "Sync complete",
                        if (last != null) "Last sync ${relativeTime(last)}" else "Done",
                    )
                }
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                    syncing = false
                    setStatus(Danger, "Sync failed", "Settings → Diagnostics → Sync log")
                }
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                    syncing = true
                }
                else -> Unit
            }
        }
    }

    private fun relativeTime(ms: Long): String {
        val diff = System.currentTimeMillis() - ms
        val minutes = diff / 60_000L
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 24 * 60 -> "${minutes / 60} h ago"
            else -> SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(ms))
        }
    }

    private fun setStatus(color: Color, title: String, detail: String) {
        statusAccent = color
        statusTitle = title
        statusSubtitle = detail
    }
}
