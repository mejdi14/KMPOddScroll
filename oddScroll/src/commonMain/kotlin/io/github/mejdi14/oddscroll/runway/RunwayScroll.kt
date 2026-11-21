package io.github.mejdi14.oddscroll.runway

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import io.github.mejdi14.oddscroll.internal.PI_F
import io.github.mejdi14.oddscroll.internal.cardBitmap
import io.github.mejdi14.oddscroll.internal.clamped
import io.github.mejdi14.oddscroll.internal.lerp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Which lateral slot a [RunwayItem] occupies on the runway. */
enum class RunwaySpan {
    /** Spans the full card rail `u in [-0.369, +0.369]` (`0.738` of the runway width). */
    Full,
    /** Left half-width plinth `u in [-0.369, -0.0074]` (`0.3616` of the runway width). */
    Left,
    /** Right half-width plinth `u in [+0.0074, +0.369]` (`0.3616` of the runway width). */
    Right,
}

/**
 * One raised 3D card plinth on the runway.
 *
 * The runway lays its rows out by itself, filling the sections in order and
 * alternating full-width rows with left/right pairs, so [sectionIndex] and
 * [span] describe where the item belongs but are not read by the layout.
 *
 * @property image artwork warped onto the plinth's top face, cropped to 1.45:1.
 * @property title bold line at the top of the plinth.
 * @property subtitle smaller line under the title; empty draws none.
 * @property callout text on the leader line off the plinth's top corner.
 * @property sectionIndex section the item belongs to.
 * @property span lateral slot the item belongs in.
 */
data class RunwayItem(
    val image: ImageBitmap,
    val title: String,
    val subtitle: String = "",
    val callout: String = title,
    val sectionIndex: Int = 0,
    val span: RunwaySpan = RunwaySpan.Full,
)

/**
 * One colour band tiling the runway, named in the margin index.
 *
 * @property name the section's title on the runway and in the margin index.
 * @property color the band's floor colour.
 * @property startPx where the band starts along the runway, in native px (`0 .. 44,071`).
 * @property endPx where it ends.
 */
data class RunwaySection(
    val name: String,
    val color: Color,
    val startPx: Float,
    val endPx: Float,
)

/** The 11 measured runway sections and their exact calibrated world bounds and RGB colours. */
val DefaultRunwaySections: List<RunwaySection> = listOf(
    RunwaySection("Agentic", Color(0xFF090C99), 0f, 7151.5f),
    RunwaySection("Sidekick", Color(0xFF5E0099), 7151.5f, 9997.4f),
    RunwaySection("Online", Color(0xFF920056), 9997.4f, 13343.6f),
    RunwaySection("Merchandising", Color(0xFF972008), 13343.6f, 15927.3f),
    RunwaySection("Retail", Color(0xFF090C9B), 15927.3f, 19360.8f),
    RunwaySection("Payments", Color(0xFF127D2F), 19360.8f, 24839.8f),
    RunwaySection("Operations", Color(0xFF920056), 24839.8f, 28799.4f),
    RunwaySection("Shop", Color(0xFF5E0099), 28799.4f, 31847.5f),
    RunwaySection("Marketing", Color(0xFF972009), 31847.5f, 34389.4f),
    RunwaySection("Fulfillment", Color(0xFF117E2F), 34389.4f, 37802.3f),
    RunwaySection("Developer", Color(0xFF090909), 37802.3f, 45000f),
)

/*
 * The runway geometry, in native runway widths (`page W = 993 px`) and reference
 * 1080x1080 screen units, as measured off the 752 post-intro frames of the
 * reference recording:
 *
 * - The runway is horizontally centred at x_c = 0.5000 * W_screen (measured
 *   539.98 px on 1080 px, RMS 0.25 px, max 0.42 px).
 * - In the foreground (y >= 0.55 * H_screen) the runway lies on a flat tilted
 *   plane whose screen width grows linearly at dw/dy = 0.798; above that it
 *   curves smoothly upward toward vertical so dw/dy -> 0 at the top edge
 *   (w(0) = 0.3812 * W_screen = 411.7 px), fitted to 0.59 px RMS across all
 *   1,020 unclipped scanlines.
 * - Card plinths occupy the lateral rails u in [-0.369, +0.369] (0.738 of the
 *   runway width, leaving a 0.131 margin each side), split into left and right
 *   half-cards by a 0.0148 centre gutter (u = +-0.0074), and stand 0.0201
 *   runway widths (20.0 px on 1080 px) proud of the floor.
 * - Pooling all 10 section boundaries (292 obs) and 17 flat card tracks
 *   (1,132 edge obs) into a single scalar scroll coordinate S(t) places
 *   section boundaries to 0.92 px RMS (median 0.17 px), card vertical centres
 *   to 0.89 px RMS (median 0.19 px), and card horizontal centres to 0.41 px RMS.
 */
private const val RAIL_OUTER_U = 0.369f
private const val RAIL_GUTTER_U = 0.0074f
private const val PLINTH_THICKNESS_NORM = 20.0f / 1080.0f
private const val TAP_SLOP = 8f

/** Left-margin HUD index dot column and vertical row pitch on the 1080x1080 reference frame. */
private const val HUD_DOT_X_NORM = 116.0f / 1080.0f
private const val HUD_TOP_Y_NORM = 404.0f / 1080.0f
private const val HUD_ROW_PITCH_NORM = 26.0f / 1080.0f

/** Screen row, as a share of the height, that counts as the middle of the frame for the current item. */
private const val FOCUS_YN = 0.5f

/** Card artwork crop (width over height) and corner rounding as a share of its shorter side. */
private const val IMAGE_RATIO = 1.45f
private const val IMAGE_CORNER = 0.045f

