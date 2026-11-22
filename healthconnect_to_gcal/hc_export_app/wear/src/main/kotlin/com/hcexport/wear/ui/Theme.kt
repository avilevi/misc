package com.hcexport.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hcexport.wear.R

// ── The phone's palette, tightened for a round screen ──────────────────────
// Deep navy, Barlow, and one jewel accent per workout type. The watch shows
// one thing at a time, so the glow is softer and the type is larger.

val Abyss = Color(0xFF04070D)
val Obsidian = Color(0xFF070B13)
val Card = Color(0xFF0E1523)
val CardHigh = Color(0xFF151F31)
val Hairline = Color(0xFF1E2A40)
val HairlineSoft = Color(0xFF151E30)

val Snow = Color(0xFFF4F7FD)
val Mist = Color(0xFFB9C5DA)
val Slate = Color(0xFF7486A3)

val Teal = Color(0xFF34E3C4)
val Periwinkle = Color(0xFF8EA6FF)
val Amber = Color(0xFFFFB454)
val Coral = Color(0xFFFF7E72)
val Danger = Color(0xFFFF6B6B)
val Success = Color(0xFF37E0A2)
val Warning = Color(0xFFFFC24B)

val Barlow = FontFamily(
    Font(R.font.barlow_light, FontWeight.Light),
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
    Font(R.font.barlow_extrabold, FontWeight.ExtraBold),
)

// The small type scale a wrist can carry.
val Display = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Light, color = Snow)
val Heading = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, color = Snow)
val Body = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, color = Snow)

val ClockStyle = Display.copy(fontSize = 40.sp, lineHeight = 42.sp, letterSpacing = (-1.2).sp)
val ClockSmallStyle = Display.copy(fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.6).sp)
val TitleStyle = Heading.copy(fontSize = 19.sp, lineHeight = 21.sp, letterSpacing = (-0.2).sp)
val TitleSmallStyle = Heading.copy(fontSize = 15.sp, lineHeight = 18.sp)
val BodyStyle = Body.copy(fontSize = 13.sp, lineHeight = 17.sp)
val CaptionStyle = Body.copy(fontSize = 11.sp, lineHeight = 14.sp, color = Slate)
val LabelStyle = Heading.copy(fontSize = 9.sp, lineHeight = 11.sp, letterSpacing = 1.3.sp)
val TinyLabelStyle = Heading.copy(fontSize = 8.sp, lineHeight = 10.sp, letterSpacing = 1.1.sp)

/** The accent a workout type owns across the phone and the watch. */
fun workoutAccent(typeKey: String): Color = when {
    typeKey == "wod" -> Teal
    typeKey == "hyrox" -> Periwinkle
    typeKey == "pump" || typeKey == "pump_blend" -> Amber
    typeKey == "weightlifting" -> Coral
    typeKey == "bridgeathletic" -> Periwinkle
    typeKey == "activity" -> Teal
    typeKey == "sleep" -> Periwinkle
    else -> Slate
}

fun sectionAccent(kind: String): Color = when (kind) {
    "strength" -> Coral
    "metcon" -> Teal
    "accessory" -> Amber
    "rest" -> Periwinkle
    else -> Teal
}

/** The canvas: near-black ink with one soft glow behind the content. */
@Composable
fun WatchBackground(
    accent: Color = Teal,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Obsidian)) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = (-80).dp)
                .size(220.dp)
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.20f), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-70).dp, y = 70.dp)
                .size(190.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Periwinkle.copy(alpha = 0.09f), Color.Transparent)
                    )
                )
        )
        content()
    }
}
