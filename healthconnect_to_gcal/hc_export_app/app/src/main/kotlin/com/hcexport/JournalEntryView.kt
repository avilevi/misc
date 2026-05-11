package com.hcexport

import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object JournalEntryView {

    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

    // Stage colors for sleep visualization
    private val stageColors = mapOf(
        "Deep Sleep"  to 0xFF7C3AED.toInt(),
        "REM"         to 0xFF6366F1.toInt(),
        "Light Sleep" to 0xFF60A5FA.toInt(),
        "Awake"       to 0xFFF59E0B.toInt(),
        "Out of Bed"  to 0xFFEF4444.toInt(),
        "Sleeping"    to 0xFF6B7280.toInt(),
        "Unknown"     to 0xFF4B5563.toInt(),
    )

    fun stageColor(stageName: String): Int = stageColors[stageName] ?: 0xFF6B7280.toInt()

    // ── Public entry point ─────────────────────────────────────────────────

    fun build(
        ctx: android.content.Context,
        entry: JournalEntry,
        onEntryChanged: (JournalEntry) -> Unit,
    ): View {
        return when (entry.entryType) {
            "exercise" -> buildExerciseCard(ctx, entry, onEntryChanged)
            "sleep"    -> buildSleepCard(ctx, entry, onEntryChanged)
            "wod"      -> buildWodCard(ctx, entry, onEntryChanged)
            else       -> buildUnknownCard(ctx, entry, onEntryChanged)
        }
    }

    // ── Exercise card ──────────────────────────────────────────────────────

    private fun buildExerciseCard(
        ctx: android.content.Context,
        entry: JournalEntry,
        onEntryChanged: (JournalEntry) -> Unit,
    ): View {
        val json = JSONObject(entry.effectiveDataJson())
        val startMs   = json.getLong("sm")
        val endMs     = json.getLong("em")
        val typeCode  = json.optInt("tc", -1)
        val sourcePkg = json.optString("sp", "")
        val source    = SourceBrands.displayName(sourcePkg)

        val (emoji, typeLabel) = CalendarHelper.EXERCISE_TYPES[typeCode]
            ?: ("🏃" to "Workout")

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                Ui.dp(ctx, 18), Ui.dp(ctx, 18),
                Ui.dp(ctx, 18), Ui.dp(ctx, 14),
            )
            background = Ui.cardBg(Ui.dpf(ctx, 16))
        }

        // Header row
        card.addView(entryHeader(ctx, emoji, typeLabel, startMs, source, sourcePkg))

        card.addView(Ui.sectionSpacer(ctx, 10))

        // ── Metrics section ────────────────────────────────────────────────

        card.addView(Ui.sectionTitle(ctx, "Metrics"))

        // Duration
        card.addView(Ui.metricRow(ctx, "Duration", fmtDur((endMs - startMs) / 60_000), "", onClick = {
            showSimpleEditDialog(ctx, "Edit Duration", fmtDurMinutesRaw((endMs - startMs) / 60_000), "minutes", isNumeric = true,
                onSave = { newVal ->
                    val newMin = newVal.toLongOrNull() ?: return@showSimpleEditDialog
                    updateEntryField(entry, "em", (startMs + newMin * 60_000).toString(), onEntryChanged)
                })
        }))

        // Distance
        if (json.has("dm")) {
            val km = json.getDouble("dm") / 1000.0
            card.addView(Ui.metricRow(ctx, "Distance", distFormat(km), "km", onClick = {
                showSimpleEditDialog(ctx, "Edit Distance", "%.2f".format(km), "km", isNumeric = true,
                    onSave = { newVal ->
                        val newKm = newVal.toDoubleOrNull() ?: return@showSimpleEditDialog
                        updateEntryField(entry, "dm", (newKm * 1000).toString(), onEntryChanged)
                    })
            }))
        }

        // Pace
        if (json.has("pp")) {
            val pace = json.getDouble("pp")
            card.addView(Ui.metricRow(ctx, "Pace", fmtPace(pace), "/km", onClick = {
                showSimpleEditDialog(ctx, "Edit Pace", fmtPaceRaw(pace), "m:ss per km", isNumeric = false,
                    onSave = { newVal ->
                        val totalSec = parsePaceToSec(newVal) ?: return@showSimpleEditDialog
                        updateEntryField(entry, "pp", totalSec.toString(), onEntryChanged)
                    })
            }))
        }

        // Calories
        if (json.has("ck")) {
            val kcal = json.getDouble("ck").toInt()
            card.addView(Ui.metricRow(ctx, "Calories", kcal.toString(), "kcal", onClick = {
                showSimpleEditDialog(ctx, "Edit Calories", kcal.toString(), "kcal", isNumeric = true,
                    onSave = { newVal ->
                        val newKcal = newVal.toDoubleOrNull() ?: return@showSimpleEditDialog
                        updateEntryField(entry, "ck", newKcal.toString(), onEntryChanged)
                    })
            }))
        }

        // Avg HR
        if (json.has("ah")) {
            val avgHr = json.getDouble("ah").toInt()
            card.addView(Ui.metricRow(ctx, "Avg HR", avgHr.toString(), "bpm", onClick = {
                showSimpleEditDialog(ctx, "Edit Avg HR", avgHr.toString(), "bpm", isNumeric = true,
                    onSave = { newVal ->
                        val newHr = newVal.toDoubleOrNull() ?: return@showSimpleEditDialog
                        updateEntryField(entry, "ah", newHr.toString(), onEntryChanged)
                    })
            }))
        }

        // Max HR
        if (json.has("mh")) {
            val maxHr = json.getDouble("mh").toInt()
            card.addView(Ui.metricRow(ctx, "Max HR", maxHr.toString(), "bpm", onClick = {
                showSimpleEditDialog(ctx, "Edit Max HR", maxHr.toString(), "bpm", isNumeric = true,
                    onSave = { newVal ->
                        val newHr = newVal.toDoubleOrNull() ?: return@showSimpleEditDialog
                        updateEntryField(entry, "mh", newHr.toString(), onEntryChanged)
                    })
            }))
        }

        // Steps
        if (json.has("st")) {
            val steps = json.getLong("st")
            card.addView(Ui.metricRow(ctx, "Steps", "%,d".format(steps), "", onClick = {
                showSimpleEditDialog(ctx, "Edit Steps", steps.toString(), "steps", isNumeric = true,
                    onSave = { newVal ->
                        val newSteps = newVal.toLongOrNull() ?: return@showSimpleEditDialog
                        updateEntryField(entry, "st", newSteps.toString(), onEntryChanged)
                    })
            }))
        }

        // HR graph
        val hrArr = json.optJSONArray("hr")
        if (hrArr != null && hrArr.length() > 1) {
            card.addView(Ui.sectionSpacer(ctx, 4))
            card.addView(Ui.sectionTitle(ctx, "Heart Rate"))
            card.addView(buildHrGraph(ctx, hrArr))
        }

        // Source (icon + name)
        card.addView(sourceRow(ctx, sourcePkg))

        // ── Notes section ──────────────────────────────────────────────────

        card.addView(Ui.sectionTitle(ctx, "Notes"))
        val narrative = entry.narrativeText()
        card.addView(TextView(ctx).apply {
            text = narrative
            textSize = 14f
            setTextColor(Ui.TEXT_PRIMARY)
            setLineSpacing(Ui.dpf(ctx, 4), 1f)
            Linkify.addLinks(this, Linkify.WEB_URLS)
            movementMethod = LinkMovementMethod.getInstance()
            setPadding(0, 0, 0, Ui.dp(ctx, 8))
        })

        // ── Merged WOD section ────────────────────────────────────────────
        val mwArr = json.optJSONArray("mw")
        if (mwArr != null && mwArr.length() > 0) {
            for (i in 0 until mwArr.length()) {
                val mw = mwArr.getJSONObject(i)
                val wodDate = mw.getString("d")
                val wodTitle = mw.optString("t", "WOD")
                val wodContent = mw.getString("w")

                // Inner card with amber accent
                val wodCard = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(
                        Ui.dp(ctx, 14), Ui.dp(ctx, 12),
                        Ui.dp(ctx, 14), Ui.dp(ctx, 12),
                    )
                    background = GradientDrawable().apply {
                        setColor(Ui.SURFACE_ELEVATED)
                        cornerRadius = Ui.dpf(ctx, 10)
                        setStroke(Ui.dp(ctx, 1), Ui.ACCENT)
                    }
                }
                (wodCard.layoutParams as? LinearLayout.LayoutParams)?.setMargins(
                    0, Ui.dp(ctx, 10), 0, 0,
                )

                // Header: 📅 Title · date
                wodCard.addView(TextView(ctx).apply {
                    text = "📅  $wodTitle  ·  $wodDate"
                    textSize = 13f
                    setTextColor(Ui.ACCENT)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, 0, 0, Ui.dp(ctx, 6))
                })

                // WOD content
                wodCard.addView(TextView(ctx).apply {
                    text = wodContent
                    textSize = 14f
                    setTextColor(Ui.TEXT_PRIMARY)
                    setLineSpacing(Ui.dpf(ctx, 4), 1f)
                    setPadding(0, 0, 0, Ui.dp(ctx, 2))
                    Linkify.addLinks(this, Linkify.WEB_URLS)
                    movementMethod = LinkMovementMethod.getInstance()
                })

                card.addView(wodCard)
            }
        }

        // Action links
        card.addView(actionLinks(ctx, entry, onEntryChanged))

        return card
    }

    // ── Sleep card ─────────────────────────────────────────────────────────

    private fun buildSleepCard(
        ctx: android.content.Context,
        entry: JournalEntry,
        onEntryChanged: (JournalEntry) -> Unit,
    ): View {
        val json = JSONObject(entry.effectiveDataJson())
        val startMs = json.getLong("sm")
        val endMs   = json.getLong("em")
        val sourcePkg = json.optString("sp", "")
        val source    = SourceBrands.displayName(sourcePkg)
        val totalMin = (endMs - startMs) / 60_000

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                Ui.dp(ctx, 18), Ui.dp(ctx, 18),
                Ui.dp(ctx, 18), Ui.dp(ctx, 14),
            )
            background = Ui.cardBg(Ui.dpf(ctx, 16))
        }

        // Header
        card.addView(entryHeader(ctx, "😴", "Sleep", startMs, source, sourcePkg))

        card.addView(Ui.sectionSpacer(ctx, 10))

        // ── Metrics section ────────────────────────────────────────────────

        card.addView(Ui.sectionTitle(ctx, "Metrics"))

        // Total duration
        card.addView(Ui.metricRow(ctx, "Duration", fmtDur(totalMin), "", onClick = {
            showSimpleEditDialog(ctx, "Edit Duration", totalMin.toString(), "minutes", isNumeric = true,
                onSave = { newVal ->
                    val newMin = newVal.toLongOrNull() ?: return@showSimpleEditDialog
                    updateEntryField(entry, "em", (startMs + newMin * 60_000).toString(), onEntryChanged)
                })
        }))

        // ── Sleep stages ───────────────────────────────────────────────────

        val stagesArr = json.optJSONArray("sg")
        if (stagesArr != null && stagesArr.length() > 0) {
            card.addView(Ui.sectionTitle(ctx, "Sleep Stages"))

            // Build stage summary: name → total duration in minutes
            val stageTotals = linkedMapOf<String, Long>()
            for (i in 0 until stagesArr.length()) {
                val s = stagesArr.getJSONObject(i)
                val stageStart = s.getLong("sm")
                val stageEnd   = s.getLong("em")
                val name = CalendarHelper.SLEEP_STAGES[s.optInt("sc", 0)] ?: "Unknown"
                stageTotals[name] = (stageTotals[name] ?: 0L) + (stageEnd - stageStart) / 60_000
            }

            // Compute total for bar proportions
            val totalStageMin = stageTotals.values.sum().coerceAtLeast(1L)

            // Horizontal stacked bar
            val barHeight = Ui.dp(ctx, 8)
            val bar = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    barHeight,
                ).also { it.setMargins(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 8)) }
                background = Ui.cardBg(Ui.dpf(ctx, 4), fillColor = Ui.SURFACE_ELEVATED, borderColor = android.graphics.Color.TRANSPARENT)
                clipChildren = true
            }
            val stageOrder = listOf("Deep Sleep", "REM", "Light Sleep", "Awake", "Out of Bed", "Sleeping", "Unknown")
            for (name in stageOrder) {
                val dur = stageTotals[name] ?: continue
                val weight = (dur.toFloat() / totalStageMin).coerceAtLeast(0.02f)
                val segment = View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, barHeight, weight)
                    setBackgroundColor(stageColor(name))
                }
                bar.addView(segment)
            }
            card.addView(bar)

            // Per-stage rows
            for (name in stageOrder) {
                val durMin = stageTotals[name] ?: continue
                card.addView(Ui.sleepStageRow(ctx, name, stageColor(name), fmtDur(durMin), durMin.toFloat() / totalStageMin, onClick = {
                    showSimpleEditDialog(ctx, "Edit $name", durMin.toString(), "minutes", isNumeric = true,
                        onSave = { newVal ->
                            val newMin = newVal.toLongOrNull() ?: return@showSimpleEditDialog
                            updateSleepStage(entry, name, newMin, onEntryChanged)
                        })
                }))
            }
        }

        // Source (icon + name)
        card.addView(sourceRow(ctx, sourcePkg))

        // ── Notes section ──────────────────────────────────────────────────

        card.addView(Ui.sectionTitle(ctx, "Notes"))
        val narrative = entry.narrativeText()
        card.addView(TextView(ctx).apply {
            text = narrative
            textSize = 14f
            setTextColor(Ui.TEXT_PRIMARY)
            setLineSpacing(Ui.dpf(ctx, 4), 1f)
            setPadding(0, 0, 0, Ui.dp(ctx, 8))
            Linkify.addLinks(this, Linkify.WEB_URLS)
            movementMethod = LinkMovementMethod.getInstance()
        })

        // Action links
        card.addView(actionLinks(ctx, entry, onEntryChanged))

        return card
    }

    // ── WOD card ───────────────────────────────────────────────────────────

    private val POTENTIAL_BG = 0xFF2A2418.toInt()  // warm amber-tinted dark surface

    private fun buildWodCard(
        ctx: android.content.Context,
        entry: JournalEntry,
        onEntryChanged: (JournalEntry) -> Unit,
    ): View {
        val json = JSONObject(entry.effectiveDataJson())
        val startMs = json.optLong("sm", 0L)
        val isFuture = startMs > System.currentTimeMillis()

        val cardBg = if (isFuture) {
            GradientDrawable().apply {
                setColor(POTENTIAL_BG)
                cornerRadius = Ui.dpf(ctx, 16)
                setStroke(Ui.dp(ctx, 1), Ui.ACCENT)
            }
        } else {
            Ui.cardBg(Ui.dpf(ctx, 16))
        }

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                Ui.dp(ctx, 18), Ui.dp(ctx, 18),
                Ui.dp(ctx, 18), Ui.dp(ctx, 14),
            )
            background = cardBg
        }

        // Header row with potential badge
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(ctx).apply {
            text = "🔥"
            textSize = 20f
        })
        header.addView(TextView(ctx).apply {
            text = "  WOD"
            textSize = 16f
            setTextColor(Ui.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
        })
        if (isFuture) {
            header.addView(TextView(ctx).apply {
                text = "  POTENTIAL"
                textSize = 9f
                setTextColor(Ui.ACCENT)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
                setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 2), Ui.dp(ctx, 8), Ui.dp(ctx, 2))
                background = GradientDrawable().apply {
                    setColor(Ui.ACCENT and 0x20FFFFFF.toInt())
                    cornerRadius = Ui.dpf(ctx, 4)
                }
            })
        }
        header.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        })
        header.addView(TextView(ctx).apply {
            val dateStr = json.optString("date", entry.date)
            val timeStr = if (startMs > 0) "  ${timeFmt.format(Date(startMs))}" else ""
            text = "$dateStr$timeStr"
            textSize = 11f
            setTextColor(Ui.TEXT_MUTED)
        })
        card.addView(header)

        card.addView(Ui.sectionSpacer(ctx, 10))

        // WOD content
        val narrative = entry.narrativeText()
        card.addView(TextView(ctx).apply {
            text = narrative
            textSize = 14f
            setTextColor(if (isFuture) Ui.TEXT_SECONDARY else Ui.TEXT_PRIMARY)
            setLineSpacing(Ui.dpf(ctx, 4), 1f)
            setPadding(0, 0, 0, Ui.dp(ctx, 8))
            Linkify.addLinks(this, Linkify.WEB_URLS)
            movementMethod = LinkMovementMethod.getInstance()
        })

        // Action links
        card.addView(actionLinks(ctx, entry, onEntryChanged))

        return card
    }

    // ── Unknown card ───────────────────────────────────────────────────────

    private fun buildUnknownCard(
        ctx: android.content.Context,
        entry: JournalEntry,
        onEntryChanged: (JournalEntry) -> Unit,
    ): View {
        val json = JSONObject(entry.effectiveDataJson())
        val startMs = json.optLong("sm", 0L)
        val sourcePkg = json.optString("sp", "")
        val source    = SourceBrands.displayName(sourcePkg)

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                Ui.dp(ctx, 18), Ui.dp(ctx, 18),
                Ui.dp(ctx, 18), Ui.dp(ctx, 14),
            )
            background = Ui.cardBg(Ui.dpf(ctx, 16))
        }

        card.addView(entryHeader(ctx, "📋", "Activity", startMs, source, sourcePkg))
        card.addView(Ui.sectionTitle(ctx, "Notes"))
        card.addView(TextView(ctx).apply {
            text = entry.narrativeText()
            textSize = 14f
            setTextColor(Ui.TEXT_PRIMARY)
        })
        card.addView(actionLinks(ctx, entry, onEntryChanged))
        return card
    }

    // ── Shared components ──────────────────────────────────────────────────

    private fun entryHeader(
        ctx: android.content.Context,
        emoji: String,
        typeLabel: String,
        timeMs: Long,
        source: String,
        sourcePkg: String,
    ): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(TextView(ctx).apply {
                text = emoji
                textSize = 20f
            })
            addView(TextView(ctx).apply {
                text = "  $typeLabel"
                textSize = 16f
                setTextColor(Ui.TEXT_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            })
            addView(TextView(ctx).apply {
                text = "${timeFmt.format(Date(timeMs))}  ·  "
                textSize = 11f
                setTextColor(Ui.TEXT_MUTED)
            })
            val iconSize = Ui.dp(ctx, 14)
            addView(android.widget.ImageView(ctx).apply {
                setImageResource(SourceBrands.iconResId(sourcePkg))
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            })
        }

    private fun sourceRow(
        ctx: android.content.Context,
        sourcePkg: String,
    ): LinearLayout {
        val brand = SourceBrands.forPackage(sourcePkg)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(ctx, 4))

            addView(TextView(ctx).apply {
                text = "Source"
                textSize = 12f
                setTextColor(Ui.TEXT_MUTED)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            })
            val iconSize = Ui.dp(ctx, 16)
            addView(android.widget.ImageView(ctx).apply {
                setImageResource(brand.iconResId)
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            })
            addView(TextView(ctx).apply {
                text = "  ${brand.displayName}"
                textSize = 13f
                setTextColor(Ui.TEXT_SECONDARY)
            })
        }
    }

    private fun actionLinks(
        ctx: android.content.Context,
        entry: JournalEntry,
        onEntryChanged: (JournalEntry) -> Unit,
    ): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, Ui.dp(ctx, 4), 0, 0)

            addView(linkButton(ctx, "edit notes") {
                showEditNotesDialog(ctx, entry, onEntryChanged)
            })
            if (entry.hasCustomizations()) {
                addView(TextView(ctx).apply {
                    text = "  "
                    textSize = 11f
                })
                addView(linkButton(ctx, "revert") {
                    showRevertDialog(ctx, entry, onEntryChanged)
                })
            }
        }

    private fun linkButton(ctx: android.content.Context, label: String, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = label
            textSize = 11f
            setTextColor(Ui.TEXT_MUTED)
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener { onClick() }
        }

    // ── Dialogs ────────────────────────────────────────────────────────────

    /** Simple single-value edit dialog for a metric. */
    private fun showSimpleEditDialog(
        ctx: android.content.Context,
        title: String,
        currentValue: String,
        unit: String,
        isNumeric: Boolean,
        onSave: (String) -> Unit,
    ) {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                Ui.dp(ctx, 22), Ui.dp(ctx, 16),
                Ui.dp(ctx, 22), Ui.dp(ctx, 4),
            )
        }
        val input = EditText(ctx).apply {
            setText(currentValue)
            textSize = 16f
            setTextColor(Ui.TEXT_PRIMARY)
            if (isNumeric) {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 12))
            background = Ui.cardBg(Ui.dpf(ctx, 10), fillColor = Ui.SURFACE_ELEVATED, borderColor = Ui.BORDER)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        container.addView(input)
        if (unit.isNotEmpty()) {
            container.addView(TextView(ctx).apply {
                text = " $unit"
                textSize = 15f
                setTextColor(Ui.TEXT_SECONDARY)
                setPadding(Ui.dp(ctx, 8), 0, 0, 0)
            })
        }

        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                onSave(input.text.toString().trim())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Full free-text notes editing dialog. */
    private fun showEditNotesDialog(
        ctx: android.content.Context,
        entry: JournalEntry,
        onEntryChanged: (JournalEntry) -> Unit,
    ) {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 16), Ui.dp(ctx, 20), Ui.dp(ctx, 8))
        }
        val input = EditText(ctx).apply {
            setText(entry.narrativeText())
            textSize = 15f
            setTextColor(Ui.TEXT_PRIMARY)
            minLines = 6
            gravity = Gravity.START or Gravity.TOP
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 12))
            background = Ui.cardBg(Ui.dpf(ctx, 10), fillColor = Ui.SURFACE_ELEVATED, borderColor = Ui.BORDER)
        }
        container.addView(input)

        AlertDialog.Builder(ctx)
            .setTitle("Edit Notes")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val text = input.text.toString().trim()
                val autoNarrative = DiaryNarrator.generate(entry)
                val updated = entry.copy(
                    customNarrative = text.takeIf { it.isNotEmpty() && it != autoNarrative },
                    updatedAtMs = System.currentTimeMillis(),
                )
                onEntryChanged(updated)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Revert confirmation dialog. */
    private fun showRevertDialog(
        ctx: android.content.Context,
        entry: JournalEntry,
        onEntryChanged: (JournalEntry) -> Unit,
    ) {
        AlertDialog.Builder(ctx)
            .setTitle("Revert to original?")
            .setMessage("This will discard your custom data and text edits.")
            .setPositiveButton("Revert") { _, _ ->
                onEntryChanged(entry.reverted())
            }
            .setNegativeButton("Keep edits", null)
            .show()
    }

    // ── Data helpers ───────────────────────────────────────────────────────

    private fun updateEntryField(
        entry: JournalEntry,
        key: String,
        value: String,
        onEntryChanged: (JournalEntry) -> Unit,
    ) {
        val updated = entry.editDataField(key, value)
        onEntryChanged(updated)
    }

    private fun updateSleepStage(
        entry: JournalEntry,
        stageName: String,
        newDurationMin: Long,
        onEntryChanged: (JournalEntry) -> Unit,
    ) {
        val json = JSONObject(entry.effectiveDataJson())
        val stagesArr = json.optJSONArray("sg") ?: return
        // Find first stage of matching type and adjust its endMs
        for (i in 0 until stagesArr.length()) {
            val s = stagesArr.getJSONObject(i)
            val name = CalendarHelper.SLEEP_STAGES[s.optInt("sc", 0)] ?: "Unknown"
            if (name == stageName) {
                val oldStart = s.getLong("sm")
                s.put("em", oldStart + newDurationMin * 60_000)
                break
            }
        }
        val updated = entry.copy(
            customDataJson = json.toString(),
            customNarrative = null,
            updatedAtMs = System.currentTimeMillis(),
        )
        onEntryChanged(updated)
    }

    // ── HR Graph ───────────────────────────────────────────────────────────

    private fun buildHrGraph(
        ctx: android.content.Context,
        hrArr: JSONArray,
    ): View {
        val samples = mutableListOf<Pair<Int, Int>>() // (offsetMin, bpm)
        for (i in 0 until hrArr.length()) {
            val s = hrArr.getJSONObject(i)
            samples += s.getInt("o") to s.getInt("b")
        }
        if (samples.isEmpty()) return View(ctx)

        val graphHeight = Ui.dp(ctx, 150)
        val leftPad     = Ui.dp(ctx, 36).toFloat()
        val rightPad    = Ui.dp(ctx, 12).toFloat()
        val topPad      = Ui.dp(ctx, 16).toFloat()
        val bottomPad   = Ui.dp(ctx, 28).toFloat()

        val minBpm = (samples.minOf { it.second } / 10 * 10).coerceAtLeast(0)
        val maxBpm = ((samples.maxOf { it.second } + 9) / 10 * 10).coerceAtLeast(minBpm + 10)
        val maxMin = samples.maxOf { it.first }.coerceAtLeast(1)

        val density = ctx.resources.displayMetrics.density
        val linePaint = Paint().apply {
            color = Ui.PRIMARY
            strokeWidth = density * 2.5f
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fillPaint = Paint().apply {
            color = Ui.PRIMARY
            style = Paint.Style.FILL
            isAntiAlias = true
            alpha = 30
        }
        val dotPaint = Paint().apply {
            color = Ui.PRIMARY
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val gridPaint = Paint().apply {
            color = Ui.BORDER_FAINT
            strokeWidth = Ui.dpf(ctx, 1)
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        return object : View(ctx) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (width == 0 || height == 0) return

                val w = width.toFloat()
                val h = height.toFloat()
                val gw = w - leftPad - rightPad  // graph area width
                val gh = h - topPad - bottomPad   // graph area height

                if (gw <= 0 || gh <= 0) return

                // Y-axis grid lines + labels
                val ySteps = 4
                for (i in 0..ySteps) {
                    val y = topPad + gh * i / ySteps
                    val bpmVal = maxBpm - (maxBpm - minBpm) * i / ySteps
                    canvas.drawLine(leftPad, y, w - rightPad, y, gridPaint)
                    canvas.drawText(
                        bpmVal.toString(),
                        leftPad - Ui.dpf(context, 4),
                        y + Ui.dpf(context, 4),
                        Paint().apply {
                            color = Ui.TEXT_MUTED
                            textSize = Ui.dpf(context, 9)
                            isAntiAlias = true
                            textAlign = Paint.Align.RIGHT
                        },
                    )
                }

                // X-axis time labels
                val xSteps = when {
                    maxMin <= 30 -> 4
                    maxMin <= 60 -> 6
                    else -> 8
                }
                val stepMin = (maxMin / xSteps).coerceAtLeast(1)
                for (t in 0..maxMin step stepMin) {
                    val x = leftPad + gw * t / maxMin
                    canvas.drawText(
                        "${t}m",
                        x,
                        bottomPad - Ui.dpf(context, 2),
                        Paint().apply {
                            color = Ui.TEXT_MUTED
                            textSize = Ui.dpf(context, 9)
                            isAntiAlias = true
                            textAlign = Paint.Align.CENTER
                        },
                    )
                }

                // Build smooth curve path
                val path = Path()
                val pts = samples.map { (t, bpm) ->
                    val x = leftPad + gw * t / maxMin
                    val y = topPad + gh - gh * (bpm - minBpm) / (maxBpm - minBpm)
                    x to y
                }

                path.moveTo(pts[0].first, pts[0].second)
                for (i in 1 until pts.size) {
                    val (x1, y1) = pts[i - 1]
                    val (x2, y2) = pts[i]
                    val cx = (x1 + x2) / 2f
                    path.cubicTo(cx, y1, cx, y2, x2, y2)
                }
                canvas.drawPath(path, linePaint)

                // Fill under curve
                if (pts.size >= 2) {
                    val fillPath = Path(path)
                    fillPath.lineTo(pts.last().first, topPad + gh)
                    fillPath.lineTo(pts.first().first, topPad + gh)
                    fillPath.close()
                    canvas.drawPath(fillPath, fillPaint)
                }

                // Data point dots
                val dotRadius = Ui.dpf(context, 3)
                for ((x, y) in pts) {
                    canvas.drawCircle(x, y, dotRadius, dotPaint)
                }
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                graphHeight,
            ).also { it.setMargins(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 4)) }
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

    // ── Formatting ─────────────────────────────────────────────────────────

    private fun fmtDur(totalMin: Long): String = when {
        totalMin >= 60 -> {
            val h = totalMin / 60; val m = totalMin % 60
            if (m > 0) "${h}h ${m}m" else "${h}h"
        }
        else -> "${totalMin}m"
    }

    private fun fmtDurMinutesRaw(totalMin: Long): String = totalMin.toString()

    private fun distFormat(km: Double): String =
        if (km >= 100) "%.0f".format(km) else if (km >= 10) "%.1f".format(km) else "%.2f".format(km)

    private fun fmtPace(secPerKm: Double): String {
        val min = (secPerKm / 60).toInt()
        val sec = (secPerKm % 60).toInt()
        return "%d:%02d".format(min, sec)
    }

    private fun fmtPaceRaw(secPerKm: Double): String {
        val min = (secPerKm / 60).toInt()
        val sec = (secPerKm % 60).toInt()
        return "%d:%02d".format(min, sec)
    }

    private fun parsePaceToSec(raw: String): Long? {
        val parts = raw.split(":")
        if (parts.size == 2) {
            val min = parts[0].toLongOrNull() ?: return null
            val sec = parts[1].toLongOrNull() ?: return null
            return min * 60 + sec
        }
        return raw.toLongOrNull()
    }

}
