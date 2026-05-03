package com.hcexport

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity

class SettingsActivity : ComponentActivity() {

    private lateinit var sleepLabel: TextView
    private lateinit var exerciseLabel: TextView
    private lateinit var schedulesList: LinearLayout

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

        // ── Auto-sync schedules ────────────────────────────────────────────

        sectionLabel("Auto-sync schedules", root)

        schedulesList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(schedulesList)

        Button(this).apply {
            text = "+ Add schedule"
            setOnClickListener { showAddScheduleDialog() }
        }.also { root.addView(it) }

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
        refreshSchedulesList()
    }

    // ── Schedule helpers ───────────────────────────────────────────────────

    private fun refreshSchedulesList() {
        schedulesList.removeAllViews()
        val schedules = Prefs.getSyncSchedules(this)
        if (schedules.isEmpty()) {
            TextView(this).apply {
                text = "No schedules yet."
                textSize = 13f
            }.also { schedulesList.addView(it) }
            return
        }
        for (schedule in schedules) {
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 6, 0, 6)
                TextView(this@SettingsActivity).apply {
                    text = schedule.displayString()
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }.also { addView(it) }
                Button(this@SettingsActivity).apply {
                    text = "✕"
                    setOnClickListener {
                        val updated = Prefs.getSyncSchedules(this@SettingsActivity)
                            .filter { it.id != schedule.id }
                        Prefs.setSyncSchedules(this@SettingsActivity, updated)
                        SyncScheduler.applySchedules(this@SettingsActivity)
                        refreshSchedulesList()
                    }
                }.also { addView(it) }
            }.also { schedulesList.addView(it) }
        }
    }

    private fun showAddScheduleDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        // Daily / Weekly radio
        val radioDaily  = RadioButton(this).apply { text = "Daily";  isChecked = true }
        val radioWeekly = RadioButton(this).apply { text = "Weekly" }
        RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(radioDaily)
            addView(radioWeekly)
        }.also { dialogView.addView(it) }

        // Time pickers
        val hourPicker = NumberPicker(this).apply {
            minValue = 0; maxValue = 23
            setFormatter { "%02d".format(it) }
            value = 8
        }
        val minutePicker = NumberPicker(this).apply {
            minValue = 0; maxValue = 59
            setFormatter { "%02d".format(it) }
            value = 0
        }
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 8)
            TextView(this@SettingsActivity).apply { text = "Time:  " }.also { addView(it) }
            addView(hourPicker)
            TextView(this@SettingsActivity).apply { text = "  :  " }.also { addView(it) }
            addView(minutePicker)
        }.also { dialogView.addView(it) }

        // Day checkboxes (weekly only)
        val dayNames  = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val dayChecks = dayNames.mapIndexed { i, name ->
            CheckBox(this).apply { text = name; tag = i + 1 }
        }
        val daysRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
            visibility = View.GONE
            dayChecks.forEach { addView(it) }
        }
        dialogView.addView(TextView(this).apply {
            text = "Days:"
            textSize = 14f
            setPadding(0, 8, 0, 0)
            visibility = View.GONE
            tag = "days_label"
        })
        dialogView.addView(daysRow)

        val daysLabel = dialogView.findViewWithTag<TextView>("days_label")

        radioDaily.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                daysLabel.visibility = View.GONE
                daysRow.visibility = View.GONE
            }
        }
        radioWeekly.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                daysLabel.visibility = View.VISIBLE
                daysRow.visibility = View.VISIBLE
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add sync schedule")
            .setView(dialogView)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val type = if (radioDaily.isChecked) "daily" else "weekly"
            val selectedDays = dayChecks.filter { it.isChecked }.map { it.tag as Int }.toSet()

            if (type == "weekly" && selectedDays.isEmpty()) {
                Toast.makeText(this, "Select at least one day", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val schedule = SyncSchedule(
                type   = type,
                hour   = hourPicker.value,
                minute = minutePicker.value,
                days   = selectedDays,
            )
            Prefs.setSyncSchedules(this, Prefs.getSyncSchedules(this) + schedule)
            SyncScheduler.enqueue(this, schedule)
            refreshSchedulesList()
            dialog.dismiss()
        }
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
