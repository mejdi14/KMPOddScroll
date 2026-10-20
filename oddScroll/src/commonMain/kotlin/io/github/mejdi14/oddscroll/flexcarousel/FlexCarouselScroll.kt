package io.github.mejdi14.oddscroll.flexcarousel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.graphicsLayer
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
import io.github.mejdi14.oddscroll.internal.SplitBy
import io.github.mejdi14.oddscroll.internal.SplitText
import io.github.mejdi14.oddscroll.internal.inOutCubic
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One card of the row. */
data class FlexCarouselItem(
    val image: ImageBitmap,
    /** Shown while this card is at the front. */
    val title: String = "",
    /** Shown beside the card once it is opened. See [FlexFocus]. */
    val description: String = "",
)

/** How the row rolls past the glass. */
enum class FlexCurl { Twist, Rise, Fall }

/** How a card is proportioned, whatever its picture's own shape. */
enum class FlexFit { Natural, Portrait, Square, Landscape }

/** Where an opened card goes, and whether its text comes with it. */
enum class FlexFocus {
    /** It stays in the middle of the frame and fills what it can of it. */
    Centred,

    /** It slides to one side and its title and description take the other. */
    Beside,

    /** Beside on a frame wider than it is tall, centred otherwise. A tall frame
        has no room to put text next to a card worth looking at. */
    Auto,
}

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
 * @param bend how far the glass drags what passes through it.
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
internal expect fun Modifier.liquidLens(lens: FlexLens, strength: () -> Float): Modifier

/** Copies either side of the row, so the loop has no visible end. */
private const val REPEATS = 3

/** The picture is held slightly over size, leaving room to slide inside the card. */
private const val OVERSCAN = 1.08f

/** How far along the row a card has to be before its picture is fully offset. */
private const val PARALLAX_SPAN = 0.75f

private const val INTRO_SECONDS = 2.1f

/** Share of the frame the opened card keeps when its text sits beside it. */
private const val FOCUS_SPLIT = 0.5f

