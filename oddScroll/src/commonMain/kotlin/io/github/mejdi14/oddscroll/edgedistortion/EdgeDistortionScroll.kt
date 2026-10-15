package io.github.mejdi14.oddscroll.edgedistortion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.mejdi14.oddscroll.internal.EdgeWarp
import io.github.mejdi14.oddscroll.internal.cardBitmap
import io.github.mejdi14.oddscroll.internal.clamped
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Subdivision of a warped card. The fisheye varies across a card that covers
 * most of a phone's width, so one quad cannot carry it: each tile is drawn with
 * its own affine transform taken from where its own edges land, and the curve
 * shows up in the difference between neighbours. Six is the point where the
 * facets stop being visible along a card's outline; raising it costs draws for
 * nothing. Tiles overlap slightly or hairline seams show between them.
 */
private const val TILES = 6
private const val TILE_OVERLAP = 0.6f

/** Under this the card sits in the undistorted middle and goes down as one quad. */
private const val FLAT_ENOUGH = 0.002f

/** Under this the fringe lands inside a pixel; not worth three passes. */
private const val FRINGE_FLOOR = 0.04f

/** The reference lattice repeats on this period, as the original's wrap does. */
private const val BLOCK = 5

private val RedOnly = channel(1f, 0f, 0f)
private val GreenOnly = channel(0f, 1f, 0f)
private val BlueOnly = channel(0f, 0f, 1f)

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
 * drawn, each taking its picture from the supplied set by position on a
 * five-by-five period. Pan far enough in any direction and the pictures come
 * round again, but no edge is ever reached.
 *
 * The borders carry a fisheye and a colour fringe. Both are screen-space
 * treatments in their usual form — one pass over the rendered frame — and are
 * done here as geometry instead, since the cards *are* the frame: the fisheye
 * by subdividing each card and warping the pieces, the fringe by laying the red
 * and blue channels down at slightly different scales about the centre.
 *
 * Left to itself the lattice drifts diagonally. A drag takes it over and the
 * drift resumes on release.
 *
 * @param images pictures to lay over the lattice, cover-fitted to [cardRatio].
 * @param background the page colour behind the cards.
 * @param cardHeight card height as a share of the viewport's height.
 * @param cardRatio card width / height.
 * @param gapX space between columns as a share of the card's width.
 * @param gapY space between rows as a share of the card's height.
 * @param corner corner rounding as a share of the card's shorter side.
 * @param distortion how hard the borders bend. 0 leaves the frame flat.
 * @param aberration colour fringe width as a share of the viewport's width.
 *   0 drops it, and with it two of every three draws along the border.
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
        Box(modifier.background(background))
        return
    }

    val cards = remember(images, cardRatio, corner) {
        images.map { cardBitmap(it, cardRatio, corner) }
    }
    val warp = remember(distortion) { EdgeWarp(strength = distortion) }

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

    Box(modifier.background(background)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
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
            drawLattice(cards, state, warp, cardHeight, cardRatio, gapX, gapY, aberration)
        }
    }
}

// --- the lattice -------------------------------------------------------------

