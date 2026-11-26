package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import com.example.kmp_odd_scroll.PlatformFamily
import com.example.kmp_odd_scroll.platformFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

internal val LocalInspectionLens = staticCompositionLocalOf<InspectionLens?> { null }

/** All input positions are local to the full-size grid, including taps on inset controls. */
internal class InspectionLens(private val scope: CoroutineScope) {
    var viewport: LayoutCoordinates? = null
    var position by mutableStateOf<Offset?>(null)
        private set
    private val tapStrength = Animatable(0f)
    private var hovering by mutableStateOf(false)
    private var animation: Job? = null

    val strength get() = if (hovering) 1f else tapStrength.value

    fun hover(at: Offset, driftRadius: Float) {
        animation?.cancel()
        hovering = true
        position = at
        // When the mouse rests, gently move the lens around that same point.
        animation = scope.launch {
            if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return@launch
            delay(3000)
            val start = withFrameNanos { it }
            while (true) withFrameNanos { now ->
                val seconds = (now - start) / 1_000_000_000f
                position = at + Offset(sin(seconds * 0.65f), sin(seconds * 0.93f)) * driftRadius
            }
        }
    }

    fun endHover() {
        if (hovering) clear()
    }

    fun clear() {
        animation?.cancel()
        hovering = false
        position = null
    }

    fun tap(at: Offset) {
        animation?.cancel()
        hovering = false
        position = at
        animation = scope.launch {
            tapStrength.snapTo(0.25f)
            tapStrength.animateTo(1f, tween(110))
            tapStrength.animateTo(0f, tween(330))
            position = null
        }
    }
}

@Composable
internal fun rememberInspectionLens(): InspectionLens {
    val scope = rememberCoroutineScope()
    return remember(scope) { InspectionLens(scope) }
}

internal fun Modifier.inspectionHover(lens: InspectionLens, enabled: Boolean): Modifier {
    if (!enabled || platformFamily != PlatformFamily.Web) return this
    return pointerInput(lens) {
        val drift = 18.dp.toPx()
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pointer = event.changes.firstOrNull() ?: continue
                    when {
                        event.type == PointerEventType.Exit || pointer.type != PointerType.Mouse -> lens.endHover()
                        event.type == PointerEventType.Enter || event.type == PointerEventType.Move ->
                            lens.hover(pointer.position, drift)
                    }
                }
            }
        } finally {
            lens.endHover()
        }
    }
}

@Composable
internal fun Modifier.inspectionButtonFeedback(enabled: Boolean = true): Modifier {
    if (!enabled || platformFamily == PlatformFamily.Desktop) return this
    val lens = LocalInspectionLens.current ?: return this
    var button by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return onPlaced { button = it }.pointerInput(lens) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (platformFamily == PlatformFamily.Web && down.type == PointerType.Mouse) return@awaitEachGesture
            val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
            val target = button ?: return@awaitEachGesture
            val viewport = lens.viewport ?: return@awaitEachGesture
            if (target.isAttached && viewport.isAttached) {
                lens.tap(viewport.localPositionOf(target, up.position))
            }
        }
    }
}
