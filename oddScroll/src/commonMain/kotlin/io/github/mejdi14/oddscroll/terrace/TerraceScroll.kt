package io.github.mejdi14.oddscroll.terrace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as BitmapCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.mejdi14.oddscroll.internal.cardBitmap
import io.github.mejdi14.oddscroll.internal.clamped
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** One card in the stack. */
data class TerraceItem(
    val image: ImageBitmap,
    val title: String = "",
    /** Second line of the caption, under the title. */
    val subtitle: String = "",
)

/*
 * The stack, in card widths, as measured off the reference. Every card edge in
 * 175 frames — widths, and the outer edge of each card where it shows past the
 * one in front — collapses onto one number per frame and five constants, to
 * 0.20 px (max 0.99 px over 2,409 edges). Card `i` is `d = i - position`
 * levels from the front:
 *
 *   scale  = SHRINK^|d|
 *   centre = STEP × d below the middle of the frame
 *
 * so the stack is one rigid rule, not seven animations. A stepped
 * interpolation between fixed slots, the other way to build it, fits no better
 * than 0.51 px with six more numbers — and its fitted slots come back as
 * 0.800, 0.641 and 0.511, which are 0.8, 0.8² and 0.8³ anyway.
 */
private const val SHRINK = 0.801f
private const val STEP = 0.2070f

/** Height over width of a card. */
private const val ASPECT = 1.384f

/*
 * Each card throws a soft shadow down onto the ones behind it, scaled with the
 * card: a gaussian-blurred copy of its rectangle, 0.070 widths soft, dropped
 * 0.065 and 20% dark, to 2/255 of the page. It is also why a card looks a
 * touch darker one level back than at the front — that is the shadow of the
 * card in front of it; nothing is dimmed.
 */
private const val SHADOW_ALPHA = 0.20f
private const val SHADOW_SIGMA = 0.070f
private const val SHADOW_DROP = 0.065f

/** Width the shadow is baked at, in px; it is a blur, so it scales up cleanly. */
private const val SHADOW_BAKE = 64

/*
 * The caption, in card widths: the title starts 0.136 to the right of the
 * front card, with a cap height of 0.116, and the index ends the same distance
 * to its left, both centred on the middle of the frame.
 */
private const val CAPTION_GAP = 0.136f
private const val TITLE_SIZE = 0.17f
private const val SMALL_SIZE = 0.046f

/** Card widths the frame must hold across: the card and a caption either side. */
private const val FIT_ACROSS = 2.5f

/** A drag of this many card heights runs the stack on one card. */
private const val DRAG_PER_CARD = 0.5f

private const val TAP_SLOP = 8f

/**
 * A deck of cards stacked in depth both ways from the one at the front: those
 * before it step back above, those after it below, each a fifth smaller than
 * the one in front, so all that shows of them is the edge — a terrace of
 * narrowing bands top and bottom, with the front card between.
 *
 * The whole stack is one number. Run it on and every card moves one level
 * along the same rule: the front card shrinks back into the bands above, the
 * first band below grows out to take its place, and the two swap in front of
 * each other the moment they are level, halfway. Nothing turns, fades or
 * rounds off; the corners are square.
 *
 * Drag up or down, flick to throw it, scroll to run it a card a notch, or tap
 * a band to bring that card to the front. However it is let go, it settles
 * on the nearest card.
 *
 * @param items cards, in order; the stack repeats, so it has no end.
 * @param background page colour.
 * @param contentColor caption text; the index and subtitle are set in it at
 *   reduced strength.
 * @param shadowColor colour of the shadow each card throws onto those behind it.
 * @param captions the index of the front card to its left, its title and
 *   subtitle to its right.
 * @param depth levels shown either side of the front card.
 */
