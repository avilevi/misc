package com.hcexport.ui

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hcexport.R
import com.hcexport.ui.theme.Abyss
import com.hcexport.ui.theme.Amber
import com.hcexport.ui.theme.Snow
import com.hcexport.ui.theme.Teal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The short celebration when a strength set earns a new record. It is a
 * card, not a full-screen effect: the athlete is mid-workout, so it scales in,
 * plays a one-second trumpet fanfare and leaves without taking a tap.
 */
@Composable
fun NewPrCelebration(
    records: List<String>,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val scale = remember(records) { Animatable(0.82f) }
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
        launch { alpha.animateTo(1f, tween(220)) }
        delay(3_100)
        onFinished()
    }
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Amber.copy(alpha = 0.34f), Teal.copy(alpha = 0.20f)),
                )
            )
            .border(1.dp, Amber.copy(alpha = 0.75f), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_trophy),
            contentDescription = null,
            tint = Amber,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "NEW PR!",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                ),
                color = Amber,
            )
            records.take(3).forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Snow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (records.size > 3) {
                Text(
                    "+${records.size - 3} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = Snow.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** The permanent badge on a record row in the journal entry. */
@Composable
fun NewPrBadge(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(listOf(Amber, Color(0xFFFFD54F))),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_trophy),
            contentDescription = null,
            tint = Abyss,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "NEW PR!",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Abyss,
        )
    }
}

private fun playPrFanfare(context: Context) {
    runCatching {
        val player = MediaPlayer.create(context, R.raw.pr_fanfare) ?: return
        player.setVolume(0.8f, 0.8f)
        player.setOnCompletionListener { it.release() }
        player.start()
    }
}