/**
 * A colour-banded 3D perspective runway of raised card plinths that curves
 * upward into the distance, flanked by a 500-px coordinate ruler and wireframe
 * callouts, with a left-margin section index tethered to the active section's
 * cards by live Bezier splines.
 *
 * Drag up or down to run along the 44,071-px runway, flick to coast and settle
 * on a damped spring, scroll with a wheel or trackpad, or tap a section in the
 * margin index to travel there. Items fill the [sections] in order and repeat
 * until every section is full, alternating full-width rows with left/right
 * pairs of half-width plinths in the longer sections.
 *
 * @param items the cards; they repeat down the runway to fill every section.
 * @param sections the colour bands tiling the runway, far end first, each with
 *   its name in the margin index.
 * @param background colour behind the runway.
 * @param contentColor ink for every line, label, ruler tick, plinth edge, the
 *   margin index and its tethers.
 * @param shadowColor colour of the plinths' front walls and the drop shadow
 *   under each card's artwork.
 * @param captions whether text is drawn: card titles, subtitles and callouts,
 *   section titles, ruler numbers, the margin index's names and the scenery's
 *   labels. Without it the runway keeps its shapes and the index its dots.
 * @param settleStiffness stiffness of the spring a throw, wheel or travel
 *   settles on.
 * @param settleDampingRatio damping ratio of that spring; below 1 it overshoots
 *   a little before it rests.
 * @param flingCoast seconds of parting speed a throw carries before it settles.
 * @param wheelSpeed runway px travelled per unit of wheel or trackpad delta.
 * @param userScrollEnabled false ignores drag, fling, wheel and margin-index
 *   taps; scrolling through [state] still works and item taps are still
 *   reported.
 * @param onItemClick called with the wrapped item index of a tapped card. When
 *   null, a tap on a card travels to that card.
 * @param state scroll position, current item and programmatic scrolling.
 */
@Composable
fun RunwayScroll(
    items: List<RunwayItem>,
    modifier: Modifier = Modifier,
    sections: List<RunwaySection> = DefaultRunwaySections,
    background: Color = Color(0xFF050507),
    contentColor: Color = Color.White,
    shadowColor: Color = Color.Black,
    captions: Boolean = true,
    settleStiffness: Float = 275.37f,
    settleDampingRatio: Float = 0.7907f,
    flingCoast: Float = 0.42f,
    wheelSpeed: Float = 320f,
    userScrollEnabled: Boolean = true,
    onItemClick: ((index: Int) -> Unit)? = null,
    state: RunwayScrollState = rememberRunwayScrollState(),
) {
    if (items.isEmpty() || sections.isEmpty()) {
        Box(modifier)
        return
    }

    val measurer = rememberTextMeasurer()
    val bakedImages = remember(items) { items.map { cardBitmap(it.image, IMAGE_RATIO, IMAGE_CORNER) } }
    val layout = remember(items, sections) { RunwayLayout(items, sections) }
    val projector = remember { RunwayProjector() }
    val scrollEnabled by rememberUpdatedState(userScrollEnabled)
    val clickHandler by rememberUpdatedState(onItemClick)
    val wheel by rememberUpdatedState(wheelSpeed)

    SideEffect {
        state.settleStiffness = settleStiffness
        state.settleDampingRatio = settleDampingRatio
        state.flingCoastSec = flingCoast
    }

    // Bound when the composition applies, so an initial index lands before the first frame is drawn.
    DisposableEffect(state, layout) {
        state.bind(
            itemCount = items.size,
            cardAnchors = FloatArray(layout.placedCards.size) {
                0.5f * (layout.placedCards[it].sTop + layout.placedCards[it].sBot)
            },
            cardItems = IntArray(layout.placedCards.size) { layout.placedCards[it].itemIndex },
            rowAnchors = layout.rowAnchors,
            sectionStarts = FloatArray(sections.size) { sections[it].startPx },
            focusXi = projector.evalPhiCard(FOCUS_YN),
        )
        onDispose { }
    }

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

    Box(modifier.background(background).fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state, layout) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var lastY = down.position.y
                        var travel = 0f
                        var started = false
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dy = change.position.y - lastY
                            lastY = change.position.y
                            travel += abs(dy) + abs(change.position.x - change.previousPosition.x)
                            if (travel > TAP_SLOP && (started || scrollEnabled)) {
                                if (!started) {
                                    state.onDragStart(change.uptimeMillis / 1000f)
                                    started = true
                                }
                                val h = max(size.height.toFloat(), 1f)
                                val yNorm = (change.position.y / h).clamped(0.05f, 0.95f)
                                val worldPerScreen = projector.dPhiFloorDyNorm(yNorm) / h
                                state.onDrag(-dy * worldPerScreen, change.uptimeMillis / 1000f)
                                change.consume()
                            }
                        }
                        if (started) {
                            state.onDragEnd()
                        } else if (travel <= TAP_SLOP) {
                            handleTap(
                                pos = down.position,
                                width = size.width.toFloat(),
                                height = size.height.toFloat(),
                                sectionCount = sections.size,
                                layout = layout,
                                projector = projector,
                                state = state,
                                scrollEnabled = scrollEnabled,
                                onItemClick = clickHandler,
                            )
                        }
                    }
                }
                .pointerInput(state) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll || !scrollEnabled) continue
                            val scrollY = event.changes.firstOrNull()?.scrollDelta?.y ?: continue
                            if (scrollY == 0f) continue
                            state.onWheel(scrollY * wheel)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            state.tick
            drawRunwayScene(
                state = state,
                layout = layout,
                bakedImages = bakedImages,
                sections = sections,
                projector = projector,
                measurer = measurer,
                ink = contentColor,
                shadow = shadowColor,
                labels = captions,
            )
        }
    }
}

private fun handleTap(
    pos: Offset,
    width: Float,
    height: Float,
    sectionCount: Int,
    layout: RunwayLayout,
    projector: RunwayProjector,
    state: RunwayScrollState,
    scrollEnabled: Boolean,
    onItemClick: ((Int) -> Unit)?,
) {
    if (width <= 0f || height <= 0f) return
    val xNorm = pos.x / width
    val yNorm = pos.y / height

    // 1. A row of the left-margin index travels to its section.
    if (scrollEnabled && xNorm <= 0.16f) {
        val rowFloat = (yNorm - HUD_TOP_Y_NORM) / HUD_ROW_PITCH_NORM
        val idx = rowFloat.roundToInt()
        if (idx in 0 until sectionCount && abs(rowFloat - idx) <= 0.65f) {
            state.animateScrollToSection(idx)
            return
        }
    }

    // 2. A card: its top face and front wall, with the same projection the draw pass uses.
    val card = layout.cardAt(pos, width, height, state.scrollPx, projector) ?: return
    when {
        onItemClick != null -> onItemClick(layout.placedCards[card].itemIndex)
        scrollEnabled -> state.animateScrollToCard(card)
    }
}

