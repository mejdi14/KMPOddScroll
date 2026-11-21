package io.github.mejdi14.oddscroll.flexcarousel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mejdi14.oddscroll.internal.clamped
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One card of the row.
 *
 * @param image the picture. Its own proportions set the card's width unless
 *   the row's `fit` overrides them.
 * @param title shown along the bottom while this card is at the front, when
 *   captions are on.
 */
data class FlexCarouselItem(
    val image: ImageBitmap,
    val title: String = "",
)

/** How the row rolls past the glass. */
enum class FlexCurl { Twist, Rise, Fall }

/** How a card is proportioned, whatever its picture's own shape. */
enum class FlexFit { Natural, Portrait, Square, Landscape }

/**
 * Shape and behaviour of the invisible glass the row runs through. The named
 * presets are the reference's; the fields are open so one can be adjusted
 * without reaching for a different one.
 *
 * @param width half-width of the lens as a share of the viewport's width.
 * @param height half-height as a share of the frame's *long* side, so the lens
 *   stays taller than a portrait frame instead of banding across the middle of
 *   it. On a landscape frame the long side is the width, which is what the
 *   reference measures both against.
 * @param tilt rotation of the lens, in degrees.
 * @param roundness 1 is an ellipse, 0 a hard rectangle.
 * @param bend how far the glass drags what passes through it. 0 turns the
 *   glass off.
 * @param reach thickness of the band either side of the lens's edge.
 * @param curl which way the row twists as it rolls past.
 * @param dispersion how far the colour splits across the band.
 */
data class FlexLens(
    val width: Float,
    val height: Float,
    val tilt: Float,
    val roundness: Float,
    val bend: Float,
    val reach: Float,
    val curl: FlexCurl,
    val dispersion: Float,
) {
    companion object {
        val Liquid = FlexLens(0.74f, 1.18f, 62f, 1f, 0.34f, 0.38f, FlexCurl.Twist, 0.45f)
        val Ribbon = FlexLens(0.8f, 0.8f, 0f, 1f, 0.34f, 0.34f, FlexCurl.Twist, 0.4f)
        val Vortex = FlexLens(0.7f, 0.95f, 30f, 1f, 0.46f, 0.3f, FlexCurl.Twist, 0.5f)
        val Arch = FlexLens(0.8f, 0.8f, 0f, 1f, 0.3f, 0.36f, FlexCurl.Rise, 0.4f)
    }
}

/**
 * The glass, as a modifier over the finished row. It is a fragment shader where
 * the platform has one and nothing where it does not.
 */
@Composable
internal expect fun Modifier.liquidLens(lens: FlexLens): Modifier

/** Copies either side of the row, so the loop has no visible end. */
private const val REPEATS = 3

/** How far along the row a card has to be before its picture is fully offset. */
private const val PARALLAX_SPAN = 0.75f

/** Pointer travel under which a press is a tap rather than a drag. */
private const val TAP_SLOP = 6f

/** Pixels of row per unit of wheel or trackpad scroll delta. */
private const val WHEEL_GAIN = 60f

/**
 * An infinite row of pictures that flows through invisible liquid glass at its
 * edges.
 *
 * Cards keep their own proportions, so the row is a run of unequal widths
 * rather than a filmstrip, and a step along it is half of this card plus the
 * gap plus half of the next. Position is a spring: a flick, a wheel notch and a
 * tap all arrive as a new goal and settle the same way. The faster the row
 * runs, the narrower every card draws — it gathers itself up when pushed and
 * relaxes once it stops.
 *
 * The glass is the part you cannot see. A superellipse sits over the frame, and
 * in a band either side of its edge the row is dragged along, twisted, and
 * split into colour. It is one shader pass over the finished row, which is the
 * only place that kind of work belongs; where no runtime shader is available
 * the row runs undistorted and everything else behaves the same, see
 * [liquidLens].
 *
 * @param items pictures and their titles, in order along the row.
 * @param modifier applied to the row's frame.
 * @param background the page colour behind the row.
 * @param contentColor colour of the caption and counter.
 * @param lens shape and behaviour of the glass. See [FlexLens].
 * @param cardHeight card height as a share of the viewport's height.
 * @param maxCardWidth the widest a card may draw, as a share of the viewport's
 *   width. It holds [cardHeight] down on a portrait frame, where height alone
 *   would make a card wider than the frame it sits in; on a landscape one the
 *   cap is never reached.
 * @param gap space between cards.
 * @param radius corner rounding. Square by default, as the reference is.
 * @param fit how cards are proportioned; [FlexFit.Natural] keeps each
 *   picture's own shape and gives the row its uneven run of widths.
 * @param squeeze how far cards narrow at full speed, 0 to 1.
 * @param parallax how far a picture slides inside its card as the card crosses
 *   the row, as a share of the card. 0 holds every picture still in its frame.
 * @param captions draw the current card's title and the counter.
 * @param autoplay step along on a timer while untouched.
 * @param interval seconds between autoplay steps.
 * @param stiffness spring stiffness of the row. Higher settles faster and
 *   snappier; the wheel always runs somewhat stiffer than this.
 * @param flingReach how far a throw carries before the row picks a card to
 *   land on, in seconds of the throw's own speed.
 * @param userScrollEnabled false ignores drag, fling and wheel and leaves taps
 *   reporting through [onItemClick] only. The row still moves through [state]
 *   and autoplay.
 * @param onItemClick called with the tapped card's index. When null, a tap
 *   travels the tapped card to the middle, provided [userScrollEnabled].
 * @param state where the row sits, and the handle to move it from outside.
 */
