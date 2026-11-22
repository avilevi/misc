package com.hcexport.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Brings the wrapped row to the exact place it was linked from, then pulses a
 * tint behind it a few times so the eye lands on it — the same "here it is"
 * gesture as a settings app pointing at the switch it wants changed.
 *
 * The scroll happens once per composition of the target; [active] stays true
 * for the whole visit.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HighlightOnArrival(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    val glow = remember { Animatable(0f) }
    val radius = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        delay(160)
        runCatching { requester.bringIntoView() }
        delay(160)
        repeat(3) {
            glow.animateTo(0.50f, tween(170))
            glow.animateTo(0.12f, tween(170))
        }
        glow.animateTo(0f, tween(420))
    }
    Box(
        modifier
            .bringIntoViewRequester(requester)
            .drawBehind {
                if (glow.value > 0.01f) {
                    drawRoundRect(
                        color = color.copy(alpha = glow.value),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
            }
    ) {
        content()
    }
}