// --- Measured Runway Projection ----------------------------------------------

/**
 * Evaluates the fitted 5th-degree runway width polynomial `w(y)` and the unified
 * floor/plinth longitudinal projections `Phi_floor(y)` and `Phi_card(y)`.
 */
internal class RunwayProjector {
    private val lutSize = 2001
    private val yNormLut = FloatArray(lutSize) { i -> -0.15f + 1.30f * i / (lutSize - 1) }
    private val xiFloorLut = FloatArray(lutSize) { i -> evalPhiFloor(yNormLut[i]) }
    private val xiCardLut = FloatArray(lutSize) { i -> evalPhiCard(yNormLut[i]) }

    val xiFloorMin = xiFloorLut.first()
    val xiFloorMax = xiFloorLut.last()
    val xiCardMin = xiCardLut.first()
    val xiCardMax = xiCardLut.last()

    /**
     * Runway width as a share of the reference 1080-px frame at normalized vertical
     * position [yNorm] (`y / height` in `0 .. 1`).
     *
     * Fitted to 1,020 scanlines of the temporal median chroma mask (RMS = 0.586 px):
     * `w(y) = 411.69 + 0.0881*y + 1.165e-3*y^2 - 1.113e-6*y^3 + 4.752e-10*y^4`.
     */
    fun widthFraction(yNorm: Float): Float {
        val y = (yNorm * 1080f).clamped(-160f, 1240f)
        val wPx = ((((-1.3189448e-12f * y + 5.1319105e-09f) * y - 6.7295610e-06f) * y +
            3.7385161e-03f) * y + 4.5441677e-02f) * y + 412.2882f
        return (wPx / 1080f).clamped(0.32f, 1.12f)
    }

    /** Calibrated longitudinal floor coordinate `xi = s - S(t)` at [yNorm]. */
    fun evalPhiFloor(yNorm: Float): Float {
        val u = yNorm * 1.080f
        val u2 = u * u
        val u3 = u2 * u
        val u4 = u3 * u
        val u5 = u4 * u
        val card = 2780.0f * u +
            6463.397f * (u2 - u) -
            16710.273f * (u3 - u) +
            13899.487f * (u4 - u) -
            4078.078f * (u5 - u)
        val floorOffset = -434.525f * u + 990.777f * u2 - 524.602f * u3
        return (card + floorOffset) * 0.960424f
    }

    /** Calibrated longitudinal raised-plinth coordinate `xi = s - S(t)` at [yNorm]. */
    fun evalPhiCard(yNorm: Float): Float {
        val u = yNorm * 1.080f
        val u2 = u * u
        val u3 = u2 * u
        val u4 = u3 * u
        val u5 = u4 * u
        val card = 2780.0f * u +
            6463.397f * (u2 - u) -
            16710.273f * (u3 - u) +
            13899.487f * (u4 - u) -
            4078.078f * (u5 - u)
        return card * 0.960424f
    }

    /** Runway px of floor per unit of normalized screen height at [yNorm]: how far a drag moves the runway. */
    fun dPhiFloorDyNorm(yNorm: Float): Float {
        val eps = 0.002f
        return (evalPhiFloor(yNorm + eps) - evalPhiFloor(yNorm - eps)) / (2f * eps)
    }

    /** Inverse floor mapping `yNorm = Phi_floor^{-1}(xi)`. */
    fun xiToYNormFloor(xi: Float): Float = interpLut(xi, xiFloorLut, yNormLut)

    /** Inverse card-plinth mapping `yNorm = Phi_card^{-1}(xi)`. */
    fun xiToYNormCard(xi: Float): Float = interpLut(xi, xiCardLut, yNormLut)

    private fun interpLut(x: Float, xs: FloatArray, ys: FloatArray): Float {
        if (x <= xs.first()) return ys.first()
        if (x >= xs.last()) return ys.last()
        var lo = 0
        var hi = xs.lastIndex
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (xs[mid] <= x) lo = mid else hi = mid
        }
        val denom = max(xs[hi] - xs[lo], 1e-5f)
        val t = (x - xs[lo]) / denom
        return ys[lo] + t * (ys[hi] - ys[lo])
    }
}

// --- Runway Content Layout ---------------------------------------------------

internal class PlacedCard(
    val itemIndex: Int,
    val item: RunwayItem,
    val sectionIndex: Int,
    val sTop: Float,
    val sBot: Float,
    val uLeft: Float,
    val uRight: Float,
    val calloutRight: Boolean,
)

