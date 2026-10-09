package io.github.mejdi14.oddscroll.helix

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mejdi14.oddscroll.internal.HelixProjector
import io.github.mejdi14.oddscroll.internal.NEAR
import io.github.mejdi14.oddscroll.internal.clamped
import io.github.mejdi14.oddscroll.internal.smoothstep
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** One card of the column. */
data class HelixItem(
    val image: ImageBitmap,
    /** Shown while this card is at the front. */
    val title: String,
)

/* Rendering. A card is drawn as a run of narrow vertical strips, each with its
   own affine transform, which is what maps it onto the surface of the cylinder
   it orbits: the outer edges retreat in depth while the middle stays square to
   the camera. Strips overlap slightly or hairline seams show between them. */
private const val STRIPS = 26
private const val STRIP_OVERLAP = 0.7f

/* Depth cueing. Contrast is reduced with distance so the far side of the spiral
   recedes into haze, rather than presenting as equally vivid cards that are
   simply drawn smaller. */
private const val FOG_NEAR = 8f
private const val FOG_FAR = 20.6f
private const val DIM_FADE = 0.62f

/* The border treatment. A card is taken down toward the page colour as it
   approaches the upper and lower edges of the frame, so the column reads as
   continuing past them instead of being cut off by them. */
private const val EDGE_POWER = 1.65f
private const val FADE_STRENGTH = 0.92f
private const val SMEAR_REACH = 0.045f
private const val SMEAR_ALPHA = 0.34f

private const val CLICK_SLOP = 6f

private val HudPadding = Modifier.padding(horizontal = 22.dp, vertical = 28.dp)

@Composable
fun rememberHelixScrollState(count: Int, entry: Boolean = true): HelixScrollState =
    remember(count, entry) { HelixScrollState(count.coerceAtLeast(1), entry) }

/**
 * A spiral column of cards, turned by dragging or scrolling.
 *
 * No card is a flat rectangle: each is mapped onto the surface of the cylinder
 * it orbits, so the outer edges retreat in depth while the middle stays square
 * to the camera, and the extremities trail behind the centre while the column is
 * turning.
 *
 * The column settles on the nearest card once input stops. Tap a card to bring
 * it to the front.
 *
 * @param items cards of the column, in order around the helix.
 * @param brand wordmark in the top-left. Omit to drop it.
 * @param background the page colour the column fades into.
 * @param contentColor colour of the wordmark, title and counter.
 * @param focusBand how much of the frame's height stays lit before the fade
 *   starts, 0-1.
 * @param twist turn of the helix between one card and the next, in radians.
 * @param rise rise of the helix between one card and the next, in card heights.
 * @param cardRatio card width / height. Art is cover-fitted into it.
 * @param entry play the arrival — the column glides in from behind.
 * @param hud draw the wordmark, title and counter.
 */
@Composable
fun HelixScroll(
    items: List<HelixItem>,
    modifier: Modifier = Modifier,
    brand: String? = null,
    background: Color = Color(0xFF0A0A0B),
    contentColor: Color = Color(0xFFF3F0E9),
    focusBand: Float = 0.25f,
    twist: Float = 0.8f,
    rise: Float = 0.79f,
    cardRatio: Float = 2f,
    entry: Boolean = true,
    hud: Boolean = true,
    state: HelixScrollState = rememberHelixScrollState(items.size, entry),
) {
    if (items.isEmpty()) {
        Box(modifier.background(background))
        return
    }

    val projector = remember { HelixProjector() }

    LaunchedEffect(state) {
        var previous = 0L
        while (true) {
            withFrameNanos { frame ->
                val deltaMs =
                    if (previous == 0L) 16.67f else (frame - previous) / 1_000_000f
                previous = frame
                state.advance(
                    nowMs = frame / 1_000_000f,
                    deltaMs = deltaMs.clamped(1f, 50f),
                )
            }
        }
    }

    Box(modifier.background(background)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .pointerInput(items.size, state) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var travel = 0f
                        var last = down.position.y
                        var dragging = false
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) break
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: break
                            if (!change.pressed) break
                            val dy = change.position.y - last
                            last = change.position.y
                            travel += abs(dy)
                            if (travel > CLICK_SLOP) {
                                dragging = true
                                state.onDrag(dy)
                                change.consume()
                            }
                        }
                        if (dragging) {
                            state.onDragEnd()
                        } else {
                            val hit = projector.pick(down.position, state, items.size)
                            if (hit >= 0) state.bringToFront(hit)
                        }
                    }
                }
                .pointerInput(state) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val scroll = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (scroll == 0f) continue
                            state.onWheel(-scroll * 100f)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            state.tick // read, so the column redraws every frame while it moves
            projector.width = size.width
            projector.height = size.height
            projector.count = items.size
            projector.twist = twist
            projector.rise = rise
            projector.cardRatio = cardRatio

            drawColumn(items, state, projector, background, focusBand)
            drawBorderFade(background, focusBand)
        }

        if (hud) {
            val active = state.active.coerceIn(items.indices)
            Hud(
                brand = brand,
                title = items[active].title,
                index = active,
                count = items.size,
                contentColor = contentColor,
            )
        }
    }
}

