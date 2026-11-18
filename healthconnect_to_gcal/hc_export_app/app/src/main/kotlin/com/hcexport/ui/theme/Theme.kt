package com.hcexport.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hcexport.R

// ── Refined Deep Ocean ───────────────────────────────────────────────────────
// Near-black navy depth, one refined grotesque (Barlow), and a small family of
// jewel accents. Each section owns one accent; everything else stays quiet.

val Abyss = Color(0xFF04070D)
val Obsidian = Color(0xFF070B13)
val Card = Color(0xFF0E1523)
val CardHigh = Color(0xFF151F31)
val CardLift = Color(0xFF1A2539)
val Hairline = Color(0xFF1E2A40)
val HairlineSoft = Color(0xFF151E30)

val Snow = Color(0xFFF4F7FD)
val Mist = Color(0xFFB9C5DA)
val Slate = Color(0xFF7486A3)

val Teal = Color(0xFF34E3C4)
val TealDeep = Color(0xFF0FB9A5)
val Periwinkle = Color(0xFF8EA6FF)
val PeriwinkleDeep = Color(0xFF5E77E8)
val Amber = Color(0xFFFFB454)
val AmberDeep = Color(0xFFE58B2A)
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

private val Display = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Light, color = Snow)
private val Heading = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, color = Snow)
private val Body = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.Normal, color = Snow)

val HcTypography = Typography(
    displayLarge = Display.copy(fontSize = 46.sp, lineHeight = 50.sp, letterSpacing = (-1.2).sp),
    displayMedium = Heading.copy(fontSize = 38.sp, lineHeight = 42.sp, letterSpacing = (-1.0).sp),
    displaySmall = Heading.copy(fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp),
    headlineLarge = Heading.copy(fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = Heading.copy(fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    headlineSmall = Heading.copy(fontSize = 20.sp, lineHeight = 24.sp),
    titleLarge = Body.copy(fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = Body.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = Body.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = Body.copy(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = Body.copy(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = Body.copy(fontSize = 12.sp, lineHeight = 18.sp, color = Mist),
    labelLarge = Heading.copy(fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 1.1.sp),
    labelMedium = Heading.copy(fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp),
    labelSmall = Heading.copy(fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 1.2.sp),
)

val HcShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun HcTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Teal,
            onPrimary = Abyss,
            secondary = Periwinkle,
            onSecondary = Abyss,
            tertiary = Amber,
            onTertiary = Abyss,
            background = Obsidian,
            onBackground = Snow,
            surface = Card,
            onSurface = Snow,
            surfaceVariant = CardHigh,
            onSurfaceVariant = Mist,
            outline = Hairline,
            outlineVariant = HairlineSoft,
            error = Danger,
            onError = Color.White,
        ),
        typography = HcTypography,
        shapes = HcShapes,
        content = content,
    )
}

/** Accent used for a workout type. Keeps every calendar/list consistent. */
fun workoutAccent(typeKey: String): Color = when {
    typeKey == "wod" -> Teal
    typeKey == "hyrox" -> Periwinkle
    typeKey == "pump" || typeKey == "pump_blend" -> Amber
    typeKey == "weightlifting" -> Coral
    else -> Slate
}