internal class RunwayLayout(
    items: List<RunwayItem>,
    val sections: List<RunwaySection>,
) {
    val placedCards: List<PlacedCard>

    /** The middle of each row of cards, far to near; a pair of half-cards is one row. */
    val rowAnchors: FloatArray

    init {
        val list = ArrayList<PlacedCard>(items.size * 3)
        // Fill each section with raised card plinths, alternating full-width rows and paired half-width plinths.
        var cursor = 0
        for (secIdx in sections.indices) {
            val sec = sections[secIdx]
            val spanPx = sec.endPx - sec.startPx
            var s = sec.startPx + 620f
            var rowIdx = 0
            while (s + 720f < sec.endPx - 180f) {
                val makePair = (rowIdx % 2 == 1) && (spanPx > 2400f)
                if (makePair) {
                    val itemL = items[cursor % items.size]
                    val idxL = cursor % items.size
                    cursor++
                    val itemR = items[cursor % items.size]
                    val idxR = cursor % items.size
                    cursor++
                    val cardLen = 680f
                    list.add(
                        PlacedCard(
                            itemIndex = idxL,
                            item = itemL,
                            sectionIndex = secIdx,
                            sTop = s,
                            sBot = s + cardLen,
                            uLeft = -RAIL_OUTER_U,
                            uRight = -RAIL_GUTTER_U,
                            calloutRight = false,
                        )
                    )
                    list.add(
                        PlacedCard(
                            itemIndex = idxR,
                            item = itemR,
                            sectionIndex = secIdx,
                            sTop = s,
                            sBot = s + cardLen,
                            uLeft = RAIL_GUTTER_U,
                            uRight = RAIL_OUTER_U,
                            calloutRight = true,
                        )
                    )
                    s += cardLen + 540f
                } else {
                    val item = items[cursor % items.size]
                    val idx = cursor % items.size
                    cursor++
                    val cardLen = 740f
                    list.add(
                        PlacedCard(
                            itemIndex = idx,
                            item = item,
                            sectionIndex = secIdx,
                            sTop = s,
                            sBot = s + cardLen,
                            uLeft = -RAIL_OUTER_U,
                            uRight = RAIL_OUTER_U,
                            calloutRight = (rowIdx % 2 == 0),
                        )
                    )
                    s += cardLen + 580f
                }
                rowIdx++
            }
        }
        placedCards = list
        val rows = list.filterIndexed { i, card -> i == 0 || card.sTop != list[i - 1].sTop }
        rowAnchors = FloatArray(rows.size) { 0.5f * (rows[it].sTop + rows[it].sBot) }
    }

    /**
     * The placed card under [pos] on a [w] by [h] frame scrolled to [scrollPx],
     * nearest first, taking in its top face and front wall.
     */
    fun cardAt(pos: Offset, w: Float, h: Float, scrollPx: Float, projector: RunwayProjector): Int? {
        val cx = w * 0.5f
        val scale = runwayScale(w, h)
        val yn = pos.y / h
        for (i in placedCards.indices.reversed()) {
            val card = placedCards[i]
            val xiTop = card.sTop - scrollPx
            val xiBot = card.sBot - scrollPx
            if (xiBot < projector.xiCardMin || xiTop > projector.xiCardMax) continue
            val ynTop = projector.xiToYNormCard(xiTop)
            val ynBot = projector.xiToYNormCard(xiBot)
            val wall = plinthThicknessPx(ynBot, h) / h
            if (yn < ynTop || yn > ynBot + wall) continue
            val across = projector.widthFraction(min(yn, ynBot) + PLINTH_THICKNESS_NORM) * scale
            if (pos.x in (cx + card.uLeft * across)..(cx + card.uRight * across)) return i
        }
        return null
    }
}

/** Horizontal span the runway's width fraction is a share of, so portrait and landscape both frame cleanly. */
private fun runwayScale(w: Float, h: Float): Float = min(w, h * 1.12f)

/** Screen height of a plinth's front wall whose near edge sits on row [ynBot]. */
private fun plinthThicknessPx(ynBot: Float, h: Float): Float =
    (PLINTH_THICKNESS_NORM * h * (0.55f + 0.65f * ynBot)).clamped(6f, 32f)

// --- Scene Drawing -----------------------------------------------------------