// --- the cards ---------------------------------------------------------------

private fun DrawScope.drawColumn(
    items: List<HelixItem>,
    state: HelixScrollState,
    projector: HelixProjector,
    background: Color,
    focusBand: Float,
) {
    val count = items.size
    // Back to front, so nearer cards cover the ones they orbit in front of.
    val order = (0 until count).sortedByDescending {
        projector.depthOf(projector.slotOf(it, state.progress))
    }

    for (index in order) {
        val slot = projector.slotOf(index, state.progress)
        val away = projector.depthOf(slot)
        if (away <= NEAR) continue

        val fog = smoothstep(FOG_NEAR, FOG_FAR, away)
        // Gamma-corrected so the parameter behaves perceptually: a linear
        // half-mix registers as roughly a quarter as dark.
        val dim = 1f - (1f - state.dim[index] * DIM_FADE).pow(2.2f)
        val wash = 1f - (1f - fog) * (1f - dim)

        val centre = projector.project(slot, 0.5f, 0.5f, state.bend)
        val borderDistance = abs(centre.y / size.height - 0.5f) * 2f
        val smear = smoothstep(focusBand, 1f, borderDistance)

        // Vertical ghosting, taken only along the column's own axis. A radially
        // symmetric blur would suggest a lens out of focus; a stretched one
        // suggests displacement.
        if (smear > 0.04f) {
            val reach = smear * size.height * SMEAR_REACH
            for (ghost in intArrayOf(-1, 1)) {
                translate(top = ghost * reach) {
                    drawCard(
                        items[index].image, slot, projector, state.bend,
                        background, wash, SMEAR_ALPHA * smear,
                    )
                }
            }
        }

        drawCard(items[index].image, slot, projector, state.bend, background, wash, 1f)
    }
}

/**
 * A card, as a run of narrow vertical strips. Each strip's destination is a
 * parallelogram: its width comes from where the strip's two edges land, and its
 * height and shear from where its top and bottom centres land, which together
 * carry the cylinder's curvature, the per-strip depth scaling and the trailing
 * deformation.
 */
private fun DrawScope.drawCard(
    image: ImageBitmap,
    slot: Float,
    projector: HelixProjector,
    bend: Float,
    background: Color,
    wash: Float,
    alpha: Float,
) {
    // Cover-fit, so nothing is stretched.
    val ratio = projector.cardRatio
    val imageAspect = image.width.toFloat() / max(image.height, 1)
    val coverW: Int
    val coverH: Int
    if (imageAspect < ratio) {
        coverW = image.width
        coverH = (image.width / ratio).roundToInt().coerceIn(1, image.height)
    } else {
        coverH = image.height
        coverW = (image.height * ratio).roundToInt().coerceIn(1, image.width)
    }
    val coverX = (image.width - coverW) / 2
    val coverY = (image.height - coverH) / 2

    val pad = STRIP_OVERLAP / STRIPS / 2f
    val matrix = Matrix()

    for (strip in 0 until STRIPS) {
        val ax0 = strip / STRIPS.toFloat()
        val ax1 = (strip + 1) / STRIPS.toFloat()

        val sxA = coverX + (ax0 * coverW).roundToInt()
        val sxB = coverX + (ax1 * coverW).roundToInt()
        val srcW = (sxB - sxA).coerceAtLeast(1)

        val edgeL = projector.project(slot, ax0 - pad, 0.5f, bend)
        val edgeR = projector.project(slot, ax1 + pad, 0.5f, bend)
        val mid = (ax0 + ax1) * 0.5f
        val top = projector.project(slot, mid, 1f, bend)
        val bottom = projector.project(slot, mid, 0f, bend)

        val colX = edgeR.x - edgeL.x
        val colY = edgeR.y - edgeL.y
        val rowX = bottom.x - top.x
        val rowY = bottom.y - top.y
        if (hypot(colX, colY) < 0.05f) continue

        matrix.reset()
        matrix[0, 0] = colX / srcW
        matrix[0, 1] = colY / srcW
        matrix[1, 0] = rowX / coverH
        matrix[1, 1] = rowY / coverH
        matrix[3, 0] = top.x - colX * 0.5f
        matrix[3, 1] = top.y - colY * 0.5f

        withTransform({ transform(matrix) }) {
            drawImage(
                image = image,
                srcOffset = IntOffset(sxA, coverY),
                srcSize = IntSize(srcW, coverH),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(srcW, coverH),
                alpha = alpha,
                filterQuality = FilterQuality.Medium,
            )
            if (wash > 0.002f) {
                drawRect(
                    color = background.copy(alpha = wash * alpha),
                    topLeft = Offset.Zero,
                    size = Size(srcW.toFloat(), coverH.toFloat()),
                )
            }
        }
    }
}

