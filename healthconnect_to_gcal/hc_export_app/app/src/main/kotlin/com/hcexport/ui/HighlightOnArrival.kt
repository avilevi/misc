package com.hcexport.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Brings the wrapped row to the exact place it was linked from, then pulses a
 * tint behind it a few times so the eye lands on it — the same "here it is"
 * gesture as a settings app pointing at the switch it wants changed.
 *
 * The scroll happens once per composition of the target; [active] stays true
 * for the whole visit. With [center], the row is placed in the middle of the
 * screen rather than merely made visible — [scrollState] is the list it lives
 * in, and the centring is measured against the window height.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HighlightOnArrival(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    center: Boolean = false,
    scrollState: ScrollableState? = null,
    content: @Composable () -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    val glow = remember { Animatable(0f) }
    val radius = with(LocalDensity.current) { 12.dp.toPx() }
    val view = LocalView.current
    var bounds by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        delay(160)
        val target = bounds
        if (center && scrollState != null && target != null) {
            // Two passes: the first scroll may stop short when the list has
            // not measured the content below yet; the second corrects it.
            repeat(2) {
                val row = bounds ?: return@repeat
                val delta = row.center.y - view.height / 2f
                if (delta.isFinite() && abs(delta) > 4f) {
                    runCatching { scrollState.animateScrollBy(delta) }
                    delay(40)
                }
            }
        } else {
            runCatching { requester.bringIntoView() }
        }
        delay(160)
        repeat(3) {
            glow.animateTo(0.50f, tween(170))
            glow.animateTo(0.12f, tween(170))
        }
        glow.animateTo(0f, tween(420))
    }
    Box(
        modifier
            .onGloballyPositioned { bounds = it.boundsInWindow() }
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