private fun DrawScope.drawRunwayScene(
    state: RunwayScrollState,
    layout: RunwayLayout,
    bakedImages: List<ImageBitmap>,
    sections: List<RunwaySection>,
    projector: RunwayProjector,
    measurer: TextMeasurer,
    ink: Color,
    shadow: Color,
    labels: Boolean,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val S = state.scrollPx
    val cx = w * 0.5f
    val runwayScale = runwayScale(w, h)

    fun xAt(u: Float, yNorm: Float): Float =
        cx + u * projector.widthFraction(yNorm) * runwayScale

    // 1. Background 3D ground grid, twin curved trajectory arcs, wireframe blocks & math equations
    drawGroundEnvironment(S, w, h, runwayScale, projector, measurer, ink, labels, ::xAt)

    // 2. Runway floor polygon & colour-banded sections
    val runwayClip = Path().apply {
        val steps = 40
        for (i in 0..steps) {
            val yn = i.toFloat() / steps
            val xL = xAt(-0.5f, yn)
            val yPx = yn * h
            if (i == 0) moveTo(xL, yPx) else lineTo(xL, yPx)
        }
        for (i in steps downTo 0) {
            val yn = i.toFloat() / steps
            val xR = xAt(0.5f, yn)
            val yPx = yn * h
            lineTo(xR, yPx)
        }
        close()
    }

    clipPath(runwayClip) {
        for (sec in sections) {
            val xiStart = sec.startPx - S
            val xiEnd = sec.endPx - S
            if (xiEnd < projector.xiFloorMin || xiStart > projector.xiFloorMax) continue
            val yTop = (projector.xiToYNormFloor(max(xiStart, projector.xiFloorMin)) * h).clamped(0f, h)
            val yBot = (projector.xiToYNormFloor(min(xiEnd, projector.xiFloorMax)) * h).clamped(0f, h)
            if (yBot > yTop) {
                drawRect(
                    color = sec.color,
                    topLeft = Offset(0f, yTop),
                    size = Size(w, yBot - yTop + 1.5f),
                )
                // Thin crisp section divider line
                if (xiStart in projector.xiFloorMin..projector.xiFloorMax) {
                    drawLine(
                        color = ink.copy(alpha = 0.28f),
                        start = Offset(0f, yTop),
                        end = Offset(w, yTop),
                        strokeWidth = 1.2f,
                    )
                }
            }
        }

        // Subtle longitudinal blueprint guide rails at u = +-0.369 and centre gutter u = +-0.0074
        for (uRail in floatArrayOf(-RAIL_OUTER_U, -RAIL_GUTTER_U, RAIL_GUTTER_U, RAIL_OUTER_U)) {
            val railPath = Path()
            val steps = 24
            for (i in 0..steps) {
                val yn = i.toFloat() / steps
                val rx = xAt(uRail, yn)
                val ry = yn * h
                if (i == 0) railPath.moveTo(rx, ry) else railPath.lineTo(rx, ry)
            }
            drawPath(railPath, ink.copy(alpha = 0.08f), style = Stroke(1f))
        }
    }

    // Outer 1px bright border along the left and right edges of the runway
    val leftBorder = Path()
    val rightBorder = Path()
    val edgeSteps = 40
    for (i in 0..edgeSteps) {
        val yn = i.toFloat() / stepsToFloat(edgeSteps)
        val yPx = yn * h
        val xL = xAt(-0.5f, yn)
        val xR = xAt(0.5f, yn)
        if (i == 0) {
            leftBorder.moveTo(xL, yPx)
            rightBorder.moveTo(xR, yPx)
        } else {
            leftBorder.lineTo(xL, yPx)
            rightBorder.lineTo(xR, yPx)
        }
    }
    drawPath(leftBorder, ink.copy(alpha = 0.42f), style = Stroke(1.4f))
    drawPath(rightBorder, ink.copy(alpha = 0.42f), style = Stroke(1.4f))

    // 3. 500-px coordinate ruler ticks & labels on both outside borders of the runway
    drawBorderRulerTicks(S, h, projector, measurer, ink, labels, ::xAt)

    // 4. Section titles, constellation node clusters & 4-column feature bullets on the runway floor
    val tetherTargets = ArrayList<Offset>(8)
    drawSectionHeadersAndClusters(S, h, sections, state.activeSection, projector, measurer, ink, labels, ::xAt, tetherTargets)

    // 5. Raised 3D card plinths, wireframe dimension callouts & projective-warped images (drawn back-to-front: top of screen to bottom)
    val corners = FloatArray(8)
    val matrix = Matrix()

    for (card in layout.placedCards) {
        val xiTop = card.sTop - S
        val xiBot = card.sBot - S
        if (xiBot < projector.xiCardMin || xiTop > projector.xiCardMax) continue
        val ynTop = projector.xiToYNormCard(xiTop)
        val ynBot = projector.xiToYNormCard(xiBot)
        if (ynBot <= -0.08f || ynTop >= 1.08f) continue

        drawCardPlinth(
            card = card,
            ynTop = ynTop,
            ynBot = ynBot,
            image = bakedImages.getOrNull(card.itemIndex),
            h = h,
            xAt = ::xAt,
            corners = corners,
            matrix = matrix,
            measurer = measurer,
            ink = ink,
            shadow = shadow,
            labels = labels,
        )

        if (card.sectionIndex == state.activeSection && ynTop in 0.05f..0.92f) {
            val ynMid = 0.5f * (ynTop + ynBot)
            tetherTargets.add(Offset(xAt(card.uLeft, ynMid + PLINTH_THICKNESS_NORM), ynMid * h))
        }
    }

    // 6. Left-margin section index and Bezier spline tethers to the active section's elements
    drawLeftHudAndTethers(state.activeSection, sections, w, h, measurer, ink, labels, tetherTargets)
}

private fun stepsToFloat(n: Int): Float = max(n, 1).toFloat()

// --- Ground Grid, Wireframe Pillars & Equations ------------------------------

private val FORMULAS = arrayOf(
    "y = e^(-x) cos(\u03C9x + \u03C6)",
    "\u03C3(x) = 1 / (1 + e^(-kx))",
    "y = sin(2\u03C0x + \u03C6)",
    "y = \u03A3 sin((2k+1)x) / (2k+1)",
)

private fun DrawScope.drawGroundEnvironment(
    S: Float,
    w: Float,
    h: Float,
    runwayScale: Float,
    projector: RunwayProjector,
    measurer: TextMeasurer,
    ink: Color,
    labels: Boolean,
    xAt: (Float, Float) -> Float,
) {
    val gridColor = ink.copy(alpha = 0.07f)
    val arcColor = ink.copy(alpha = 0.32f)

    // Twin curved trajectory rails flanking the runway on both sides
    for (uArc in floatArrayOf(-0.68f, -0.84f, 0.68f, 0.84f)) {
        val path = Path()
        val steps = 28
        for (i in 0..steps) {
            val yn = i.toFloat() / steps
            val ax = xAt(uArc, yn)
            val ay = yn * h
            if (i == 0) path.moveTo(ax, ay) else path.lineTo(ax, ay)
        }
        drawPath(path, if (abs(uArc) < 0.75f) arcColor else gridColor, style = Stroke(1.2f))
    }

    // Horizontal ground grid lines every 1000 world px outside the runway
    val firstGrid = (floor((S + projector.xiFloorMin) / 1000f).toInt() * 1000).coerceAtLeast(0)
    val lastGrid = ceil((S + projector.xiFloorMax) / 1000f).toInt() * 1000
    val formulaStyle = TextStyle(
        color = ink.copy(alpha = 0.36f),
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
    )
    val dimStyle = TextStyle(
        color = ink.copy(alpha = 0.42f),
        fontSize = 8.sp,
        fontFamily = FontFamily.Monospace,
    )

    var sGrid = firstGrid
    while (sGrid <= lastGrid) {
        val xi = sGrid - S
        val yn = projector.xiToYNormFloor(xi)
        if (yn in 0.02f..0.98f) {
            val yPx = yn * h
            val xL = xAt(-0.5f, yn)
            val xR = xAt(0.5f, yn)
            drawLine(gridColor, Offset(0f, yPx), Offset(xL, yPx), strokeWidth = 1f)
            drawLine(gridColor, Offset(xR, yPx), Offset(w, yPx), strokeWidth = 1f)

            val stepIndex = sGrid / 1000
            // Wireframe 3D architectural pillar every 2000 world px along the runway flanks
            if (stepIndex % 2 == 0) {
                val side = if (stepIndex % 4 == 0) -1f else 1f
                val uBase = side * 0.58f
                val bx = xAt(uBase, yn)
                val bw = (0.042f * projector.widthFraction(yn) * runwayScale).coerceAtLeast(10f)
                val bh = bw * 2.1f
                val depth = bw * 0.45f * side
                val topY = yPx - bh
                // Front & side wireframe box
                drawRect(
                    color = ink.copy(alpha = 0.22f),
                    topLeft = Offset(bx - bw * 0.5f, topY),
                    size = Size(bw, bh),
                    style = Stroke(1f),
                )
                drawRect(
                    color = ink.copy(alpha = 0.14f),
                    topLeft = Offset(bx - bw * 0.5f + depth, topY - abs(depth) * 0.4f),
                    size = Size(bw, bh),
                    style = Stroke(1f),
                )
                val label = if (stepIndex % 6 == 0) "1.2 m" else "0.8 m"
                val tx = (bx + side * bw * 0.8f).clamped(4f, w - 42f)
                val ty = yPx.clamped(12f, h - 18f)
                if (labels) drawText(measurer, label, Offset(tx, ty), style = dimStyle)
            }

            // Floating mathematical formula on the outer grid every 3000 world px
            if (labels && stepIndex % 3 == 1 && yn in 0.15f..0.82f) {
                val formula = FORMULAS[stepIndex.mod(FORMULAS.size)]
                val onRight = stepIndex % 2 == 1
                val fx = if (onRight) xAt(0.64f, yn) else xAt(-0.88f, yn)
                if (fx in 8f..(w - 140f)) {
                    drawText(measurer, formula, Offset(fx, yPx - 12f), style = formulaStyle)
                }
            }
        }
        sGrid += 1000
    }
}

