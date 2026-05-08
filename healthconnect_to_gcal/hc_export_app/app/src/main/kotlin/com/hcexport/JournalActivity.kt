package com.hcexport

import android.app.AlertDialog
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class JournalActivity : ComponentActivity() {

    private var currentYear: Int = 0
    private var currentMonth: Int = 0
    private var selectedDate: String = ""

    private lateinit var calendarContainer: LinearLayout
    private lateinit var monthLabel: TextView
    private lateinit var dayHeaderText: TextView
    private lateinit var dayEntriesContainer: LinearLayout

    private val dateFmt    = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val headerFmt  = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
    private val timeFmt    = SimpleDateFormat("h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cal = Calendar.getInstance()
        currentYear  = cal.get(Calendar.YEAR)
        currentMonth = cal.get(Calendar.MONTH)
        selectedDate = dateFmt.format(cal.time)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(Ui.dp(this@JournalActivity, 20), Ui.dp(this@JournalActivity, 48), Ui.dp(this@JournalActivity, 20), Ui.dp(this@JournalActivity, 24))
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(this@JournalActivity, 20))
        }
        TextView(this).apply {
            text = "Journal"
            textSize = 24f
            setTextColor(Ui.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
        }.also { header.addView(it) }
        header.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        })
        header.addView(Ui.secondaryButton(this, "Back") { finish() })
        root.addView(header)

        // Calendar card
        root.addView(Ui.card(this) {
            val nav = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, Ui.dp(context, 8))
            }
            nav.addView(navButton("<") { shiftMonth(-1) })
            monthLabel = TextView(context).apply {
                textSize = 16f
                setTextColor(Ui.TEXT_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            nav.addView(monthLabel)
            nav.addView(navButton(">") { shiftMonth(1) })
            addView(nav)

            addView(Ui.divider(context))

            val dowRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 4))
            }
            for (d in listOf("M", "T", "W", "T", "F", "S", "S")) {
                dowRow.addView(TextView(context).apply {
                    text = d
                    textSize = 11f
                    setTextColor(Ui.TEXT_MUTED)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            }
            addView(dowRow)

            calendarContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            addView(calendarContainer)

            addView(Ui.sectionSpacer(context, 8))
            addView(Ui.secondaryButton(context, "Today") {
                val c = Calendar.getInstance()
                currentYear  = c.get(Calendar.YEAR)
                currentMonth = c.get(Calendar.MONTH)
                selectedDate = dateFmt.format(c.time)
                refreshCalendar()
                refreshDayEntries()
            })
        })

        root.addView(Ui.sectionSpacer(this, 24))

        // Day header
        dayHeaderText = TextView(this).apply {
            textSize = 18f
            setTextColor(Ui.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, Ui.dp(this@JournalActivity, 16))
        }
        root.addView(dayHeaderText)

        // Entries container
        dayEntriesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(dayEntriesContainer)

        setContentView(ScrollView(this).apply {
            addView(root)
            setBackgroundColor(Ui.BG)
        })

        refreshCalendar()
        refreshDayEntries()
    }

    override fun onResume() {
        super.onResume()
        refreshCalendar()
        refreshDayEntries()
    }

    // ── Calendar grid ────────────────────────────────────────────────────────

    private fun refreshCalendar() {
        monthLabel.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            .format(Date(dayStartMs(currentYear, currentMonth, 1)))

        calendarContainer.removeAllViews()

        val cal = Calendar.getInstance()
        cal.set(currentYear, currentMonth, 1)
        val daysInMonth   = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDowZeroMon = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

        val todayCal = Calendar.getInstance()
        val todayY = todayCal.get(Calendar.YEAR)
        val todayM = todayCal.get(Calendar.MONTH)
        val todayD = todayCal.get(Calendar.DAY_OF_MONTH)

        val selDay = if (selectedDate.startsWith("%04d-%02d".format(currentYear, currentMonth + 1)))
            selectedDate.substringAfterLast('-').toIntOrNull() else null

        val entries = JournalStorage.getEntriesForMonth(this, currentYear, currentMonth)
        val dayMap  = JournalStorage.buildDayMap(entries)

        var cell = 0
        val totalCells = firstDowZeroMon + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, Ui.dp(this@JournalActivity, 2), 0, Ui.dp(this@JournalActivity, 2))
            }
            for (col in 0 until 7) {
                val day = cell - firstDowZeroMon + 1
                val inMonth = day in 1..daysInMonth

                val cellView = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(Ui.dp(this@JournalActivity, 4), Ui.dp(this@JournalActivity, 6), Ui.dp(this@JournalActivity, 4), Ui.dp(this@JournalActivity, 6))

                    val bgColor = when {
                        inMonth && day == selDay -> Ui.SURFACE_ELEVATED
                        inMonth && currentYear == todayY && currentMonth == todayM && day == todayD -> Ui.SURFACE
                        else -> android.graphics.Color.TRANSPARENT
                    }
                    background = if (bgColor != android.graphics.Color.TRANSPARENT)
                        Ui.cardBg(Ui.dpf(this@JournalActivity, 8), fillColor = bgColor, borderColor = android.graphics.Color.TRANSPARENT)
                    else null

                    isClickable = inMonth
                    if (inMonth) {
                        setOnClickListener {
                            selectedDate = "%04d-%02d-%02d".format(currentYear, currentMonth + 1, day)
                            refreshCalendar()
                            refreshDayEntries()
                        }
                    }
                }

                if (inMonth) {
                    cellView.addView(TextView(this).apply {
                        text = day.toString()
                        textSize = 14f
                        setTextColor(if (day == selDay) Ui.PRIMARY else Ui.TEXT_PRIMARY)
                        gravity = Gravity.CENTER
                    })
                    val hasEntries = dayMap.containsKey(day)
                    val hasCustom  = hasEntries && dayMap[day]!!.any { it.hasCustomizations() }
                    val dotColor = when {
                        hasCustom -> Ui.ACCENT
                        hasEntries -> Ui.PRIMARY
                        else -> android.graphics.Color.TRANSPARENT
                    }
                    if (hasEntries) {
                        val dotSize = Ui.dp(this, 5)
                        cellView.addView(View(this).apply {
                            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).also {
                                it.setMargins(0, Ui.dp(this@JournalActivity, 2), 0, 0)
                            }
                            background = Ui.dotBg(dotColor, dotSize)
                        })
                    }
                }
                rowLayout.addView(cellView)
                cell++
            }
            calendarContainer.addView(rowLayout)
        }
    }

    // ── Day entries (narrative diary) ────────────────────────────────────────

    private fun refreshDayEntries() {
        val dayMs = dateToMs(selectedDate)
        dayHeaderText.text = headerFmt.format(Date(dayMs))
        dayEntriesContainer.removeAllViews()

        val entries = JournalStorage.getEntriesForDay(this, selectedDate)

        if (entries.isEmpty()) {
            dayEntriesContainer.addView(TextView(this).apply {
                text = "Nothing logged on this day."
                textSize = 13f
                setTextColor(Ui.TEXT_MUTED)
                setPadding(0, Ui.dp(this@JournalActivity, 8), 0, 0)
            })
            return
        }

        for ((i, entry) in entries.withIndex()) {
            if (i > 0) {
                dayEntriesContainer.addView(ruleView())
            }
            dayEntriesContainer.addView(buildEntryBlock(entry))
        }
    }

    private fun buildEntryBlock(entry: JournalEntry): View {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Ui.dp(this@JournalActivity, 8), 0, Ui.dp(this@JournalActivity, 8))
        }

        // Time + type label
        val metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(this@JournalActivity, 4))
        }
        val startMs = extractStartMs(entry)
        val typeLabel = if (entry.entryType == "sleep") "Sleep" else {
            try {
                val tc = JSONObject(entry.originalDataJson).optInt("tc", -1)
                CalendarHelper.EXERCISE_TYPES[tc]?.second ?: "Workout"
            } catch (_: Exception) { "Workout" }
        }
        metaRow.addView(TextView(this).apply {
            text = "${timeFmt.format(Date(startMs))}  ·  $typeLabel"
            textSize = 11f
            setTextColor(Ui.TEXT_MUTED)
            letterSpacing = 0.04f
        })
        block.addView(metaRow)

        // Narrative text
        val narrative = entry.narrativeText()
        block.addView(TextView(this).apply {
            text = narrative
            textSize = 15f
            setTextColor(Ui.TEXT_PRIMARY)
            setLineSpacing(Ui.dpf(this@JournalActivity, 4), 1f)
            setPadding(0, 0, 0, Ui.dp(this@JournalActivity, 8))
        })

        // Edit links row
        val linksRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 0)
        }
        if (entry.entryType == "exercise") {
            linksRow.addView(editLink("edit data…") { showEditDataDialog(entry) })
            linksRow.addView(TextView(this).apply {
                text = "  "
                textSize = 11f
            })
        }
        linksRow.addView(editLink("edit text…") { showEditTextDialog(entry) })
        if (entry.hasCustomizations()) {
            linksRow.addView(TextView(this).apply {
                text = "  "
                textSize = 11f
            })
            linksRow.addView(editLink("revert") { showRevertDialog(entry) })
        }
        block.addView(linksRow)

        return block
    }

    private fun editLink(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 11f
            setTextColor(Ui.TEXT_MUTED)
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener { onClick() }
        }

    private fun ruleView(): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this@JournalActivity, 1),
            ).also { it.setMargins(0, Ui.dp(this@JournalActivity, 4), 0, Ui.dp(this@JournalActivity, 4)) }
            setBackgroundColor(Ui.BORDER_FAINT)
        }

    // ── Edit Text dialog ─────────────────────────────────────────────────────

    private fun showEditTextDialog(entry: JournalEntry) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@JournalActivity, 20), Ui.dp(this@JournalActivity, 16), Ui.dp(this@JournalActivity, 20), Ui.dp(this@JournalActivity, 8))
        }
        val input = EditText(this).apply {
            setText(entry.narrativeText())
            textSize = 15f
            setTextColor(Ui.TEXT_PRIMARY)
            minLines = 6
            gravity = Gravity.START or Gravity.TOP
            setPadding(Ui.dp(this@JournalActivity, 14), Ui.dp(this@JournalActivity, 12), Ui.dp(this@JournalActivity, 14), Ui.dp(this@JournalActivity, 12))
            background = Ui.cardBg(Ui.dpf(this@JournalActivity, 10), fillColor = Ui.SURFACE_ELEVATED, borderColor = Ui.BORDER_FAINT)
        }
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Edit Text")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val text = input.text.toString().trim()
                // Check if text differs from auto-generated narrative
                val autoNarrative = DiaryNarrator.generate(entry)
                val updated = entry.copy(
                    customNarrative = text.takeIf { it.isNotEmpty() && it != autoNarrative },
                    updatedAtMs = System.currentTimeMillis(),
                )
                JournalStorage.updateEntry(this, updated)
                refreshDayEntries()
                refreshCalendar()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Edit Data dialog (exercise fields) ────────────────────────────────────

    private fun showEditDataDialog(entry: JournalEntry) {
        val json = JSONObject(entry.effectiveDataJson())
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@JournalActivity, 20), Ui.dp(this@JournalActivity, 12), Ui.dp(this@JournalActivity, 20), Ui.dp(this@JournalActivity, 8))
        }

        data class FieldSpec(val key: String, val label: String, val isNum: Boolean = false,
                             val toDisplay: (JSONObject, String) -> String = { j, k -> j.optString(k, "") })

        val fields = mutableListOf<Pair<FieldSpec, EditText>>()

        // Exercise field specs
        val fieldSpecs = listOf(
            FieldSpec("tt", "Title"),
            FieldSpec("dm", "Distance (km)", isNum = true,
                toDisplay = { j, k -> if (j.has(k)) "%.2f".format(j.getDouble(k) / 1000.0) else "" }),
            FieldSpec("ck", "Calories (kcal)", isNum = true,
                toDisplay = { j, k -> if (j.has(k)) "%.0f".format(j.getDouble(k)) else "" }),
            FieldSpec("ah", "Avg HR (bpm)", isNum = true,
                toDisplay = { j, k -> if (j.has(k)) "%.0f".format(j.getDouble(k)) else "" }),
            FieldSpec("mh", "Max HR (bpm)", isNum = true,
                toDisplay = { j, k -> if (j.has(k)) "%.0f".format(j.getDouble(k)) else "" }),
            FieldSpec("pp", "Pace (/km)", isNum = true,
                toDisplay = { j, k ->
                    if (!j.has(k)) "" else {
                        val sec = j.getDouble(k).toInt()
                        "%d:%02d".format(sec / 60, sec % 60)
                    }
                }),
            FieldSpec("st", "Steps", isNum = true,
                toDisplay = { j, k -> if (j.has(k)) j.getLong(k).toString() else "" }),
            FieldSpec("nt", "Notes"),
        )

        for (spec in fieldSpecs) {
            container.addView(TextView(this).apply {
                text = spec.label.uppercase()
                textSize = 10f
                setTextColor(Ui.TEXT_MUTED)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.06f
                setPadding(0, Ui.dp(this@JournalActivity, 8), 0, Ui.dp(this@JournalActivity, 4))
            })
            val edit = EditText(this).apply {
                setText(spec.toDisplay(json, spec.key))
                textSize = 13f
                setTextColor(Ui.TEXT_PRIMARY)
                setPadding(Ui.dp(this@JournalActivity, 12), Ui.dp(this@JournalActivity, 8), Ui.dp(this@JournalActivity, 12), Ui.dp(this@JournalActivity, 8))
                background = Ui.cardBg(Ui.dpf(this@JournalActivity, 8), fillColor = Ui.SURFACE_ELEVATED, borderColor = Ui.BORDER_FAINT)
                if (spec.key == "nt") {
                    minLines = 3
                    gravity = Gravity.START or Gravity.TOP
                }
                if (spec.isNum) {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                }
            }
            container.addView(edit)
            fields += spec to edit
        }

        // Source (read-only)
        container.addView(TextView(this).apply {
            text = "SOURCE".uppercase()
            textSize = 10f
            setTextColor(Ui.TEXT_MUTED)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.06f
            setPadding(0, Ui.dp(this@JournalActivity, 8), 0, Ui.dp(this@JournalActivity, 4))
        })
        container.addView(TextView(this).apply {
            val pkg = json.optString("sp", "")
            val name = when {
                pkg.contains("samsung") -> "Samsung Health"
                pkg.contains("fitbit")  -> "Fitbit"
                pkg.contains("garmin")  -> "Garmin Connect"
                pkg.contains("fitness") -> "Google Fit"
                pkg.contains("huawei")  -> "Huawei Health"
                pkg.contains("polar")   -> "Polar Flow"
                pkg.contains("strava")  -> "Strava"
                pkg.contains("withings")-> "Withings"
                pkg.contains("whoop")   -> "WHOOP"
                else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }
            text = name
            textSize = 13f
            setTextColor(Ui.TEXT_SECONDARY)
        })

        val scrollContainer = ScrollView(this).apply {
            addView(container)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this@JournalActivity, 480),
            )
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Data")
            .setView(scrollContainer)
            .setPositiveButton("Save") { _, _ ->
                val updates = mutableMapOf<String, String?>()
                val origJson = JSONObject(entry.originalDataJson)
                for ((spec, edit) in fields) {
                    val newVal = edit.text.toString().trim()
                    if (newVal.isEmpty()) continue

                    val parsedValue: Any? = when (spec.key) {
                        "dm" -> newVal.toDoubleOrNull()?.times(1000.0)?.let {
                            val origM = if (origJson.has("dm")) origJson.getDouble("dm") else null
                            if (origM != null && kotlin.math.abs(it - origM) < 0.01) null else it
                        }
                        "pp" -> {
                            val parts = newVal.split(":")
                            if (parts.size == 2) {
                                val totalSec = (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
                                totalSec.toDouble().takeIf { it > 0 }
                            } else newVal.toDoubleOrNull()
                        }
                        "ck", "ah", "mh" -> newVal.toDoubleOrNull()
                        "st" -> newVal.toLongOrNull()
                        "tt", "nt" -> newVal
                        else -> newVal
                    }

                    if (parsedValue != null) {
                        // Check if value actually changed from original
                        val origVal = if (origJson.has(spec.key)) origJson.get(spec.key) else null
                        if (parsedValue != origVal) {
                            updates[spec.key] = parsedValue.toString()
                        }
                    }
                }

                if (updates.isNotEmpty()) {
                    val updated = entry.editDataFields(updates)
                    JournalStorage.updateEntry(this, updated)
                }
                refreshDayEntries()
                refreshCalendar()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Revert dialog ────────────────────────────────────────────────────────

    private fun showRevertDialog(entry: JournalEntry) {
        AlertDialog.Builder(this)
            .setTitle("Revert to original?")
            .setMessage("This will discard your custom data and text edits.")
            .setPositiveButton("Revert") { _, _ ->
                JournalStorage.updateEntry(this, entry.reverted())
                refreshDayEntries()
                refreshCalendar()
            }
            .setNegativeButton("Keep edits", null)
            .show()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun shiftMonth(delta: Int) {
        currentMonth += delta
        if (currentMonth < 0) { currentMonth = 11; currentYear-- }
        if (currentMonth > 11) { currentMonth = 0; currentYear++ }
        refreshCalendar()
    }

    private fun navButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 18f
            setTextColor(Ui.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            background = Ui.cardBg(Ui.dpf(this@JournalActivity, 8), fillColor = Ui.SURFACE_ELEVATED, borderColor = Ui.BORDER_FAINT)
            setPadding(Ui.dp(this@JournalActivity, 14), Ui.dp(this@JournalActivity, 6), Ui.dp(this@JournalActivity, 14), Ui.dp(this@JournalActivity, 6))
            setOnClickListener { onClick() }
        }

    private fun dayStartMs(year: Int, month: Int, day: Int): Long {
        val c = Calendar.getInstance()
        c.set(year, month, day, 0, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun dateToMs(dateStr: String): Long {
        try { return dateFmt.parse(dateStr)?.time ?: System.currentTimeMillis() }
        catch (_: Exception) { return System.currentTimeMillis() }
    }

    private fun extractStartMs(entry: JournalEntry): Long =
        try { JSONObject(entry.originalDataJson).getLong("sm") }
        catch (_: Exception) { 0L }
}
