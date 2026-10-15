package io.github.mejdi14.oddscroll.edgedistortion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import io.github.mejdi14.oddscroll.internal.cardBitmap
import io.github.mejdi14.oddscroll.internal.clamped
import kotlin.math.ceil
import kotlin.math.floor

/** The reference lattice repeats on this period, as the original's wrap does. */
private const val BLOCK = 5

/**
 * The border treatment, as a modifier over the finished frame. It is a fragment
 * shader where the platform has one and nothing where it does not.
 */
@Composable
internal expect fun Modifier.edgeDistortion(strength: Float, aberration: Float): Modifier

@Composable
fun rememberEdgeDistortionScrollState(
    drift: Offset = Offset(0.03f, 0.015f),
): EdgeDistortionScrollState = remember(drift) { EdgeDistortionScrollState(drift) }

/**
 * An endless lattice of cards, panned freely on both axes, bending away at the
 * borders of the frame.
 *
 * Nothing is scrolled in the usual sense: there is no list and no extent. The
 * camera is unbounded, and whichever lattice cells it covers are the ones
 * drawn, each taking its picture from the supplied set on a five-by-five
 * period. Pan far enough in any direction and the pictures come round again,
 * but no edge is ever reached.
 *
 * Left to itself the lattice drifts diagonally. A drag takes it over and the
 * drift resumes on release.
 *
 * The grid underneath is deliberately plain — one axis-aligned draw per visible
 * card, a handful per frame. The fisheye and the colour fringe are not in it at
 * all; they are a single shader pass over the whole frame, which is where that
 * kind of work belongs and the only way it stays free as the lattice fills up.
 * Where no runtime shader is available the grid is drawn flat and everything
 * else behaves the same: see [edgeDistortion].
 *
 * @param images pictures to lay over the lattice, cover-fitted to [cardRatio].
 * @param background the page colour behind the cards.
 * @param cardHeight card height as a share of the viewport's height.
 * @param cardRatio card width / height.
 * @param gapX space between columns as a share of the card's width.
 * @param gapY space between rows as a share of the card's height.
 * @param corner corner rounding as a share of the card's shorter side.
 * @param distortion how hard the borders bend. 0 leaves the frame flat.
 * @param aberration colour fringe offset in uv, so 0.008 is eight pixels across
 *   a thousand-pixel frame. 0 drops it.
 * @param panSpeed lattice pixels moved per pixel of drag.
 * @param drift idle movement per second, as a share of the viewport's height.
 */
@Composable
fun EdgeDistortionScroll(
    images: List<ImageBitmap>,
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    cardHeight: Float = 0.375f,
    cardRatio: Float = 0.8f,
    gapX: Float = 0.333f,
    gapY: Float = 0.267f,
    corner: Float = 0.053f,
    distortion: Float = 0.3f,
    aberration: Float = 0.008f,
    panSpeed: Float = 1.5f,
    drift: Offset = Offset(0.03f, 0.015f),
    state: EdgeDistortionScrollState = rememberEdgeDistortionScrollState(drift),
) {
    if (images.isEmpty()) {
        Box(modifier)
        return
    }

    val cards = remember(images, cardRatio, corner) {
        images.map { cardBitmap(it, cardRatio, corner) }
    }

    LaunchedEffect(state) {
        var previous = 0L
        while (true) {
            withFrameNanos { frame ->
                val deltaMs =
                    if (previous == 0L) 16.67f else (frame - previous) / 1_000_000f
                previous = frame
                state.advance(deltaMs.clamped(1f, 50f), state.lastViewportHeight)
            }
        }
    }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .edgeDistortion(distortion, aberration)
                .pointerInput(state, panSpeed) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        state.onDragStart()
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) break
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: break
                            if (!change.pressed) break
                            state.onDrag(
                                change.position.x - last.x,
                                change.position.y - last.y,
                                panSpeed,
                            )
                            last = change.position
                            change.consume()
                        }
                        state.onDragEnd()
                    }
                }
                .pointerInput(state) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val scroll = event.changes.firstOrNull()?.scrollDelta
                                ?: continue
                            if (scroll.x == 0f && scroll.y == 0f) continue
                            state.offsetBy(scroll.x * 60f, scroll.y * 60f)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            state.tick // read, so the grid redraws every frame while it drifts
            state.lastViewportHeight = size.height
            // Inside the shader's layer, not behind it, so the warp has opaque
            // content to pull on all the way out to the borders.
            drawRect(background)
            drawLattice(cards, state, cardHeight, cardRatio, gapX, gapY)
        }
    }
}

// --- the lattice -------------------------------------------------------------

private fun DrawScope.drawLattice(
    cards: List<ImageBitmap>,
    state: EdgeDistortionScrollState,
    cardHeight: Float,
    cardRatio: Float,
    gapX: Float,
    gapY: Float,
) {
    val halfW = size.width / 2f
    val halfH = size.height / 2f
    if (halfW <= 0f || halfH <= 0f) return

    val cardH = cardHeight * size.height
    val cardW = cardH * cardRatio
    val stepX = cardW * (1f + gapX)
    val stepY = cardH * (1f + gapY)
    if (stepX <= 0f || stepY <= 0f) return

    // The warp only ever pulls content inward, so nothing from outside the
    // undistorted view can reach the frame and this plain rectangle of cells is
    // the whole of what has to be drawn.
    val firstX = floor((state.cameraX - halfW - cardW) / stepX).toInt()
    val lastX = ceil((state.cameraX + halfW + cardW) / stepX).toInt()
    val firstY = floor((state.cameraY - halfH - cardH) / stepY).toInt()
    val lastY = ceil((state.cameraY + halfH + cardH) / stepY).toInt()

    for (cellY in firstY..lastY) {
        for (cellX in firstX..lastX) {
            val card = cards[pictureFor(cellX, cellY, cards.size)]
            val left = cellX * stepX - cardW / 2f - state.cameraX + halfW
            val top = cellY * stepY - cardH / 2f - state.cameraY + halfH

            // Scaled rather than drawn into an integer destination rect, so the
            // drift stays smooth instead of stepping a pixel at a time.
            withTransform({
                translate(left, top)
                scale(cardW / card.width, cardH / card.height, Offset.Zero)
            }) {
                drawImage(image = card, topLeft = Offset.Zero)
            }
        }
    }
}

/**
 * Which picture a cell shows. The reference builds one five-by-five block and
 * teleports its cards around the camera; the lattice here is unbounded instead,
 * so the block is reproduced by taking the cell's position within it — same
 * arrangement, same period, without anything to keep in step.
 */
private fun pictureFor(cellX: Int, cellY: Int, count: Int): Int =
    (floorMod(cellX, BLOCK) * BLOCK + floorMod(cellY, BLOCK)) % count

private fun floorMod(value: Int, by: Int): Int {
    val m = value % by
    return if (m < 0) m + by else m
}
