package com.hcexport.ui

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hcexport.R
import com.hcexport.ui.theme.Abyss
import com.hcexport.ui.theme.Amber
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Snow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One record on the celebration card: the lift, then its numbers. */
data class PrLine(
    val exercise: String,
    val detail: String,
)

/**
 * The celebration when a strength set earns a new record: a modal card in the
 * middle of the screen, opaque enough to read over the journal without
 * covering it. It leaves by itself, or on a tap, without taking the athlete
 * out of what they were doing.
 */
@Composable
fun NewPrCelebration(
    records: List<PrLine>,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val finish by rememberUpdatedState(onFinished)
    val scale = remember(records) { Animatable(0.86f) }
    val alpha = remember(records) { Animatable(0f) }
    LaunchedEffect(records) {
        if (records.isEmpty()) return@LaunchedEffect
        playPrFanfare(context)
        launch {
            scale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
        launch { alpha.animateTo(1f, tween(180)) }
        delay(15_000)
        finish()
    }
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                }
                .clip(shape)
                // Dark glass, warm at the top: readable over the journal
                // without hiding it, and clearly a celebration.
                .background(Abyss.copy(alpha = 0.78f))
                .background(
                    Brush.verticalGradient(
                        listOf(Amber.copy(alpha = 0.16f), Color.Transparent),
                    )
                )
                .border(
                    BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                Amber.copy(alpha = 0.80f),
                                Amber.copy(alpha = 0.18f),
                                Amber.copy(alpha = 0.60f),
                            ),
                        ),
                    ),
                    shape,
                ),
        ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Amber.copy(alpha = 0.34f), Color.Transparent),
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Amber, Color(0xFFFFE39B)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_trophy),
                        contentDescription = null,
                        tint = Abyss,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "NEW PR!",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                ),
                color = Amber,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .width(96.dp)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Amber.copy(alpha = 0.9f), Color.Transparent),
                        )
                    )
            )
            Spacer(Modifier.height(18.dp))
            records.take(3).forEachIndexed { index, line ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Snow, fontWeight = FontWeight.Bold)) {
                            append(line.exercise)
                        }
                        withStyle(SpanStyle(color = Amber.copy(alpha = 0.75f))) {
                            append("  ·  ")
                        }
                        withStyle(SpanStyle(color = Amber, fontWeight = FontWeight.Bold)) {
                            append(line.detail)
                        }
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    textAlign = TextAlign.Center,
                )
            }
            if (records.size > 3) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "+${records.size - 3} more",
                    style = MaterialTheme.typography.labelMedium,
                    color = Mist,
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Snow.copy(alpha = 0.08f))
                .clickable(onClickLabel = "Dismiss", onClick = { finish() }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Dismiss",
                tint = Mist,
                modifier = Modifier.size(17.dp),
            )
        }
        }
    }
}

/**
 * `res/raw/pr_fanfare.wav` is "Tada Fanfare A" by plasterbrain (Freesound
 * sound 397355), released CC0 — real sampled brass (Bravura Scoring Brass),
 * 1.7 s. Replaces the old synthesized fanfare.
 */
private fun playPrFanfare(context: Context) {
    runCatching {
        val player = MediaPlayer.create(context, R.raw.pr_fanfare) ?: return
        player.setVolume(0.8f, 0.8f)
        player.setOnCompletionListener { it.release() }
        player.start()
    }
}