// --- the border fade ---------------------------------------------------------

/**
 * Takes the column down to the page colour at the top and bottom of the frame.
 * It keys solely off where a pixel sits, so it runs as one gradient over the
 * composited column rather than as a stage of each card — which also keeps the
 * two edges in step however the cards happen to overlap there.
 */
private fun DrawScope.drawBorderFade(background: Color, focusBand: Float) {
    val stops = Array(17) { i ->
        val y = i / 16f
        val d = abs(y - 0.5f) * 2f
        y to background.copy(alpha = smoothstep(focusBand, 1f, d).pow(EDGE_POWER) * FADE_STRENGTH)
    }
    drawRect(
        brush = Brush.verticalGradient(colorStops = stops),
        blendMode = BlendMode.SrcAtop,
    )
}

// --- hit testing -------------------------------------------------------------

/**
 * Closest card whose outline contains the pointer. The layout is columnar, so
 * the projected outline is enough and no ray cast is needed.
 */
private fun HelixProjector.pick(at: Offset, state: HelixScrollState, count: Int): Int {
    var best = -1
    var bestAway = Float.MAX_VALUE
    for (index in 0 until count) {
        val slot = slotOf(index, state.progress)
        val away = depthOf(slot)
        if (away <= NEAR || away >= bestAway) continue
        if (outlinePoints(slot, state.bend).surrounds(at)) {
            bestAway = away
            best = index
        }
    }
    return best
}

/**
 * The card's projected silhouette as a flat x,y run, sampled along its curved
 * top and then back along its bottom.
 */
private fun HelixProjector.outlinePoints(slot: Float, bend: Float): FloatArray {
    val steps = 10
    val points = FloatArray((steps + 1) * 4)
    var at = 0
    for (i in 0..steps) {
        val p = project(slot, i / steps.toFloat(), 1f, bend)
        points[at++] = p.x
        points[at++] = p.y
    }
    for (i in steps downTo 0) {
        val p = project(slot, i / steps.toFloat(), 0f, bend)
        points[at++] = p.x
        points[at++] = p.y
    }
    return points
}

/** Even-odd ray cast against the outline run. */
private fun FloatArray.surrounds(point: Offset): Boolean {
    var inside = false
    val n = size / 2
    var j = n - 1
    for (i in 0 until n) {
        val xi = this[i * 2]
        val yi = this[i * 2 + 1]
        val xj = this[j * 2]
        val yj = this[j * 2 + 1]
        if ((yi > point.y) != (yj > point.y) &&
            point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

// --- hud ---------------------------------------------------------------------

@Composable
private fun BoxScope.Hud(
    brand: String?,
    title: String,
    index: Int,
    count: Int,
    contentColor: Color,
) {
    if (brand != null) {
        BasicText(
            text = brand,
            modifier = Modifier.align(Alignment.TopStart).then(HudPadding),
            style = TextStyle(
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
    BasicText(
        text = title,
        modifier = Modifier.align(Alignment.BottomStart).then(HudPadding),
        style = TextStyle(
            color = contentColor,
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
    BasicText(
        text = "${pad2(index + 1)} / ${pad2(count)}",
        modifier = Modifier.align(Alignment.BottomEnd).then(HudPadding),
        style = TextStyle(
            color = contentColor.copy(alpha = 0.55f),
            fontSize = 13.sp,
        ),
    )
}

private fun pad2(value: Int): String = if (value < 10) "0$value" else value.toString()
