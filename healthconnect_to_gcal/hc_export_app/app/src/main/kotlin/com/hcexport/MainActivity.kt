package com.hcexport

import android.app.AlertDialog
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var forceResyncCheck: CheckBox
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

    private val requestHcPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(hcPermissions)) {
            hcPermissionsLaunched = false
            checkAndSetup()
        } else {
            status("⚠ Some Health Connect permissions denied.\n\nOpen the Health Connect app → App permissions → HC Sync, and allow all permissions. Then tap Refresh.")
        }
    }

    private val requestCalendarPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it })
            checkAndSetup()
        else
            status("⚠ Calendar permission denied. HC Sync needs it to create events.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 80, 64, 64)
        }

        TextView(this).apply {
            text = "HC Sync"
            textSize = 24f
            setPadding(0, 0, 0, 12)
        }.also { root.addView(it) }

        statusText = TextView(this).apply {
            text = "Checking…"
            textSize = 14f
            setPadding(0, 0, 0, 32)
        }.also { root.addView(it) }

        forceResyncCheck = CheckBox(this).apply {
            text = "Force full resync"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }.also { root.addView(it) }

        Button(this).apply {
            text = "Sync Now"
            setOnClickListener { triggerSync() }
        }.also { root.addView(it) }

        Button(this).apply {
            text = "View Log"
            setOnClickListener { showLog() }
        }.also { root.addView(it) }

        Button(this).apply {
            text = "Refresh / Re-check Permissions"
            setOnClickListener {
                hcPermissionsLaunched = false
                checkAndSetup()
            }
        }.also { root.addView(it) }

        Button(this).apply {
            text = "Settings"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }.also { root.addView(it) }

        Button(this).apply {
            text = "Check for Updates"
            setOnClickListener { checkForUpdates() }
        }.also { root.addView(it) }

        setContentView(ScrollView(this).also { it.addView(root) })
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
            status("Step 1 of 2: Requesting calendar permission…")
            requestCalendarPermissions.launch(calendarPermissions)
            return
        }

        if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) {
            status("⚠ Health Connect is not available on this device.\nRequires Android 9+ with Health Connect installed.")
            return
        }

        lifecycleScope.launch {
            try {
                val client  = HealthConnectClient.getOrCreate(this@MainActivity)
                val granted = client.permissionController.getGrantedPermissions()
                if (!granted.containsAll(hcPermissions)) {
                    if (!hcPermissionsLaunched) {
                        hcPermissionsLaunched = true
                        status("Step 2 of 2: Requesting Health Connect permissions…")
                        try {
                            requestHcPermissions.launch(hcPermissions)
                        } catch (e: Exception) {
                            hcPermissionsLaunched = false
                            status("⚠ Could not open Health Connect permissions.\n\nOpen the Health Connect app → App permissions → HC Sync → allow all. Then tap Refresh.")
                        }
                    }
                    return@launch
                }
            } catch (e: Exception) {
                status("⚠ Health Connect error: ${e.message}\n\nMake sure Health Connect is installed and up to date, then tap Refresh.")
                return@launch
            }

            val calId = Prefs.getCalendarId(this@MainActivity)
                ?: CalendarHelper.findCalendarId(this@MainActivity)
            if (calId == null) {
                status("⚠ No Google calendar found on this device.\nMake sure a Google account is set up.")
            } else {
                if (Prefs.getCalendarId(this@MainActivity) == null) Prefs.setCalendarId(this@MainActivity, calId)
                val calName    = resolveCalendarName(calId)
                val schedCount = Prefs.getSyncSchedules(this@MainActivity).size
                val schedInfo  = if (schedCount == 0) "No auto-sync scheduled. Tap Settings → Auto-sync schedules to add one."
                                 else "$schedCount auto-sync schedule(s) active."
                val lastSummary = Prefs.getLastSyncSummary(this@MainActivity)
                val summaryLine = if (lastSummary != null) "\n\n$lastSummary" else ""
                status("✓ Ready\n\nCalendar: $calName\n$schedInfo$summaryLine\n\nTap Settings to configure.")
                SyncScheduler.applySchedules(this@MainActivity)
            }
        }
    }

    private fun triggerSync() {
        val force = forceResyncCheck.isChecked
        status("Syncing${if (force) " (full resync)…" else "…"}")
        val data = workDataOf(HcSyncWorker.KEY_FORCE_RESYNC to force)
        val req  = OneTimeWorkRequestBuilder<HcSyncWorker>().setInputData(data).build()
        WorkManager.getInstance(this).enqueue(req)
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(req.id).observe(this) { info ->
            when (info?.state) {
                WorkInfo.State.SUCCEEDED -> {
                    forceResyncCheck.isChecked = false
                    lifecycleScope.launch {
                        val calId   = Prefs.getCalendarId(this@MainActivity)
                            ?: CalendarHelper.findCalendarId(this@MainActivity)
                        val calName = calId?.let { resolveCalendarName(it) } ?: "?"
                        val summary = Prefs.getLastSyncSummary(this@MainActivity) ?: ""
                        status("✓ Sync complete.\nEvents written to: $calName\n\n$summary")
                    }
                }
                WorkInfo.State.FAILED -> status("✗ Sync failed. Tap \"View Log\" for details.")
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
            typeface = android.graphics.Typeface.MONOSPACE
        }
        AlertDialog.Builder(this)
            .setTitle("Sync Log")
            .setView(ScrollView(this).also { it.addView(tv) })
            .setPositiveButton("Close", null)
            .setNeutralButton("Clear") { _, _ -> SyncLogger.clear(this) }
            .show()
    }

    private fun checkForUpdates() {
        status("Checking for updates…")
        lifecycleScope.launch {
            val update = UpdateChecker.checkForUpdate(BuildConfig.VERSION_CODE)
            if (update == null) {
                status("✓ App is up to date (build ${BuildConfig.VERSION_CODE}).")
                return@launch
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Update available")
                .setMessage("Build ${update.remoteVersionCode} is available (you have ${BuildConfig.VERSION_CODE}).\n\nDownload and install now?")
                .setPositiveButton("Install") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            UpdateChecker.downloadAndInstall(this@MainActivity) { pct ->
                                runOnUiThread { status("Downloading update… $pct%") }
                            }
                        } catch (e: Exception) {
                            status("✗ Download failed: ${e.message}")
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

    private fun status(msg: String) { statusText.text = msg }
}
