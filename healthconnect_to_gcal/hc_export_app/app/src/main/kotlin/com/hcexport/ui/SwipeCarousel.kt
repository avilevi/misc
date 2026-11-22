package com.hcexport.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val FLING_VELOCITY = 900f

/**
 * A horizontal carousel that shows the neighbouring page while the finger
 * moves: dragging by d slides the current page out by d and reveals the next
 * page from the far edge, with [gap] of background between them. A full page
 * plus the gap is one step, so at rest a page fills the width.
 *
 * Past a distance (or velocity) threshold the release animates the rest of the
 * way, [onSwitch] changes the state behind [page], and the offset resets to 0
 * in the same frame — the page that was sliding in is now the current page.
 * Below the threshold it springs back.
 *
 * [page] receives -1, 0 and +1; all three are composed so the neighbours are
 * real content, not blanks. [onSwitch] receives +1 for "next" (swipe left) and
 * -1 for "previous"; disable a direction with [canSwipeNext]/[canSwipePrevious].
 */
@Composable
fun SwipeCarousel(
    onSwitch: (Int) -> Unit,
    modifier: Modifier = Modifier,
    canSwipeNext: Boolean = true,
    canSwipePrevious: Boolean = true,
    enabled: Boolean = true,
    gap: Dp = 12.dp,
    thresholdFraction: Float = 0.2f,
    page: @Composable (step: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    var switching by remember { mutableStateOf(false) }
    val currentOnSwitch by rememberUpdatedState(onSwitch)
    val currentCanNext by rememberUpdatedState(canSwipeNext)
    val currentCanPrevious by rememberUpdatedState(canSwipePrevious)
    val gapPx = with(LocalDensity.current) { gap.roundToPx() }

    BoxWithConstraints(modifier.clipToBounds()) {
        val stepPx = constraints.maxWidth + gapPx
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { widthPx = it.width }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = enabled && !switching,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val w = widthPx.toFloat()
                            if (w > 0f) {
                                val travel = w + gapPx
                                val lower = if (currentCanNext) -travel else 0f
                                val upper = if (currentCanPrevious) travel else 0f
                                offset.snapTo((offset.value + delta).coerceIn(lower, upper))
                            }
                        }
                    },
                    onDragStopped = { velocity ->
                        val w = widthPx.toFloat()
                        if (w <= 0f) {
                            offset.animateTo(0f)
                        } else {
                            val travel = w + gapPx
                            val current = offset.value
                            val threshold = w * thresholdFraction
                            val direction = when {
                                (current <= -threshold ||
                                    (velocity < -FLING_VELOCITY && current < -8f)) &&
                                    currentCanNext -> 1

                                (current >= threshold ||
                                    (velocity > FLING_VELOCITY && current > 8f)) &&
                                    currentCanPrevious -> -1

                                else -> 0
                            }
                            if (direction == 0) {
                                offset.animateTo(
                                    0f,
                                    spring(stiffness = Spring.StiffnessMediumLow),
                                )
                            } else {
                                switching = true
                                try {
                                    offset.animateTo(
                                        if (direction > 0) -travel else travel,
                                        tween(durationMillis = 170, easing = FastOutSlowInEasing),
                                    )
                                    currentOnSwitch(direction)
                                    offset.snapTo(0f)
                                } finally {
                                    switching = false
                                }
                            }
                        }
                    },
                )
        ) {
            for (step in -1..1) {
                Box(
                    Modifier.offset {
                        IntOffset((step * stepPx + offset.value).roundToInt(), 0)
                    }
                ) {
                    page(step)
                }
            }
        }
    }
}