// --- 500-px Border Ruler Ticks -----------------------------------------------

private fun DrawScope.drawBorderRulerTicks(
    S: Float,
    h: Float,
    projector: RunwayProjector,
    measurer: TextMeasurer,
    ink: Color,
    labels: Boolean,
    xAt: (Float, Float) -> Float,
) {
    val firstTick = (floor((S + projector.xiFloorMin) / 500f).toInt() * 500).coerceAtLeast(0)
    val lastTick = (ceil((S + projector.xiFloorMax) / 500f).toInt() * 500).coerceAtMost(45000)
    val tickStyle = TextStyle(
        color = ink.copy(alpha = 0.40f),
        fontSize = 8.5.sp,
        fontFamily = FontFamily.Monospace,
    )
    val tickColor = ink.copy(alpha = 0.45f)

    var sTick = firstTick
    while (sTick <= lastTick) {
        val xi = sTick - S
        val yn = projector.xiToYNormFloor(xi)
        if (yn in 0.02f..0.96f) {
            val yPx = yn * h
            val xL = xAt(-0.5f, yn)
            val xR = xAt(0.5f, yn)
            drawLine(tickColor, Offset(xL - 6f, yPx), Offset(xL, yPx), strokeWidth = 1.2f)
            drawLine(tickColor, Offset(xR, yPx), Offset(xR + 6f, yPx), strokeWidth = 1.2f)

            val label = sTick.toString()
            if (labels && xL > 44f) {
                drawText(measurer, label, Offset(xL - 36f, yPx - 6f), style = tickStyle)
            }
            if (labels && xR + 44f < size.width) {
                drawText(measurer, label, Offset(xR + 9f, yPx - 6f), style = tickStyle)
            }
        }
        sTick += 500
    }
}

// --- Section Headers, Node Clusters & Feature Columns ------------------------

