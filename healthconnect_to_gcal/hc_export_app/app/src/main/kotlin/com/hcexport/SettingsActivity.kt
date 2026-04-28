package com.hcexport

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity

class SettingsActivity : ComponentActivity() {

    private lateinit var sleepLabel: TextView
    private lateinit var exerciseLabel: TextView

    // Mutable so we can refresh after calendar creation
    private var calendars = mutableListOf<Triple<Long, String, String>>() // id, displayName, accountName+type
    private lateinit var calSpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 80, 64, 64)
        }

        TextView(this).apply {
            text = "Settings"
            textSize = 22f
            setPadding(0, 0, 0, 32)
        }.also { root.addView(it) }

        // ── Calendar picker ────────────────────────────────────────────────

        sectionLabel("Calendar", root)

        calSpinner = Spinner(this)
        root.addView(calSpinner)

        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            Button(this@SettingsActivity).apply {
                text = "Refresh list"
                setOnClickListener { refreshCalendarSpinner() }
            }.also { addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
            Button(this@SettingsActivity).apply {
                text = "Create new…"
                setOnClickListener { showCreateCalendarDialog() }
            }.also { addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
        }.also { root.addView(it) }

        root.addView(spacer(32))

        // ── Sync range ─────────────────────────────────────────────────────

        sectionLabel("Sync range (days back)", root)

        val rangeDays    = listOf(7L, 14L, 30L, 60L, 90L, 180L, 365L)
        val rangeLabels  = listOf("7 days", "14 days", "30 days", "60 days", "90 days", "180 days", "1 year")
        val rangeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_item,
                rangeLabels,
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }
        val savedDays = Prefs.getSyncDaysBack(this)
        rangeSpinner.setSelection(rangeDays.indexOf(savedDays).takeIf { it >= 0 } ?: 4)
        rangeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                Prefs.setSyncDaysBack(this@SettingsActivity, rangeDays[pos])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        root.addView(rangeSpinner)

        root.addView(spacer(32))

        // ── Sleep sources ──────────────────────────────────────────────────

        sectionLabel("Sleep — source priority", root)
        sleepLabel = TextView(this).apply {
            textSize = 13f
            setPadding(0, 0, 0, 8)
        }.also { root.addView(it) }

        Button(this).apply {
            text = "Reorder sleep sources…"
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, ReorderActivity::class.java).apply {
                    putExtra(ReorderActivity.EXTRA_TYPE, ReorderActivity.TYPE_SLEEP)
                })
            }
        }.also { root.addView(it) }

        root.addView(spacer(32))

        // ── Exercise sources ───────────────────────────────────────────────

        sectionLabel("Exercise — source priority", root)
        exerciseLabel = TextView(this).apply {
            textSize = 13f
            setPadding(0, 0, 0, 8)
        }.also { root.addView(it) }

        Button(this).apply {
            text = "Reorder exercise sources…"
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, ReorderActivity::class.java).apply {
                    putExtra(ReorderActivity.EXTRA_TYPE, ReorderActivity.TYPE_EXERCISE)
                })
            }
        }.also { root.addView(it) }

        setContentView(ScrollView(this).also { it.addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refreshCalendarSpinner()
        refreshSourceLabels()
    }

    // ── Calendar helpers ───────────────────────────────────────────────────

    private fun refreshCalendarSpinner() {
        calendars.clear()
        calendars.addAll(loadCalendars())

        calSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            calendars.map { it.second },
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        calSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (pos < calendars.size) Prefs.setCalendarId(this@SettingsActivity, calendars[pos].first)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val savedId = Prefs.getCalendarId(this)
        val idx = calendars.indexOfFirst { it.first == savedId }.takeIf { it >= 0 } ?: 0
        if (calendars.isNotEmpty()) {
            calSpinner.setSelection(idx)
            if (savedId == null) Prefs.setCalendarId(this, calendars[0].first)
        }
    }

    private fun showCreateCalendarDialog() {
        val input = EditText(this).apply {
            hint = "Calendar name"
            setText("Health Connect")
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Create new calendar")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "Health Connect" }
                // Use the account of the currently selected calendar
                val selected = calendars.getOrNull(calSpinner.selectedItemPosition)
                val (accountName, accountType) = if (selected != null) {
                    val parts = selected.third.split("|")
                    (parts.getOrNull(0) ?: "") to (parts.getOrNull(1) ?: "com.google")
                } else "" to "com.google"

                val newId = CalendarHelper.createCalendar(this, name, accountName, accountType)
                if (newId != null) {
                    Prefs.setCalendarId(this, newId)
                    refreshCalendarSpinner()
                    Toast.makeText(this, "Calendar \"$name\" created", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to create calendar", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadCalendars(): List<Triple<Long, String, String>> {
        val projection = arrayOf(
            android.provider.CalendarContract.Calendars._ID,
            android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            android.provider.CalendarContract.Calendars.ACCOUNT_NAME,
            android.provider.CalendarContract.Calendars.ACCOUNT_TYPE,
        )
        val result = mutableListOf<Triple<Long, String, String>>()
        contentResolver.query(
            android.provider.CalendarContract.Calendars.CONTENT_URI,
            projection, null, null, null,
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndex(android.provider.CalendarContract.Calendars._ID)
            val nameCol = cursor.getColumnIndex(android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val acctCol = cursor.getColumnIndex(android.provider.CalendarContract.Calendars.ACCOUNT_NAME)
            val typeCol = cursor.getColumnIndex(android.provider.CalendarContract.Calendars.ACCOUNT_TYPE)
            while (cursor.moveToNext()) {
                val type = cursor.getString(typeCol) ?: ""
                if (type != "com.google") continue
                val id      = cursor.getLong(idCol)
                val calName = cursor.getString(nameCol) ?: ""
                val acct    = cursor.getString(acctCol) ?: ""
                result.add(Triple(id, "$calName ($acct)", "$acct|$type"))
            }
        }
        return result
    }

    // ── Source helpers ─────────────────────────────────────────────────────

    private fun refreshSourceLabels() {
        sleepLabel.text    = sourceSummary(Prefs.getSleepSourcePriority(this))
        exerciseLabel.text = sourceSummary(Prefs.getExerciseSourcePriority(this))
    }

    private fun sourceSummary(sources: List<String>): String =
        if (sources.isEmpty()) "Run a sync first to discover sources."
        else sources.mapIndexed { i, pkg -> "${i + 1}. ${friendlySource(pkg)}" }.joinToString("\n")

    private fun sectionLabel(text: String, parent: LinearLayout) {
        TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }.also { parent.addView(it) }
    }

    private fun spacer(dp: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp)
    }

    private fun friendlySource(pkg: String): String = when {
        pkg.contains("samsung") -> "Samsung Health"
        pkg.contains("fitbit")  -> "Fitbit"
        pkg.contains("garmin")  -> "Garmin Connect"
        pkg.contains("google.android.apps.fitness") -> "Google Fit"
        pkg.contains("huawei")  -> "Huawei Health"
        pkg.contains("polar")   -> "Polar Flow"
        pkg.contains("strava")  -> "Strava"
        pkg.contains("withings")-> "Withings"
        pkg.contains("whoop")   -> "WHOOP"
        else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