@Composable
fun TerraceScroll(
    items: List<TerraceItem>,
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    contentColor: Color = Color(0xFF141414),
    shadowColor: Color = Color.Black,
    captions: Boolean = true,
    depth: Int = 3,
    state: TerraceScrollState = rememberTerraceScrollState(),
) {
    if (items.isEmpty()) {
        Box(modifier)
        return
    }

    val cards = remember(items) { items.map { cardBitmap(it.image, 1f / ASPECT, 0f) } }
    val shadow = remember(shadowColor) { bakeShadow(shadowColor) }
    val reach = depth.coerceAtLeast(0) + 0.5f

    LaunchedEffect(state) {
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                val deltaMs = if (previous == 0L) 16.67f else (now - previous) / 1_000_000f
                previous = now
                state.advance(deltaMs.clamped(1f, 50f))
            }
        }
    }

    BoxWithConstraints(modifier.background(background)) {
        // The stack runs to 1.04 card widths either side of the middle at its
        // fullest, half a level past the last band, so it fits in 0.9 of the
        // height with room for the shadow under it.
        val outer = outerEdge(reach)
        val cardDp = min(maxWidth.value / FIT_ACROSS, maxHeight.value * 0.9f / (2f * outer))
        val density = LocalDensity.current.density
        val cardPx = cardDp * density

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state, cardPx, reach) {
                    val dragPx = max(cardPx * ASPECT * DRAG_PER_CARD, 1f)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var last = down.position.y
                        var travel = 0f
                        var started = false
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dy = change.position.y - last
                            last = change.position.y
                            travel += abs(dy) + abs(change.position.x - change.previousPosition.x)
                            if (travel > TAP_SLOP) {
                                if (!started) {
                                    state.onDragStart(change.uptimeMillis / 1000f)
                                    started = true
                                }
                                // Up the frame runs the stack on: the front
                                // card goes back into the bands above.
                                state.onDrag(-dy / dragPx, change.uptimeMillis / 1000f)
                                change.consume()
                            }
                        }
                        if (started) {
                            state.onDragEnd()
                        } else {
                            val hit = hitCard(
                                down.position, state.position, cardPx,
                                size.width / 2f, size.height / 2f, reach,
                            )
                            if (hit != null) state.scrollTo(hit)
                        }
                    }
                }
                .pointerInput(state) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val scroll = event.changes.firstOrNull()?.scrollDelta?.y ?: continue
                            if (scroll == 0f) continue
                            state.onWheel(scroll.coerceIn(-1f, 1f))
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            state.tick // read, so the stack repaints every frame
            drawStack(cards, shadow, state.position, cardPx, size.width / 2f, size.height / 2f, reach)
        }

        if (captions) Caption(items, state, contentColor, cardDp, maxWidth.value)
    }
}

// --- the stack ---------------------------------------------------------------

/** How far a card [d] levels back reaches from the middle, in card widths. */
private fun outerEdge(d: Float): Float = STEP * d + ASPECT / 2f * SHRINK.pow(d)

private fun DrawScope.drawStack(
    cards: List<ImageBitmap>,
    shadow: BakedShadow,
    position: Float,
    card: Float,
    cx: Float,
    cy: Float,
    reach: Float,
) {
    if (card <= 0f) return
    val first = ceil(position - reach).toInt()
    val last = floor(position + reach).toInt()

    // Back to front, which is outside in: the levels shrink away from the front
    // card both ways, so taking whichever end is further back draws the whole
    // stack in order without sorting anything.
    var lo = first
    var hi = last
    while (lo <= hi) {
        val dLo = lo - position
        val dHi = hi - position
        val index: Int
        if (abs(dLo) > abs(dHi)) {
            index = lo
            lo++
        } else {
            index = hi
            hi--
        }
        val d = index - position
        if (abs(d) > reach) continue
        val scale = SHRINK.pow(abs(d))
        val w = card * scale
        val h = w * ASPECT
        val left = cx - w / 2f
        val top = cy + STEP * card * d - h / 2f

        val k = w / SHADOW_BAKE
        withTransform({
            translate(left - shadow.pad * k, top - shadow.pad * k + SHADOW_DROP * w)
            scale(k, k, Offset.Zero)
        }) {
            drawWhole(shadow.image, FilterQuality.Low)
        }

        val image = cards[wrapCard(index, cards.size)]
        withTransform({
            translate(left, top)
            scale(w / image.width, h / image.height, Offset.Zero)
        }) {
            drawWhole(image, FilterQuality.Medium)
        }
    }
}

/**
 * [image] at its own size at the origin. The transform around it does the
 * placing and the scaling, so the card moves sub-pixel smoothly where an
 * integer destination would step.
 */
private fun DrawScope.drawWhole(image: ImageBitmap, quality: FilterQuality) {
    val whole = IntSize(image.width, image.height)
    drawImage(image, IntOffset.Zero, whole, IntOffset.Zero, whole, filterQuality = quality)
}

/** The card under [at], unwrapped, or null. Front first, since it covers the rest. */
private fun hitCard(
    at: Offset,
    position: Float,
    card: Float,
    cx: Float,
    cy: Float,
    reach: Float,
): Int? {
    val nearest = floor(position + 0.5f).toInt()
    val levels = ceil(reach).toInt() + 1
    for (step in 0..levels * 2) {
        val index = nearest + if (step % 2 == 0) step / 2 else -(step + 1) / 2
        val d = index - position
        if (abs(d) > reach) continue
        val w = card * SHRINK.pow(abs(d))
        val h = w * ASPECT
        val top = cy + STEP * card * d - h / 2f
        if (at.x >= cx - w / 2f && at.x <= cx + w / 2f && at.y >= top && at.y <= top + h) {
            return index
        }
    }
    return null
}