private fun DrawScope.drawSectionHeadersAndClusters(
    S: Float,
    h: Float,
    sections: List<RunwaySection>,
    activeSection: Int,
    projector: RunwayProjector,
    measurer: TextMeasurer,
    ink: Color,
    labels: Boolean,
    xAt: (Float, Float) -> Float,
    tetherTargets: MutableList<Offset>,
) {
    for (secIdx in sections.indices) {
        val sec = sections[secIdx]
        // 1. Constellation node cluster centred at s = sec.startPx + 240
        val sCluster = sec.startPx + 240f
        val xiCluster = sCluster - S
        if (xiCluster in projector.xiFloorMin..projector.xiFloorMax) {
            val yn = projector.xiToYNormFloor(xiCluster)
            if (yn in 0.04f..0.94f) {
                val cx = xAt(0f, yn)
                val cy = yn * h
                val rScale = (xAt(0.14f, yn) - cx).coerceAtLeast(16f)
                val rays = 14
                for (k in 0 until rays) {
                    val angle = k * (2f * PI_F / rays) + secIdx * 0.4f
                    val dist = rScale * (0.45f + 0.55f * ((k * 7 + secIdx * 3) % 5) / 4f)
                    val nx = cx + cos(angle) * dist
                    val ny = cy + sin(angle) * dist * 0.52f
                    drawLine(ink.copy(alpha = 0.28f), Offset(cx, cy), Offset(nx, ny), strokeWidth = 1f)
                    drawCircle(ink.copy(alpha = 0.75f), radius = 2.2f, center = Offset(nx, ny))
                }
                drawRect(
                    color = ink,
                    topLeft = Offset(cx - 5f, cy - 5f),
                    size = Size(10f, 10f),
                )
            }
        }

        // 2. Large section display title at s = sec.startPx + 460
        val sTitle = sec.startPx + 460f
        val xiTitle = sTitle - S
        if (labels && xiTitle in projector.xiFloorMin..projector.xiFloorMax) {
            val yn = projector.xiToYNormFloor(xiTitle)
            if (yn in 0.04f..0.94f) {
                val tx = xAt(-RAIL_OUTER_U, yn)
                val ty = yn * h
                val fontScale = (0.55f + 0.75f * yn).clamped(0.55f, 1.25f)
                val titleStyle = TextStyle(
                    color = ink,
                    fontSize = (22f * fontScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                )
                drawText(measurer, sec.name, Offset(tx, ty - 14f * fontScale), style = titleStyle)

                // 4-column feature bullet nodes across the runway just below the title
                val ynBullets = (yn + 0.045f).clamped(0f, 0.98f)
                val yBulletsPx = ynBullets * h
                val colUs = floatArrayOf(-0.34f, -0.16f, 0.02f, 0.20f)
                for (uCol in colUs) {
                    val bx = xAt(uCol, ynBullets)
                    drawCircle(ink, radius = 2.8f, center = Offset(bx, yBulletsPx))
                    // Simulated crisp multi-line blueprint spec bars beside each bullet dot
                    val barW = (xAt(uCol + 0.12f, ynBullets) - bx).coerceAtLeast(18f)
                    for (line in 0..3) {
                        val ly = yBulletsPx + line * 4.5f * fontScale
                        val lw = barW * (if (line == 3) 0.62f else 0.92f)
                        drawLine(
                            color = ink.copy(alpha = if (line == 0) 0.55f else 0.26f),
                            start = Offset(bx + 6f, ly),
                            end = Offset(bx + 6f + lw, ly),
                            strokeWidth = 1.4f,
                        )
                    }
                    if (secIdx == activeSection) {
                        tetherTargets.add(Offset(bx, yBulletsPx))
                    }
                }
            }
        }
    }
}

// --- 3D Extruded Card Plinths & Projective Images ----------------------------

private fun DrawScope.drawCardPlinth(
    card: PlacedCard,
    ynTop: Float,
    ynBot: Float,
    image: ImageBitmap?,
    h: Float,
    xAt: (Float, Float) -> Float,
    corners: FloatArray,
    matrix: Matrix,
    measurer: TextMeasurer,
    ink: Color,
    shadow: Color,
    labels: Boolean,
) {
    val yTopPx = ynTop * h
    val yBotPx = ynBot * h
    val thicknessPx = plinthThicknessPx(ynBot, h)

    // Raised top face of the plinth has its effective width evaluated at yn + PLINTH_THICKNESS_NORM
    val ynTopEff = ynTop + PLINTH_THICKNESS_NORM
    val ynBotEff = ynBot + PLINTH_THICKNESS_NORM

    val xTL = xAt(card.uLeft, ynTopEff)
    val xTR = xAt(card.uRight, ynTopEff)
    val xBL = xAt(card.uLeft, ynBotEff)
    val xBR = xAt(card.uRight, ynBotEff)

    // Base footprint corners on the runway floor (shifted down by thicknessPx)
    val yBaseTopPx = yTopPx + thicknessPx * 0.72f
    val yBaseBotPx = yBotPx + thicknessPx

    // 1. Plinth top face fill & wireframe border
    val topFace = Path().apply {
        moveTo(xTL, yTopPx)
        lineTo(xTR, yTopPx)
        lineTo(xBR, yBotPx)
        lineTo(xBL, yBotPx)
        close()
    }
    drawPath(topFace, ink.copy(alpha = 0.08f))
    drawPath(topFace, ink.copy(alpha = 0.38f), style = Stroke(1.2f))

    // 2. Plinth front vertical wall (`z(t)`) between yBotPx and yBaseBotPx
    val frontFace = Path().apply {
        moveTo(xBL, yBotPx)
        lineTo(xBR, yBotPx)
        lineTo(xBR, yBaseBotPx)
        lineTo(xBL, yBaseBotPx)
        close()
    }
    drawPath(frontFace, shadow.copy(alpha = 0.28f))
    drawPath(frontFace, ink.copy(alpha = 0.42f), style = Stroke(1.2f))

    // Wireframe vertical corner struts & `Δx`, `z(t)` dimension lines
    drawLine(ink.copy(alpha = 0.30f), Offset(xTL, yTopPx), Offset(xTL, yBaseTopPx), strokeWidth = 1f)
    drawLine(ink.copy(alpha = 0.30f), Offset(xTR, yTopPx), Offset(xTR, yBaseTopPx), strokeWidth = 1f)
    drawLine(
        ink.copy(alpha = 0.22f),
        Offset(xBL, yBaseBotPx + 5f),
        Offset(xBR, yBaseBotPx + 5f),
        strokeWidth = 1f,
    )

    // 3. Projective-warped card artwork inside the plinth top face
    // Full-width plinths place the artwork on the right 54% of the plinth and copy on the left;
    // half-width plinths place the artwork across the lower 62% of the plinth below the header.
    val isFull = card.uRight - card.uLeft > 0.5f
    val imgULeft = if (isFull) lerp(card.uLeft, card.uRight, 0.44f) else lerp(card.uLeft, card.uRight, 0.08f)
    val imgURight = lerp(card.uLeft, card.uRight, 0.94f)
    val imgYnTop = if (isFull) lerp(ynTop, ynBot, 0.14f) else lerp(ynTop, ynBot, 0.32f)
    val imgYnBot = lerp(ynTop, ynBot, 0.88f)

    val imgYnTopEff = imgYnTop + PLINTH_THICKNESS_NORM
    val imgYnBotEff = imgYnBot + PLINTH_THICKNESS_NORM

    corners[0] = xAt(imgULeft, imgYnTopEff)
    corners[1] = imgYnTop * h
    corners[2] = xAt(imgURight, imgYnTopEff)
    corners[3] = imgYnTop * h
    corners[4] = xAt(imgURight, imgYnBotEff)
    corners[5] = imgYnBot * h
    corners[6] = xAt(imgULeft, imgYnBotEff)
    corners[7] = imgYnBot * h

    // Soft drop shadow under the inner UI panel
    if (image != null) {
        val shadowPath = Path().apply {
            moveTo(corners[0] + 4f, corners[1] + 5f)
            lineTo(corners[2] + 4f, corners[3] + 5f)
            lineTo(corners[4] + 4f, corners[5] + 5f)
            lineTo(corners[6] + 4f, corners[7] + 5f)
            close()
        }
        drawPath(shadowPath, shadow.copy(alpha = 0.35f))
    }

    if (image != null && quadMatrix(corners, image.width.toFloat(), image.height.toFloat(), matrix)) {
        withTransform({ transform(matrix) }) {
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(image.width, image.height),
                filterQuality = FilterQuality.Medium,
            )
        }
    }

    // 4. Card title & subtitle copy on the plinth top face + wireframe callout leader line
    val scale = (0.5f + 0.7f * ynBot).clamped(0.48f, 1.15f)
    val padX = (xTR - xTL) * 0.05f
    val padY = (yBotPx - yTopPx) * 0.10f
    if (labels && yBotPx - yTopPx > 28f && xTR - xTL > 70f) {
        val cardTitleStyle = TextStyle(
            color = ink.copy(alpha = 0.92f),
            fontSize = (11f * scale).sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
        )
        drawText(
            textMeasurer = measurer,
            text = "• ${card.item.title}",
            topLeft = Offset(xTL + padX, yTopPx + padY),
            style = cardTitleStyle,
        )
        if (card.item.subtitle.isNotEmpty() && yBotPx - yTopPx > 54f) {
            val subStyle = TextStyle(
                color = ink.copy(alpha = 0.68f),
                fontSize = (8.5f * scale).sp,
                fontFamily = FontFamily.SansSerif,
            )
            drawText(
                textMeasurer = measurer,
                text = card.item.subtitle,
                topLeft = Offset(xTL + padX, yTopPx + padY + 16f * scale),
                style = subStyle,
            )
        }
    }

    // 5. Side callout leader line (`o─── Callout`) from the top corner of the plinth
    if (labels && ynTop in 0.08f..0.88f) {
        val anchorX = if (card.calloutRight) xTR else xTL
        val anchorY = yTopPx
        val elbowX = if (card.calloutRight) xAt(0.44f, ynTop) else xAt(-0.44f, ynTop)
        val elbowY = (anchorY - 18f * scale).coerceAtLeast(14f)
        val endX = if (card.calloutRight) {
            (elbowX + 115f * scale).coerceAtMost(size.width - 8f)
        } else {
            (elbowX - 115f * scale).coerceAtLeast(8f)
        }

        drawCircle(ink.copy(alpha = 0.75f), radius = 2.4f, center = Offset(anchorX, anchorY))
        drawLine(ink.copy(alpha = 0.50f), Offset(anchorX, anchorY), Offset(elbowX, elbowY), strokeWidth = 1f)
        drawLine(ink.copy(alpha = 0.50f), Offset(elbowX, elbowY), Offset(endX, elbowY), strokeWidth = 1f)

        val calloutStyle = TextStyle(
            color = ink.copy(alpha = 0.92f),
            fontSize = (9.5f * scale).sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif,
        )
        val textX = if (card.calloutRight) elbowX + 4f else endX + 2f
        drawText(
            textMeasurer = measurer,
            text = card.item.callout,
            topLeft = Offset(textX.clamped(6f, size.width - 130f), (elbowY - 15f * scale).coerceAtLeast(2f)),
            style = calloutStyle,
        )
    }
}

