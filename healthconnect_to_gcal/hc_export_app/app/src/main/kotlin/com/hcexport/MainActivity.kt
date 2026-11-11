package com.hcexport

import android.app.AlertDialog
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var statusDot: View
    private lateinit var statusLabel: TextView
    private lateinit var statusDetail: TextView
    private lateinit var statusCard: LinearLayout
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
            setStatus(Ui.WARNING, "Permissions incomplete",
                "Open Health Connect → App permissions → HC Sync, and allow all permissions. Then tap Refresh.")
        }
    }

    private val requestCalendarPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) checkAndSetup()
        else setStatus(Ui.WARNING, "Calendar permission needed",
            "HC Sync needs calendar access to create events.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 60), Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 24))
        }

        // ── Header ───────────────────────────────────────────────────────────

        TextView(this).apply {
            text = "HC Sync"
            textSize = 28f
            setTextColor(Ui.PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, Ui.dp(this@MainActivity, 2))
        }.also { root.addView(it) }

        TextView(this).apply {
            text = "Health Connect  →  Calendar"
            textSize = 13f
            setTextColor(Ui.TEXT_MUTED)
            setPadding(0, 0, 0, Ui.dp(this@MainActivity, 28))
        }.also { root.addView(it) }

        // ── Status card ──────────────────────────────────────────────────────

        statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 20))
            background = Ui.cardBg(Ui.dpf(this@MainActivity, 16))
        }

        // Status row: dot + label
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusDot = View(this).apply {
            val s = Ui.dp(this@MainActivity, 10)
            layoutParams = LinearLayout.LayoutParams(s, s).also {
                it.setMargins(0, 0, Ui.dp(this@MainActivity, 10), 0)
            }
            background = Ui.dotBg(Ui.TEXT_MUTED, s)
        }
        statusRow.addView(statusDot)
        statusLabel = TextView(this).apply {
            text = "Checking…"
            textSize = 16f
            setTextColor(Ui.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
        }
        statusRow.addView(statusLabel)
        statusCard.addView(statusRow)

        statusDetail = TextView(this).apply {
            textSize = 13f
            setTextColor(Ui.TEXT_SECONDARY)
            setPadding(0, Ui.dp(this@MainActivity, 8), 0, 0)
            visibility = View.GONE
        }
        statusCard.addView(statusDetail)
        root.addView(statusCard)

        root.addView(Ui.sectionSpacer(this, 20))

        // ── Sync button ──────────────────────────────────────────────────────

        root.addView(Ui.primaryButton(this, "Sync now") { triggerSync() })

        root.addView(Ui.sectionSpacer(this, 24))

        // ── Secondary button grid ────────────────────────────────────────────

        // Journal — prominent full-width secondary
        root.addView(Ui.secondaryButton(this, "Journal") {
            startActivity(Intent(this, JournalActivity::class.java))
        })

        root.addView(Ui.sectionSpacer(this, 10))

        val buttonRows = listOf(
            listOf("View Log" to { showLog() }, "Settings" to {
                startActivity(Intent(this, SettingsActivity::class.java))
            }),
            listOf("Refresh Permissions" to { hcPermissionsLaunched = false; checkAndSetup() },
                   "Check for Updates" to { checkForUpdates() }),
        )

        for (row in buttonRows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, Ui.dp(this@MainActivity, 10))
            }
            for ((label, action) in row) {
                val btn = Ui.secondaryButton(this, label) { action() }
                val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (row.indexOf(label to action) == 0) {
                    params.setMargins(0, 0, Ui.dp(this@MainActivity, 6), 0)
                } else {
                    params.setMargins(Ui.dp(this@MainActivity, 6), 0, 0, 0)
                }
                btn.layoutParams = params
                rowLayout.addView(btn)
            }
            root.addView(rowLayout)
        }

        setContentView(ScrollView(this).apply {
            addView(root)
            setBackgroundColor(Ui.BG)
        })

        checkAndSetup()
    }

    override fun onResume() {
        super.onResume()
        if (!hcPermissionsLaunched) checkAndSetup()
    }

    private fun checkAndSetup() {
        if (calendarPermissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) {
            setStatus(Ui.WARNING, "Calendar permission needed",
                "Step 1 of 2 — grant calendar access.")
            requestCalendarPermissions.launch(calendarPermissions)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) {
            setStatus(Ui.ERROR, "Health Connect unavailable",
                "Requires Android 9+ with Health Connect installed.")
            return
        }

        lifecycleScope.launch {
            try {
                val client  = HealthConnectClient.getOrCreate(this@MainActivity)
                val granted = client.permissionController.getGrantedPermissions()
                if (!granted.containsAll(hcPermissions)) {
                    if (!hcPermissionsLaunched) {
                        hcPermissionsLaunched = true
                        setStatus(Ui.WARNING, "Health Connect permissions required",
                            "Step 2 of 2 — grant data access.")
                        try {
                            requestHcPermissions.launch(hcPermissions)
                        } catch (e: Exception) {
                            hcPermissionsLaunched = false
                            setStatus(Ui.ERROR, "Could not open permissions",
                                "Open Health Connect → App permissions → HC Sync → allow all. Then tap Refresh.")
                        }
                    }
                    return@launch
                }
            } catch (e: Exception) {
                setStatus(Ui.ERROR, "Health Connect error",
                    e.message ?: "Unknown error")
                return@launch
            }

            val calId = Prefs.getCalendarId(this@MainActivity)
                ?: CalendarHelper.findCalendarId(this@MainActivity)
            if (calId == null) {
                setStatus(Ui.WARNING, "No calendar found",
                    "Make sure a Google account is set up on this device.")
            } else {
                if (Prefs.getCalendarId(this@MainActivity) == null)
                    Prefs.setCalendarId(this@MainActivity, calId)
                val calName    = resolveCalendarName(calId)
                val schedCount = Prefs.getSyncSchedules(this@MainActivity).size
                val schedInfo  = if (schedCount == 0) "No auto-sync scheduled"
                                 else "$schedCount auto-sync schedule(s) active"
                val calSummary  = Prefs.getLastSyncSummary(this@MainActivity)
                val pullSummary = Prefs.getLastWorkoutPullSummary(this@MainActivity)
                val nextLine    = nextSyncLine()
                val batteryLine = BatteryOptimizationHelper.statusLine(this@MainActivity)
                val exactLine = if (AlarmScheduler.canScheduleExact(this@MainActivity))
                    "Exact alarms allowed."
                else
                    "Exact alarms off — schedules may drift (Settings → Allow exact alarms)."
                ensureArmedAndCatchUp()
                val detail = buildString {
                    append("Calendar: $calName")
                    append("\n$schedInfo")
                    append("\n$batteryLine")
                    append("\n$exactLine")
                    if (calSummary != null) append("\n$calSummary")
                    if (pullSummary != null) append("\n$pullSummary")
                    if (nextLine.isNotEmpty()) append(nextLine)
                }
                setStatus(Ui.SUCCESS, "Ready", detail)
            }
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

    private fun nextSyncLine(): String {
        val fmt = SimpleDateFormat("EEE, dd MMM HH:mm", Locale.getDefault())
        val sync = AlarmScheduler.nextSyncMs(this)
        val pull = Prefs.getNextPullAlarmMs(this) ?: AlarmScheduler.nextPullTimeMs()
        return buildString {
            if (sync != null) append("\nNext auto-sync: ${fmt.format(Date(sync))}")
            append("\nWorkout refresh: ${fmt.format(Date(pull))}")
        }
    }

    private fun triggerSync() {
        setStatus(Ui.PRIMARY, "Syncing…", "Workouts → calendar → journal → watch")
        val id = SyncWorker.enqueue(this)
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(id).observe(this) { info ->
            when (info?.state) {
                WorkInfo.State.SUCCEEDED -> {
                    lifecycleScope.launch {
                        val summary = Prefs.getLastSyncSummary(this@MainActivity) ?: ""
                        setStatus(Ui.SUCCESS, "Sync complete", summary)
                        WodWidgetProvider.notifyDataChanged(this@MainActivity)
                    }
                }
                WorkInfo.State.FAILED ->
                    setStatus(Ui.ERROR, "Sync failed", "Tap View Log for details.")
                else -> {}
            }
        }
    }

    private fun showLog() {
        val log = SyncLogger.read(this)
        val tv = TextView(this).apply {
            text = log
            textSize = 11f
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            setTextColor(Ui.TEXT_PRIMARY)
        }
        AlertDialog.Builder(this)
            .setTitle("Sync Log")
            .setView(ScrollView(this).also { it.addView(tv) })
            .setPositiveButton("Close", null)
            .setNeutralButton("Clear") { _, _ -> SyncLogger.clear(this) }
            .show()
    }

    private fun checkForUpdates() {
        setStatus(Ui.PRIMARY, "Checking for updates…", "")
        lifecycleScope.launch {
            val update = UpdateChecker.checkForUpdate(BuildConfig.VERSION_CODE)
            if (update == null) {
                setStatus(Ui.SUCCESS, "Up to date",
                    "v${BuildConfig.VERSION_NAME} is the latest.")
                return@launch
            }
            val dateDisplay = if (update.buildDate.isNotEmpty()) {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    val fmt = SimpleDateFormat("MMM d, yyyy 'at' HH:mm 'UTC'", Locale.getDefault())
                    fmt.format(sdf.parse(update.buildDate)!!)
                } catch (_: Exception) { update.buildDate }
            } else "unknown"
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Update available")
                .setMessage("v${update.remoteVersionName} is available (you have v${BuildConfig.VERSION_NAME}).\nBuilt: $dateDisplay\n\nDownload and install now?")
                .setPositiveButton("Install") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            UpdateChecker.downloadAndInstall(this@MainActivity) { pct ->
                                runOnUiThread { setStatus(Ui.PRIMARY, "Downloading update…", "$pct%") }
                            }
                        } catch (e: Exception) {
                            setStatus(Ui.ERROR, "Download failed", e.message ?: "")
                        }
                    }
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun resolveCalendarName(calendarId: Long): String {
        val projection = arrayOf(
            android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            android.provider.CalendarContract.Calendars.ACCOUNT_NAME,
        )
        contentResolver.query(
            android.provider.CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${android.provider.CalendarContract.Calendars._ID} = ?",
            arrayOf(calendarId.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name    = cursor.getString(0) ?: ""
                val account = cursor.getString(1) ?: ""
                return "$name ($account)"
            }
        }
        return calendarId.toString()
    }

    private fun setStatus(color: Int, title: String, detail: String) {
        val dotSize = Ui.dp(this, 10)
        statusDot.background = Ui.dotBg(color, dotSize)
        statusLabel.text = title
        if (detail.isNotEmpty()) {
            statusDetail.text = detail
            statusDetail.visibility = View.VISIBLE
        } else {
            statusDetail.visibility = View.GONE
        }
    }
}