// --- shadow ------------------------------------------------------------------

private class BakedShadow(val image: ImageBitmap, val pad: Float)

/**
 * The shadow of a card [SHADOW_BAKE] px wide, padded to hold its blur. A
 * gaussian blur of a rectangle is the product of two edge profiles, one across
 * and one down, so it is laid in as a gradient across and then multiplied by
 * one down — two fills, once, rather than a blur anywhere.
 */
private fun bakeShadow(color: Color): BakedShadow {
    val w = SHADOW_BAKE.toFloat()
    val h = w * ASPECT
    val sigma = SHADOW_SIGMA * w
    val pad = ceil(3f * sigma)
    val bw = (w + 2f * pad).toInt()
    val bh = ceil(h + 2f * pad).toInt()
    val bitmap = ImageBitmap(bw, bh)

    fun profile(length: Float, extent: Int, alpha: Float, tint: Color): Array<Pair<Float, Color>> {
        val stops = 48
        return Array(stops + 1) { i ->
            val t = i / stops.toFloat()
            val p = t * extent
            val cover = 0.5f * (erf((p - pad) / (sigma * SQRT2)) - erf((p - pad - length) / (sigma * SQRT2)))
            t to tint.copy(alpha = alpha * cover)
        }
    }

    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        BitmapCanvas(bitmap),
        Size(bw.toFloat(), bh.toFloat()),
    ) {
        drawRect(
            Brush.horizontalGradient(*profile(w, bw, SHADOW_ALPHA * color.alpha, color), endX = bw.toFloat()),
        )
        drawRect(
            Brush.verticalGradient(*profile(h, bh, 1f, Color.Black), endY = bh.toFloat()),
            blendMode = BlendMode.DstIn,
        )
    }
    return BakedShadow(bitmap, pad)
}

private val SQRT2 = sqrt(2f)

/** Abramowitz and Stegun 7.1.26; good to 1.5e-7, far past what a shadow shows. */
private fun erf(x: Float): Float {
    val a = abs(x)
    val t = 1f / (1f + 0.3275911f * a)
    val y = 1f - (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t -
        0.284496736f) * t + 0.254829592f) * t * exp(-a * a)
    return if (x < 0f) -y else y
}

// --- caption -----------------------------------------------------------------

@Composable
private fun BoxScope.Caption(
    items: List<TerraceItem>,
    state: TerraceScrollState,
    contentColor: Color,
    cardDp: Float,
    widthDp: Float,
) {
    val front = state.front
    val item = items[wrapCard(front, items.size)]
    val density = LocalDensity.current
    val inset = (widthDp / 2f + cardDp * (0.5f + CAPTION_GAP)).dp
    val side = (widthDp / 2f - cardDp * (0.5f + CAPTION_GAP) - 12f).coerceAtLeast(0f).dp
    val title = with(density) { max(cardDp * TITLE_SIZE, 18f).dp.toSp() }
    val small = with(density) { max(cardDp * SMALL_SIZE, 9f).dp.toSp() }

    key(front) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = inset),
            horizontalAlignment = Alignment.End,
        ) {
            BasicText(
                text = (wrapCard(front, items.size) + 1).toString().padStart(2, '0'),
                style = TextStyle(
                    color = contentColor.copy(alpha = 0.55f),
                    fontSize = small,
                    letterSpacing = 0.12.em,
                    textAlign = TextAlign.End,
                ),
            )
            BasicText(
                text = "/ " + items.size.toString().padStart(2, '0'),
                style = TextStyle(
                    color = contentColor.copy(alpha = 0.32f),
                    fontSize = small,
                    letterSpacing = 0.12.em,
                    textAlign = TextAlign.End,
                ),
            )
        }

        if (item.title.isNotEmpty() || item.subtitle.isNotEmpty()) {
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = inset)
                    .widthIn(max = side),
            ) {
                if (item.title.isNotEmpty()) {
                    BasicText(
                        text = item.title,
                        style = TextStyle(
                            color = contentColor,
                            fontSize = title,
                            fontFamily = FontFamily.Serif,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.subtitle.isNotEmpty()) {
                    BasicText(
                        text = item.subtitle.uppercase(),
                        style = TextStyle(
                            color = contentColor.copy(alpha = 0.45f),
                            fontSize = small,
                            letterSpacing = 0.2.em,
                        ),
                        modifier = Modifier.padding(top = (cardDp * 0.03f).dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