private fun DrawScope.drawLattice(
    cards: List<ImageBitmap>,
    state: EdgeDistortionScrollState,
    warp: EdgeWarp,
    cardHeight: Float,
    cardRatio: Float,
    gapX: Float,
    gapY: Float,
    aberration: Float,
) {
    val halfW = size.width / 2f
    val halfH = size.height / 2f
    if (halfW <= 0f || halfH <= 0f) return

    val cardH = cardHeight * size.height
    val cardW = cardH * cardRatio
    val stepX = cardW * (1f + gapX)
    val stepY = cardH * (1f + gapY)
    if (stepX <= 0f || stepY <= 0f) return

    // Which cells the viewport covers. The warp only ever pulls content inward,
    // so nothing from outside the undistorted view can appear inside the frame
    // and the plain rectangle is the whole of what has to be considered.
    val firstX = floor((state.cameraX - halfW - cardW) / stepX).toInt()
    val lastX = ceil((state.cameraX + halfW + cardW) / stepX).toInt()
    val firstY = floor((state.cameraY - halfH - cardH) / stepY).toInt()
    val lastY = ceil((state.cameraY + halfH + cardH) / stepY).toInt()

    // The fringe is specified against the viewport's width in uv, where the
    // frame spans one; as a scale about the centre it spans two, so it doubles.
    val fringeScale = aberration * 2f
    val matrix = Matrix()

    for (cellY in firstY..lastY) {
        for (cellX in firstX..lastX) {
            val centreX = cellX * stepX
            val centreY = cellY * stepY
            val card = cards[pictureFor(cellX, cellY, cards.size)]

            // Normalised position of a point of this card, after the warp.
            fun warped(u: Float, v: Float): Offset {
                val sceneX = centreX + (u - 0.5f) * cardW
                val sceneY = centreY + (v - 0.5f) * cardH
                return warp.forward(
                    (sceneX - state.cameraX) / halfW,
                    (sceneY - state.cameraY) / halfH,
                )
            }

            val topLeft = warped(0f, 0f)
            val topRight = warped(1f, 0f)
            val bottomLeft = warped(0f, 1f)
            val bottomRight = warped(1f, 1f)
            if (offFrame(topLeft, topRight, bottomLeft, bottomRight)) continue

            val bend = max(
                max(warp.edgeAt(warp.radiusOf(topLeft)), warp.edgeAt(warp.radiusOf(topRight))),
                max(
                    warp.edgeAt(warp.radiusOf(bottomLeft)),
                    warp.edgeAt(warp.radiusOf(bottomRight)),
                ),
            )
            // A card sitting entirely in the flat middle needs no subdivision:
            // its four corners already describe it exactly.
            val tiles = if (bend < FLAT_ENOUGH) 1 else TILES
            val pad = TILE_OVERLAP / tiles / 2f

            for (ty in 0 until tiles) {
                for (tx in 0 until tiles) {
                    val u0 = tx / tiles.toFloat()
                    val u1 = (tx + 1) / tiles.toFloat()
                    val v0 = ty / tiles.toFloat()
                    val v1 = (ty + 1) / tiles.toFloat()

                    val sxA = (u0 * card.width).roundToInt()
                    val sxB = (u1 * card.width).roundToInt()
                    val syA = (v0 * card.height).roundToInt()
                    val syB = (v1 * card.height).roundToInt()
                    val srcW = (sxB - sxA).coerceAtLeast(1)
                    val srcH = (syB - syA).coerceAtLeast(1)

                    val uMid = (u0 + u1) * 0.5f
                    val vMid = (v0 + v1) * 0.5f
                    val left = warped(u0 - pad, vMid)
                    val right = warped(u1 + pad, vMid)
                    val top = warped(uMid, v0 - pad)
                    val bottom = warped(uMid, v1 + pad)

                    val colX = (right.x - left.x) * halfW
                    val colY = (right.y - left.y) * halfH
                    val rowX = (bottom.x - top.x) * halfW
                    val rowY = (bottom.y - top.y) * halfH
                    if (abs(colX) + abs(colY) < 0.05f) continue

                    val originX = (top.x * 0.5f + 0.5f) * size.width - colX * 0.5f
                    val originY = (top.y * 0.5f + 0.5f) * size.height - colY * 0.5f
                    // Most of a lattice is off screen even after the cards are
                    // culled; the corners of this parallelogram are exactly
                    // where the tile lands, so the test is free.
                    if (tileOffFrame(originX, originY, colX, colY, rowX, rowY)) continue

                    matrix.reset()
                    matrix[0, 0] = colX / srcW
                    matrix[0, 1] = colY / srcW
                    matrix[1, 0] = rowX / srcH
                    matrix[1, 1] = rowY / srcH
                    matrix[3, 0] = originX
                    matrix[3, 1] = originY

                    val edge = warp.edgeAt(warp.radiusOf(warped(uMid, vMid)))
                    if (aberration > 0f && edge > FRINGE_FLOOR) {
                        val fringe = fringeScale * edge
                        // Red is sampled further out by the screen-space
                        // original, which lands it further IN; blue the other
                        // way. Lighten rather than an additive blend: it gives
                        // the same result for channels that do not overlap,
                        // and unlike addition it is unchanged by drawing the
                        // same pixel twice, which the tile overlap does
                        // constantly.
                        tile(card, matrix, sxA, syA, srcW, srcH, GreenOnly, 1f, BlendMode.SrcOver)
                        tile(card, matrix, sxA, syA, srcW, srcH, RedOnly, 1f - fringe, BlendMode.Lighten)
                        tile(card, matrix, sxA, syA, srcW, srcH, BlueOnly, 1f + fringe, BlendMode.Lighten)
                    } else {
                        tile(card, matrix, sxA, syA, srcW, srcH, null, 1f, BlendMode.SrcOver)
                    }
                }
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

/**
 * One tile, optionally through a single colour channel and scaled about the
 * centre of the frame. The scale goes outside the tile's own transform, so it
 * acts on where the tile lands rather than on the tile's shape.
 */
private fun DrawScope.tile(
    card: ImageBitmap,
    matrix: Matrix,
    srcX: Int,
    srcY: Int,
    srcW: Int,
    srcH: Int,
    channel: ColorFilter?,
    scale: Float,
    blendMode: BlendMode,
) {
    withTransform({
        if (scale != 1f) scale(scale, scale, Offset(size.width / 2f, size.height / 2f))
        transform(matrix)
    }) {
        drawImage(
            image = card,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(srcW, srcH),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(srcW, srcH),
            colorFilter = channel,
            blendMode = blendMode,
            filterQuality = FilterQuality.Medium,
        )
    }
}

/** True when all four corners fall off the same side of the frame. */
private fun offFrame(a: Offset, b: Offset, c: Offset, d: Offset): Boolean {
    val margin = 1.05f
    val minX = min(min(a.x, b.x), min(c.x, d.x))
    val maxX = max(max(a.x, b.x), max(c.x, d.x))
    val minY = min(min(a.y, b.y), min(c.y, d.y))
    val maxY = max(max(a.y, b.y), max(c.y, d.y))
    return minX > margin || maxX < -margin || minY > margin || maxY < -margin
}

private fun DrawScope.tileOffFrame(
    originX: Float,
    originY: Float,
    colX: Float,
    colY: Float,
    rowX: Float,
    rowY: Float,
): Boolean {
    val margin = 2f
    val xs0 = originX
    val xs1 = originX + colX
    val xs2 = originX + rowX
    val xs3 = originX + colX + rowX
    if (min(min(xs0, xs1), min(xs2, xs3)) > size.width + margin) return true
    if (max(max(xs0, xs1), max(xs2, xs3)) < -margin) return true
    val ys0 = originY
    val ys1 = originY + colY
    val ys2 = originY + rowY
    val ys3 = originY + colY + rowY
    if (min(min(ys0, ys1), min(ys2, ys3)) > size.height + margin) return true
    if (max(max(ys0, ys1), max(ys2, ys3)) < -margin) return true
    return false
}

private fun floorMod(value: Int, by: Int): Int {
    val m = value % by
    return if (m < 0) m + by else m
}

/** Keeps one channel and discards the other two, alpha untouched. */
private fun channel(r: Float, g: Float, b: Float): ColorFilter =
    ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                r, 0f, 0f, 0f, 0f,
                0f, g, 0f, 0f, 0f,
                0f, 0f, b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
