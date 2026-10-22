package io.github.mejdi14.oddscroll.cdrack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.mejdi14.oddscroll.internal.clamped
import io.github.mejdi14.oddscroll.internal.lerp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAP_SLOP = 6f

/**
 * A clear CD jewel case on a white studio backdrop, after Ann Nguyen's WebGL
 * one: the tray's rails and ribs, the hub, the hinge and the lid as clear
 * plastic, a disc inside that turns slowly with [art] printed on it, and the
 * case's soft shadow on the wall behind.
 *
 * Drag it to turn it, and it springs back when let go; tap it to open it —
 * it turns right round as the lid swings out to its left — and tap again to
 * shut it.
 *
 * Every piece is flat in its own frame, so each is drawn once and then sent
 * through its own exact projection, back to front as the case faces; the
 * walls round its edge and the knuckles of the hinge are drawn as the camera
 * sees them each frame. On Android 13 and up the glass and the disc are lit a
 * pixel at a time, after the reference's own shaders; elsewhere they are
 * drawn from a look baked once.
 *
 * @param art printed across the disc, its middle square to the disc's width.
 * @param cover slipped in under the lid's glass, its middle square filling
 *   the lid; none leaves the lid clear.
 * @param background the backdrop; the reference's is white, and the clear
 *   plastic is shaded for a pale one.
 * @param spin turn the disc inside the case.
 */
@Composable
fun CdCase(
    art: ImageBitmap,
    modifier: Modifier = Modifier,
    cover: ImageBitmap? = null,
    background: Color = Color.White,
    spin: Boolean = true,
    state: CdCaseState = rememberCdCaseState(),
) {
    val scene = remember { CaseScene() }
    val camera = remember { Camera() }
    val pose = remember { CasePose() }
    val spinning = rememberUpdatedState(spin)

    LaunchedEffect(state) {
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (previous == 0L) 1f / 60f else (now - previous) / 1e9f
                previous = now
                state.advance(dt.clamped(0f, 1f / 20f), spinning.value)
            }
        }
    }

    Box(modifier.background(background)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var last = down.position
                        var travel = 0f
                        var grabbed = false
                        var lastMillis = down.uptimeMillis
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            lastMillis = change.uptimeMillis
                            if (!change.pressed) break
                            val dx = change.position.x - last.x
                            val dy = change.position.y - last.y
                            last = change.position
                            travel += abs(dx) + abs(dy)
                            if (!grabbed && travel > TAP_SLOP * density) {
                                state.onGrab()
                                grabbed = true
                            }
                            if (grabbed) {
                                state.onGrabMove(dx / density, dy / density, change.uptimeMillis)
                                change.consume()
                            }
                            if (event.type == PointerEventType.Release) break
                        }
                        if (grabbed) {
                            state.onLetGo(lastMillis)
                        } else if (scene.hit(down.position.x, down.position.y)) {
                            state.toggle()
                        }
                    }
                },
        ) {
            state.tick // read, so the case repaints every frame
            camera.fit(size.width, size.height)
            state.pose(pose, camera.openScale)
            scene.setArt(art)
            scene.setCover(cover)
            scene.draw(this, camera, pose)
        }
    }
}