@Composable
fun FlexCarouselScroll(
    items: List<FlexCarouselItem>,
    modifier: Modifier = Modifier,
    background: Color = Color(0xFF060607),
    contentColor: Color = Color(0xFFF2F0EC),
    lens: FlexLens = FlexLens.Liquid,
    cardHeight: Float = 0.5f,
    maxCardWidth: Float = 0.62f,
    gap: Dp = 12.dp,
    radius: Dp = 0.dp,
    fit: FlexFit = FlexFit.Natural,
    squeeze: Float = 0.2f,
    parallax: Float = 0.08f,
    captions: Boolean = true,
    autoplay: Boolean = false,
    interval: Float = 4f,
    stiffness: Float = 55f,
    flingReach: Float = 0.32f,
    userScrollEnabled: Boolean = true,
    onItemClick: ((index: Int) -> Unit)? = null,
    state: FlexCarouselScrollState = rememberFlexCarouselScrollState(),
) {
    if (items.isEmpty()) {
        Box(modifier)
        return
    }

    val density = LocalDensity.current
    val gapPx = with(density) { gap.toPx() }
    val radiusPx = with(density) { radius.toPx() }

    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val aspects = remember(items) {
        FloatArray(items.size) {
            items[it].image.width.toFloat() / max(items[it].image.height, 1)
        }
    }
    val metrics = remember(aspects, viewport, cardHeight, maxCardWidth, gapPx, fit) {
        if (viewport.height <= 0 || viewport.width <= 0) {
            null
        } else {
            buildMetrics(
                aspects = aspects,
                viewportWidth = viewport.width.toFloat(),
                viewportHeight = viewport.height.toFloat(),
                cardHeight = cardHeight,
                gap = gapPx,
                fixedAspect = fit.aspect(),
                maxCardWidth = maxCardWidth,
            )
        }
    }
    SideEffect {
        state.metrics = metrics
        state.stiffness = stiffness
        state.flingReach = flingReach
    }
    val click by rememberUpdatedState(onItemClick)

    LaunchedEffect(state, autoplay, interval) {
        var previous = 0L
        while (true) {
            withFrameNanos { frame ->
                val deltaMs =
                    if (previous == 0L) 16.67f else (frame - previous) / 1_000_000f
                previous = frame
                state.advance(
                    nowMs = frame / 1_000_000f,
                    deltaMs = deltaMs.clamped(1f, 50f),
                    autoplay = autoplay,
                    intervalMs = interval * 1000f,
                )
            }
        }
    }

    // Nothing here reads the position. It moves every frame, and reading it in
    // composition would rebuild the whole carousel — canvas, gestures and all —
    // sixty times a second. The draw reads it instead, and composition only
    // hears about the current index, inside the caption.
    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                .liquidLens(lens)
                .pointerInput(state, userScrollEnabled) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (userScrollEnabled) state.onDragStart()
                        var last = down.position.x
                        var travel = 0f
                        var sampleX = down.position.x
                        var sampleAt = 0L
                        var speed = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) break
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: break
                            if (!change.pressed) break
                            val dx = change.position.x - last
                            last = change.position.x
                            travel += abs(dx)
                            if (travel > TAP_SLOP && userScrollEnabled) {
                                state.onDrag(dx)
                                change.consume()
                            }
                            val at = change.uptimeMillis
                            val span = (at - sampleAt).toFloat()
                            if (span > 8f) {
                                speed = -(change.position.x - sampleX) / span * 1000f
                                sampleX = change.position.x
                                sampleAt = at
                            }
                        }
                        if (travel > TAP_SLOP) {
                            if (userScrollEnabled) state.onDragEnd(speed)
                        } else {
                            if (userScrollEnabled) state.onRelease()
                            val hit = state.placed.firstOrNull {
                                down.position.x >= it.left && down.position.x <= it.right
                            }
                            if (hit != null) {
                                val handler = click
                                when {
                                    handler != null -> handler(hit.index)
                                    userScrollEnabled -> state.travelBy(hit.rel)
                                }
                            }
                        }
                    }
                }
                .then(
                    if (!userScrollEnabled) {
                        Modifier
                    } else {
                        Modifier.pointerInput(state) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type != PointerEventType.Scroll) continue
                                    val scroll = event.changes.firstOrNull()?.scrollDelta
                                        ?: continue
                                    val delta = if (scroll.x != 0f) scroll.x else scroll.y
                                    if (delta == 0f) continue
                                    state.onWheel(delta * WHEEL_GAIN)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                )
        ) {
            state.tick // read, so the row redraws every frame while it settles
            drawRect(background)
            drawRow(items, state, squeeze, parallax, radiusPx)
        }

        if (captions) {
            Caption(items, state, contentColor)
        }
    }
}

