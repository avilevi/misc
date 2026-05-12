package com.hcexport

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object Ui {

    // ── Color constants (mirrors colors.xml for programmatic use) ────────────

    val BG              = 0xFF0B1121.toInt()
    val SURFACE         = 0xFF162033.toInt()
    val SURFACE_ELEVATED = 0xFF1D2A42.toInt()
    val PRIMARY         = 0xFF00D4C3.toInt()
    val PRIMARY_DARK    = 0xFF00B09D.toInt()
    val ON_PRIMARY      = 0xFF0B1121.toInt()
    val SECONDARY       = 0xFF7B93E0.toInt()
    val ACCENT          = 0xFFF5A623.toInt()
    val TEXT_PRIMARY    = 0xFFECF1F8.toInt()
    val TEXT_SECONDARY  = 0xFF8B9EC0.toInt()
    val TEXT_MUTED      = 0xFF506080.toInt()
    val SUCCESS         = 0xFF34D399.toInt()
    val WARNING         = 0xFFF59E0B.toInt()
    val ERROR           = 0xFFF87171.toInt()
    val BORDER          = 0xFF253550.toInt()
    val BORDER_FAINT    = 0xFF1C2B44.toInt()

    // ── Unit conversion ──────────────────────────────────────────────────────

    /** Convert dp to raw pixels. */
    fun dp(ctx: Context, dp: Int): Int = (dp * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun dpf(ctx: Context, dp: Int): Float = (dp * ctx.resources.displayMetrics.density + 0.5f)

    // ── Drawables (all params in raw pixels) ─────────────────────────────────

    fun cardBg(cornerPx: Float, fillColor: Int = SURFACE, borderColor: Int = BORDER): GradientDrawable =
        GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = cornerPx
            setStroke(1, borderColor)
        }

    fun editFieldBg(cornerPx: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(SURFACE_ELEVATED)
            cornerRadius = cornerPx
            setStroke(2, ACCENT)
        }

    fun pillBg(fillColor: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fillColor)
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 9999f
        }

    fun dotBg(color: Int, px: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            shape = GradientDrawable.OVAL
            setSize(px, px)
        }

    // ── View factories ───────────────────────────────────────────────────────

    fun sectionHeader(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text.uppercase()
            textSize = 11f
            setTextColor(TEXT_MUTED)
            letterSpacing = 0.10f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(context,6))
        }

    fun card(context: Context, block: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context,20), dp(context,20), dp(context,20), dp(context,20))
            background = cardBg(dpf(context,16))
            block()
        }

    fun primaryButton(context: Context, label: String, onClick: () -> Unit): Button =
        Button(context).apply {
            text = label
            textSize = 15f
            setTextColor(ON_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            background = pillBg(PRIMARY)
            setPadding(dp(context,24), dp(context,16), dp(context,24), dp(context,16))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

    fun secondaryButton(context: Context, label: String, onClick: () -> Unit): Button =
        Button(context).apply {
            text = label
            textSize = 13f
            setTextColor(TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            background = cardBg(dpf(context,12), fillColor = SURFACE_ELEVATED, borderColor = BORDER_FAINT)
            setPadding(dp(context,16), dp(context,13), dp(context,16), dp(context,13))
            setOnClickListener { onClick() }
        }

    fun sectionSpacer(context: Context, heightDp: Int = 24): View =
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context,heightDp),
            )
        }

    fun divider(context: Context): View =
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context,1),
            ).also { it.setMargins(0, dp(context,10), 0, dp(context,10)) }
            setBackgroundColor(BORDER_FAINT)
        }

    fun statusLine(
        context: Context,
        label: String,
        value: String,
        valueColor: Int = TEXT_PRIMARY,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(context,2), 0, dp(context,2))
            addView(TextView(context).apply {
                text = label
                textSize = 13f
                setTextColor(TEXT_SECONDARY)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            })
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            })
            addView(TextView(context).apply {
                text = value
                textSize = 13f
                setTextColor(valueColor)
                gravity = Gravity.END
            })
        }

    /** Section title within a card: bold, muted, uppercase label. */
    fun sectionTitle(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text.uppercase()
            textSize = 11f
            setTextColor(TEXT_MUTED)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setPadding(0, dp(context, 12), 0, dp(context, 8))
        }

    /** Tappable metric row: [label] … [value + unit], used in journal entry cards. */
    fun metricRow(
        context: Context,
        label: String,
        value: String,
        unit: String,
        onClick: (() -> Unit)?,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 6), 0, dp(context, 6))

            addView(TextView(context).apply {
                text = label
                textSize = 14f
                setTextColor(TEXT_SECONDARY)
            })
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            })
            addView(TextView(context).apply {
                text = value
                textSize = 14f
                setTextColor(TEXT_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
            })
            if (unit.isNotEmpty()) {
                addView(TextView(context).apply {
                    text = " $unit"
                    textSize = 13f
                    setTextColor(TEXT_MUTED)
                })
            }
            if (onClick != null) {
                isClickable = true
                isFocusable = true
                background = cardBg(dpf(context, 8), fillColor = android.graphics.Color.TRANSPARENT, borderColor = android.graphics.Color.TRANSPARENT)
                setOnClickListener { onClick() }
            }
        }

    /** Sleep stage row with colored indicator bar. */
    fun sleepStageRow(
        context: Context,
        label: String,
        color: Int,
        durationStr: String,
        barFraction: Float,
        onClick: (() -> Unit)?,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 6), 0, dp(context, 6))

            // Colored dot
            val dot = dp(context, 10)
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dot, dot).also {
                    it.setMargins(0, 0, dp(context, 10), 0)
                }
                background = GradientDrawable().apply {
                    setColor(color)
                    shape = GradientDrawable.OVAL
                    setSize(dot, dot)
                }
            })

            // Label
            addView(TextView(context).apply {
                text = label
                textSize = 14f
                setTextColor(TEXT_PRIMARY)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            // Duration
            addView(TextView(context).apply {
                text = durationStr
                textSize = 14f
                setTextColor(TEXT_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
            })

            if (onClick != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        }

    /** Thin horizontal rule used between entry cards. */
    fun cardRule(context: Context): View =
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 1),
            ).also { it.setMargins(0, dp(context, 8), 0, dp(context, 8)) }
            setBackgroundColor(BORDER_FAINT)
        }

    /** Small colored dot + text, used for status indicators. */
    fun labeledDot(context: Context, color: Int, text: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val dotSize = dp(context,8)
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).also {
                    it.setMargins(0, 0, dp(context,8), 0)
                }
                background = dotBg(color, dotSize)
            })
            addView(TextView(context).apply {
                this.text = text
                textSize = 15f
                setTextColor(TEXT_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
            })
        }
}