@Composable
fun rememberFlexCarouselScrollState(count: Int): FlexCarouselScrollState =
    remember(count) { FlexCarouselScrollState(count.coerceAtLeast(1)) }

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
 * @param focusOnClick tapping the centred card opens it; tapping again closes.
 * @param focus where an opened card goes. [FlexFocus.Beside] slides it to the
 *   left and gives the room it leaves to its title and description.
 * @param autoplay step along on a timer while untouched.
 * @param interval seconds between autoplay steps.
 * @param captions draw the title and counter.
 * @param intro play the arrival — the row rises into place.
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
    focusOnClick: Boolean = true,
    focus: FlexFocus = FlexFocus.Auto,
    autoplay: Boolean = false,
    interval: Float = 4f,
    captions: Boolean = true,
    intro: Boolean = true,
    state: FlexCarouselScrollState = rememberFlexCarouselScrollState(items.size),
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
    SideEffect { state.metrics = metrics }

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

    // Nothing here reads how far open the card is. Those values move every
    // frame, and reading one in composition would rebuild the whole carousel —
    // canvas, gestures, panel and all — sixty times a second for the length of
    // the animation. They are read inside layers and inside the draw instead,
    // and the only thing composition is told is whether the panel exists.
    val panelOn by remember(state) {
        derivedStateOf { state.opening && state.asideAmount > 0.004f }
    }
    val beside = when (focus) {
        FlexFocus.Beside -> true
        FlexFocus.Centred -> false
        FlexFocus.Auto -> viewport.width > viewport.height
    }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it }
                .liquidLens(lens) { 1f - inOutCubic(state.focusAmount.clamped(0f, 1f)) }
                .pointerInput(state, focusOnClick) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        state.onDragStart()
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
                            if (travel > 6f) {
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
                        if (travel > 6f) {
                            state.onDragEnd(speed)
                        } else {
                            val hit = state.placed.firstOrNull {
                                down.position.x >= it.left && down.position.x <= it.right
                            }
                            if (hit != null) {
                                state.onTap(hit.index, hit.rel, focusOnClick)
                            } else {
                                state.closeFocus()
                            }
                        }
                    }
                }
                .pointerInput(state) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val scroll = event.changes.firstOrNull()?.scrollDelta
                                ?: continue
                            val delta = if (scroll.x != 0f) scroll.x else scroll.y
                            if (delta == 0f) continue
                            state.onWheel(delta * 60f)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            state.tick // read, so the row redraws every frame while it settles
            drawRect(background)
            drawRow(items, state, squeeze, radiusPx, intro, beside)
        }

        if (captions) {
            Caption(
                title = items[state.active.coerceIn(items.indices)].title,
                index = state.active.coerceIn(items.indices),
                count = items.size,
                contentColor = contentColor,
                // The panel says all of this and more, so the caption gives way
                // to it rather than repeating it along the bottom.
                alpha = {
                    if (beside) 1f - inOutCubic(state.focusAmount.clamped(0f, 1f)) else 1f
                },
            )
        }
        if (beside && panelOn) {
            val shown = state.focusIndex.coerceIn(items.indices)
            FocusPanel(
                title = items[shown].title,
                description = items[shown].description,
                contentColor = contentColor,
                // Laid out as the card sets off and held until it lands. Doing
                // the measuring up front is the point: building eighty pieces
                // of text in the frame the card arrives is what made the
                // arrival stutter.
                arrived = { state.asideAmount > 0.5f },
            )
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
    radiusPx: Float,
    intro: Boolean,
    beside: Boolean,
) {
    val m = state.metrics ?: return
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return

    val homeX = width / 2f
    val homeY = height / 2f
    val shrink = 1f - squeeze.clamped(0f, 1f) * state.energy

    val focus = state.focusAmount.clamped(0f, 1f)
    val focusEase = inOutCubic(focus)
    val focusWidth = state.focusIndex.let {
        if (it in m.widths.indices) m.widths[it] else m.cardH
    }
    val aside = if (beside) inOutCubic(state.asideAmount.clamped(0f, 1f)) else 0f

    // Opened as far as the frame allows and no further, so a wide card and a
    // narrow one both land inside it. There are two of these: the card arrives
    // at the size the whole frame allows, and then gives up what it has to in
    // order to fit its own half of it.
    fun openedTo(lane: Float) = min(
        min(1.3f, height * 0.84f / m.cardH),
        lane * 0.92f / focusWidth,
    ).coerceAtLeast(1f)

    val middleScale = openedTo(width)
    val asideScale = if (beside) openedTo(width * FOCUS_SPLIT) else middleScale
    val focusHome = if (beside) width * FOCUS_SPLIT / 2f else homeX

    val arrival = if (!intro) 1f else (state.introAge / INTRO_SECONDS).clamped(0f, 1f)

    state.placed.clear()
    val path = Path()

    // Far cards first, so the centre of the row sits over its neighbours.
    val draws = ArrayList<Placement>()
    for (i in items.indices) {
        val base = wrapAround(m.centers[i] - state.position, m.loop)
        for (copy in -REPEATS..REPEATS) {
            val rel = base + copy * m.loop
            if (abs(rel) - m.widths[i] / 2f > width + 40f) continue

            var x = homeX + rel
            var scale = shrink
            var alpha = 1f
            var y = homeY

            if (intro && arrival < 1f) {
                // The row rises into place, the middle of it arriving first.
                val lag = (min(abs(rel) / max(width, 1f), 1f) * 0.35f)
                val t = ((arrival - lag) / (1f - lag).coerceAtLeast(0.01f)).clamped(0f, 1f)
                val eased = inOutCubic(t)
                y += (1f - eased) * height * 0.3f
                alpha *= eased
                scale *= 0.92f + 0.08f * eased
            }

            if (focus > 0f) {
                if (i == state.focusIndex && abs(rel) < m.widths[i]) {
                    // Two steps, not one: it opens into the middle at the size
                    // the frame allows, and only then moves over and tucks down
                    // to what its half of the frame allows.
                    val middle = 1f + (middleScale - 1f) * focusEase
                    val tucked = 1f + (asideScale - 1f) * focusEase
                    scale *= middle + (tucked - middle) * aside
                    x += (focusHome - homeX) * aside
                } else {
                    // Everything else is swept out of the way, the near cards
                    // leaving first so the opening reads as an order.
                    val order = min(abs(rel) / max(width, 1f), 1f) * 0.25f
                    val part = inOutCubic((focus * 1.25f - order).clamped(0f, 1f))
                    x += (if (rel >= 0f) 1f else -1f) * part * width * 0.7f
                    alpha *= 1f - part
                }
            }

            val cw = m.widths[i] * scale
            val ch = m.cardH * scale
            if (alpha <= 0.004f || x + cw / 2f < -40f || x - cw / 2f > width + 40f) continue
            draws.add(Placement(i, rel, x, y, cw, ch, alpha))
        }
    }
    draws.sortByDescending { abs(it.rel) }

    for (d in draws) {
        val image = items[d.i].image
        val left = d.x - d.cw / 2f
        val top = d.y - d.ch / 2f

        // Cover-fit, held slightly over size, then slid along by where the card
        // sits in the row — the picture lags its own frame, which is what makes
        // a flat strip of cards read as depth.
        val cardAspect = d.cw / max(d.ch, 1f)
        val imageAspect = image.width.toFloat() / max(image.height, 1)
        var sw = image.width.toFloat()
        var sh = image.height.toFloat()
        if (imageAspect > cardAspect) sw = sh * cardAspect else sh = sw / cardAspect
        sw /= OVERSCAN
        sh /= OVERSCAN
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
                    alpha = d.alpha,
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
    val y: Float,
    val cw: Float,
    val ch: Float,
    val alpha: Float,
)

// --- caption -----------------------------------------------------------------

@Composable
private fun BoxScope.Caption(
    title: String,
    index: Int,
    count: Int,
    contentColor: Color,
    alpha: () -> Float,
) {
    if (title.isNotEmpty()) {
        BasicText(
            text = title,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 24.dp, vertical = 28.dp)
                .graphicsLayer { this.alpha = alpha() },
            style = TextStyle(
                color = contentColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
    BasicText(
        text = "${pad2(index + 1)} / ${pad2(count)}",
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(horizontal = 24.dp, vertical = 28.dp)
            .graphicsLayer { this.alpha = alpha() },
        style = TextStyle(color = contentColor.copy(alpha = 0.55f), fontSize = 13.sp),
    )
}

/**
 * The half of the frame the opened card left behind.
 *
 * Nothing is composed here until the card is most of the way over, and the text
 * then lets itself in a piece at a time, so the two read as one gesture — the
 * card clearing the space and the text arriving into it.
 *
 * The title comes in by the letter and the description by the word. By the
 * letter is the finer effect and it is what a title is short enough for; across
 * a paragraph it reads as a machine typing rather than as text arriving.
 */
@Composable
private fun BoxScope.FocusPanel(
    title: String,
    description: String,
    contentColor: Color,
    arrived: () -> Boolean,
) {
    Column(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxWidth(1f - FOCUS_SPLIT)
            .padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
        SplitText(
            text = title,
            style = TextStyle(
                color = contentColor,
                fontSize = 26.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Medium,
            ),
            splitBy = SplitBy.Chars,
            rise = 26.dp,
            startWhen = arrived,
        )
        if (description.isNotEmpty()) {
            SplitText(
                text = description,
                modifier = Modifier.padding(top = 14.dp),
                style = TextStyle(
                    color = contentColor.copy(alpha = 0.62f),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                ),
                splitBy = SplitBy.Words,
                rise = 18.dp,
                startWhen = arrived,
                // Held back until the title is on its way, so the panel fills
                // from the top down rather than all at once.
                startDelay = 260,
            )
        }
    }
}

private fun pad2(value: Int): String = if (value < 10) "0$value" else value.toString()

