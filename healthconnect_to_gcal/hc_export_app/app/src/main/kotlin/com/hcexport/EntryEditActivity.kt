package com.hcexport

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import org.json.JSONObject

class EntryEditActivity : ComponentActivity() {

    private var originalEntry: JournalEntry? = null
    private var workingEntry: JournalEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val entryJson = intent.getStringExtra("entryJson") ?: run { finish(); return }
        val entry = try { JournalEntry.fromJson(JSONObject(entryJson)) }
            catch (_: Exception) { finish(); return }

        originalEntry = entry
        workingEntry = entry

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
        }

        // ── Top bar ────────────────────────────────────────────────────────
        root.addView(buildTopBar())

        // ── Editable card ──────────────────────────────────────────────────
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setBackgroundColor(Ui.BG)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@EntryEditActivity, 20), Ui.dp(this@EntryEditActivity, 16),
                Ui.dp(this@EntryEditActivity, 20), Ui.dp(this@EntryEditActivity, 24))
        }

        val cardView = JournalEntryView.buildEditable(this, entry) { updated ->
            workingEntry = updated
        }
        content.addView(cardView)

        // Delete button
        content.addView(Ui.sectionSpacer(this, 24))
        val deleteBtn = Button(this).apply {
            text = "Delete Entry"
            textSize = 14f
            setTextColor(Ui.ERROR)
            typeface = Typeface.DEFAULT_BOLD
            background = Ui.cardBg(Ui.dpf(this@EntryEditActivity, 12),
                fillColor = Ui.SURFACE_ELEVATED, borderColor = Ui.BORDER)
            setPadding(Ui.dp(this@EntryEditActivity, 20), Ui.dp(this@EntryEditActivity, 14),
                Ui.dp(this@EntryEditActivity, 20), Ui.dp(this@EntryEditActivity, 14))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                AlertDialog.Builder(this@EntryEditActivity)
                    .setTitle("Delete entry?")
                    .setMessage("This will permanently remove this journal entry.")
                    .setPositiveButton("Delete") { _, _ ->
                        val result = Intent().apply {
                            putExtra("deleted", true)
                            putExtra("entryId", entry.id)
                        }
                        setResult(RESULT_OK, result)
                        finish()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        content.addView(deleteBtn)

        scroll.addView(content)
        root.addView(scroll)

        setContentView(root)
    }

    override fun onBackPressed() {
        val orig = originalEntry ?: run { super.onBackPressed(); return }
        val work = workingEntry ?: run { super.onBackPressed(); return }

        if (work != orig) {
            AlertDialog.Builder(this)
                .setTitle("Unsaved changes")
                .setMessage("You have unsaved changes. What would you like to do?")
                .setPositiveButton("Save") { _, _ -> saveAndFinish() }
                .setNegativeButton("Discard") { _, _ -> super.onBackPressed() }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }

    // ── Top bar ────────────────────────────────────────────────────────────

    private fun buildTopBar(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(this@EntryEditActivity, 12), Ui.dp(this@EntryEditActivity, 40),
                Ui.dp(this@EntryEditActivity, 12), Ui.dp(this@EntryEditActivity, 12))
            setBackgroundColor(Ui.SURFACE)

            addView(backButton())
            addView(TextView(this@EntryEditActivity).apply {
                text = "Edit Entry"
                textSize = 18f
                setTextColor(Ui.TEXT_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(Ui.dp(this@EntryEditActivity, 12), 0, 0, 0)
            })
            addView(View(this@EntryEditActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            })
            addView(saveButton())
        }

    private fun backButton(): Button =
        Button(this).apply {
            text = "←"
            textSize = 20f
            setTextColor(Ui.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            background = Ui.cardBg(Ui.dpf(this@EntryEditActivity, 10),
                fillColor = Ui.SURFACE_ELEVATED, borderColor = Ui.BORDER_FAINT)
            setPadding(Ui.dp(this@EntryEditActivity, 14), Ui.dp(this@EntryEditActivity, 8),
                Ui.dp(this@EntryEditActivity, 14), Ui.dp(this@EntryEditActivity, 8))
            setOnClickListener { onBackPressed() }
        }

    private fun saveButton(): Button =
        Button(this).apply {
            text = "Save"
            textSize = 14f
            setTextColor(Ui.ON_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            background = Ui.pillBg(Ui.PRIMARY)
            setPadding(Ui.dp(this@EntryEditActivity, 20), Ui.dp(this@EntryEditActivity, 10),
                Ui.dp(this@EntryEditActivity, 20), Ui.dp(this@EntryEditActivity, 10))
            setOnClickListener { saveAndFinish() }
        }

    // ── Save ───────────────────────────────────────────────────────────────

    private fun saveAndFinish() {
        val entry = workingEntry ?: return
        val result = Intent().apply {
            putExtra("entryJson", entry.toJson().toString())
        }
        setResult(RESULT_OK, result)
        finish()
    }
}
