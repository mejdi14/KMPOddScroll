package com.example.kmp_odd_scroll

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/** Slow-in / slow-out curve the curtain travels on. */
private val CurtainEasing = CubicBezierEasing(0.76f, 0f, 0.24f, 1f)

/** Quick settle for the title rising into place. */
private val TitleEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

private const val COVER_MS = 550
private const val REVEAL_MS = 650
private const val TITLE_MS = 450
private const val TITLE_DELAY_MS = COVER_MS - 200
private const val LOADER_DELAY_MS = 400L

/**
 * Full-screen black page curtain shown between screens.
 *
 * [navigate] runs a page swap behind it: the curtain slides in, the title
 * rises into view, the swap happens while the screen is covered, the curtain
 * holds so the new screen can compile its shaders and settle, then it slides
 * off the far edge.
 *
 * `direction = 1` travels upwards (bottom → top, going deeper);
 * `direction = -1` travels downwards (top → bottom, going back).
 */
@Stable
internal class PageCurtainState(private val scope: CoroutineScope) {
    var title by mutableStateOf("")
        private set

    /** Vertical position in screen heights: 1 below, 0 covering, -1 above. */
    val offset = Animatable(1f)
    val titleAlpha = Animatable(0f)
    val titleShift = Animatable(0f)
    val loaderAlpha = Animatable(0f)

    var isActive by mutableStateOf(false)
        private set

    fun navigate(
        title: String,
        direction: Int = 1,
        holdMillis: Long = 900,
        swap: () -> Unit
    ) {
        if (isActive) return
        isActive = true
        this.title = title
        val dir = if (direction >= 0) 1f else -1f

        scope.launch {
            try {
                offset.snapTo(dir)
                titleAlpha.snapTo(0f)
                titleShift.snapTo(dir)
                loaderAlpha.snapTo(0f)

                val titleJob = launch {
                    delay(TITLE_DELAY_MS.toLong())
                    launch { titleAlpha.animateTo(1f, tween(TITLE_MS, easing = TitleEasing)) }
                    titleShift.animateTo(0f, tween(TITLE_MS, easing = TitleEasing))
                }

                offset.animateTo(0f, tween(COVER_MS, easing = CurtainEasing))

                swap()
                val coveredAt = TimeSource.Monotonic.markNow()

                val loaderJob = launch {
                    delay(LOADER_DELAY_MS)
                    loaderAlpha.animateTo(1f, tween(200))
                }

                withTimeoutOrNull(2_000) { awaitCalmFrames() }
                val elapsed = coveredAt.elapsedNow().inWholeMilliseconds
                if (elapsed < holdMillis) delay(holdMillis - elapsed)

                titleJob.join()
                loaderJob.cancel()
                offset.animateTo(-dir, tween(REVEAL_MS, easing = CurtainEasing))
            } finally {
                isActive = false
            }
        }
    }

    /**
     * Waits until the new screen renders a run of smooth frames, so the reveal
     * doesn't stutter while shaders compile underneath.
     */
    private suspend fun awaitCalmFrames() {
        var last = withFrameNanos { it }
        var streak = 0
        while (streak < 6) {
            val now = withFrameNanos { it }
            streak = if (now - last < 34_000_000) streak + 1 else 0
            last = now
        }
    }
}

@Composable
internal fun rememberPageCurtainState(): PageCurtainState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PageCurtainState(scope) }
}

@Composable
internal fun PageCurtain(state: PageCurtainState, modifier: Modifier = Modifier) {
    if (!state.isActive) return
    val density = LocalDensity.current
    val riseDistance = with(density) { 24.dp.toPx() }

    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            // Shield the whole pane, including the area the curtain has uncovered.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { translationY = state.offset.value * size.height }
                .background(Color.Black)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.title,
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 36.sp,
                    lineHeight = 40.sp,
                    letterSpacing = (-0.025).em,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer {
                        alpha = state.titleAlpha.value
                        translationY = state.titleShift.value * riseDistance
                    }
                )
                Spacer(Modifier.height(32.dp))
                LoadingLine(Modifier.graphicsLayer { alpha = state.loaderAlpha.value })
            }
        }
    }
}

/** A 1dp hairline with a highlight sweeping across it. */
@Composable
private fun LoadingLine(modifier: Modifier = Modifier) {
    val sweep by rememberInfiniteTransition(label = "loader").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            tween(1_000, easing = CurtainEasing),
            RepeatMode.Restart
        ),
        label = "sweep"
    )
    Box(
        modifier
            .width(96.dp)
            .height(1.dp)
            .clipToBounds()
            .background(Color.White.copy(alpha = 0.15f))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .graphicsLayer { translationX = sweep * size.width }
                .background(Color.White.copy(alpha = 0.7f))
        )
    }
}