private fun FlexFit.aspect(): Float? = when (this) {
    FlexFit.Natural -> null
    FlexFit.Portrait -> 0.75f
    FlexFit.Square -> 1f
    FlexFit.Landscape -> 4f / 3f
}

// --- the row -----------------------------------------------------------------

private fun DrawScope.drawRow(
    items: List<FlexCarouselItem>,
    state: FlexCarouselScrollState,
    squeeze: Float,
    parallax: Float,
    radiusPx: Float,
) {
    val m = state.metrics ?: return
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return

    val homeX = width / 2f
    val homeY = height / 2f
    val scale = 1f - squeeze.clamped(0f, 1f) * state.energy
    // The picture is held over size by this much, leaving it room to slide.
    val overscan = 1f + parallax.coerceAtLeast(0f)

    state.placed.clear()
    val path = Path()

    // Far cards first, so the centre of the row sits over its neighbours.
    val draws = ArrayList<Placement>()
    for (i in items.indices) {
        val base = wrapAround(m.centers[i] - state.position, m.loop)
        for (copy in -REPEATS..REPEATS) {
            val rel = base + copy * m.loop
            if (abs(rel) - m.widths[i] / 2f > width + 40f) continue
            val x = homeX + rel
            val cw = m.widths[i] * scale
            val ch = m.cardH * scale
            if (x + cw / 2f < -40f || x - cw / 2f > width + 40f) continue
            draws.add(Placement(i, rel, x, cw, ch))
        }
    }
    draws.sortByDescending { abs(it.rel) }

    for (d in draws) {
        val image = items[d.i].image
        val left = d.x - d.cw / 2f
        val top = homeY - d.ch / 2f

        // Cover-fit, held slightly over size, then slid along by where the card
        // sits in the row — the picture lags its own frame, which is what makes
        // a flat strip of cards read as depth.
        val cardAspect = d.cw / max(d.ch, 1f)
        val imageAspect = image.width.toFloat() / max(image.height, 1)
        var sw = image.width.toFloat()
        var sh = image.height.toFloat()
        if (imageAspect > cardAspect) sw = sh * cardAspect else sh = sw / cardAspect
        sw /= overscan
        sh /= overscan
        val slide = (d.rel / (size.width * PARALLAX_SPAN)).clamped(-1f, 1f)
        val roomX = image.width - sw
        val srcX = (roomX * 0.5f + slide * roomX * 0.5f).coerceIn(0f, roomX)
        val srcY = ((image.height - sh) * 0.5f).coerceAtLeast(0f)

        val draw: DrawScope.() -> Unit = {
            withTransform({
                translate(left, top)
                scale(d.cw / sw, d.ch / sh, Offset.Zero)
            }) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset(srcX.roundToInt(), srcY.roundToInt()),
                    srcSize = IntSize(sw.roundToInt().coerceAtLeast(1), sh.roundToInt().coerceAtLeast(1)),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(sw.roundToInt().coerceAtLeast(1), sh.roundToInt().coerceAtLeast(1)),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }

        if (radiusPx > 0.5f) {
            val r = min(radiusPx, min(d.cw, d.ch) / 2f)
            path.reset()
            path.addRoundRect(
                RoundRect(
                    Rect(left, top, left + d.cw, top + d.ch),
                    CornerRadius(r, r),
                )
            )
            clipPath(path) { draw() }
        } else {
            draw()
        }

        if (abs(d.rel) < m.loop / 2f) {
            state.placed.add(
                FlexCarouselScrollState.PlacedCard(d.i, left, left + d.cw, d.rel)
            )
        }
    }
}

private class Placement(
    val i: Int,
    val rel: Float,
    val x: Float,
    val cw: Float,
    val ch: Float,
)

// --- caption -----------------------------------------------------------------

/**
 * The current card's title and the counter. Its own composable so that the
 * index changing recomposes only the text, not the row.
 */
@Composable
private fun BoxScope.Caption(
    items: List<FlexCarouselItem>,
    state: FlexCarouselScrollState,
    contentColor: Color,
) {
    val index = state.currentIndex.coerceIn(items.indices)
    val title = items[index].title
    if (title.isNotEmpty()) {
        BasicText(
            text = title,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            style = TextStyle(
                color = contentColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
    BasicText(
        text = "${pad2(index + 1)} / ${pad2(items.size)}",
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        style = TextStyle(color = contentColor.copy(alpha = 0.55f), fontSize = 13.sp),
    )
}

private fun pad2(value: Int): String = if (value < 10) "0$value" else value.toString()