// --- Left-Margin HUD Index & Dynamic Bezier Tether Splines -------------------

private fun DrawScope.drawLeftHudAndTethers(
    activeSection: Int,
    sections: List<RunwaySection>,
    w: Float,
    h: Float,
    measurer: TextMeasurer,
    ink: Color,
    labels: Boolean,
    tetherTargets: List<Offset>,
) {
    val dotX = HUD_DOT_X_NORM * w
    val labelX = 14f / 1080f * w

    for (i in sections.indices) {
        val rowY = (HUD_TOP_Y_NORM + i * HUD_ROW_PITCH_NORM) * h
        val isActive = i == activeSection
        if (labels) {
            val textStyle = TextStyle(
                color = ink.copy(alpha = if (isActive) 0.95f else 0.45f),
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            )
            drawText(measurer, sections[i].name, Offset(labelX, rowY - 6f), style = textStyle)
        }

        if (isActive) {
            drawCircle(ink, radius = 3.2f, center = Offset(dotX, rowY))
        } else {
            drawCircle(
                color = ink.copy(alpha = 0.48f),
                radius = 2.4f,
                center = Offset(dotX, rowY),
                style = Stroke(1.1f),
            )
        }
    }

    // Curved cubic Bezier spline tethers fanning out from the active section's HUD dot to the runway elements
    if (tetherTargets.isNotEmpty() && activeSection in sections.indices) {
        val startY = (HUD_TOP_Y_NORM + activeSection * HUD_ROW_PITCH_NORM) * h
        val start = Offset(dotX, startY)
        for (target in tetherTargets) {
            val dx = target.x - start.x
            val path = Path().apply {
                moveTo(start.x, start.y)
                cubicTo(
                    start.x + dx * 0.36f,
                    start.y,
                    start.x + dx * 0.58f,
                    target.y,
                    target.x,
                    target.y,
                )
            }
            drawPath(path, ink.copy(alpha = 0.58f), style = Stroke(1.15f))
        }
    }
}

/**
 * Exact projective homography mapping a [w] by [h] bitmap onto four screen
 * corners `[x0, y0, x1, y1, x2, y2, x3, y3]` (top-left clockwise).
 */
private fun quadMatrix(q: FloatArray, w: Float, h: Float, out: Matrix): Boolean {
    val x0 = q[0]; val y0 = q[1]
    val x1 = q[2]; val y1 = q[3]
    val x2 = q[4]; val y2 = q[5]
    val x3 = q[6]; val y3 = q[7]
    val sx = x0 - x1 + x2 - x3
    val sy = y0 - y1 + y2 - y3
    val dx1 = x1 - x2
    val dx2 = x3 - x2
    val dy1 = y1 - y2
    val dy2 = y3 - y2
    val den = dx1 * dy2 - dx2 * dy1
    if (abs(den) < 1e-6f) return false
    val g = (sx * dy2 - dx2 * sy) / den
    val k = (dx1 * sy - sx * dy1) / den
    out.reset()
    out[0, 0] = (x1 - x0 + g * x1) / w
    out[0, 1] = (y1 - y0 + g * y1) / w
    out[0, 3] = g / w
    out[1, 0] = (x3 - x0 + k * x3) / h
    out[1, 1] = (y3 - y0 + k * y3) / h
    out[1, 3] = k / h
    out[3, 0] = x0
    out[3, 1] = y0
    out[3, 3] = 1f
    return true
}
