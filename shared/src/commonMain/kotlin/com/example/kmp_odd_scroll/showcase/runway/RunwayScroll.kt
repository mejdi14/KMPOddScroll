package com.example.kmp_odd_scroll.showcase.runway

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
import com.example.kmp_odd_scroll.showcase.internal.PI_F
import com.example.kmp_odd_scroll.showcase.internal.cardBitmap
import com.example.kmp_odd_scroll.showcase.internal.clamped
import com.example.kmp_odd_scroll.showcase.internal.lerp
import com.example.kmp_odd_scroll.showcase.internal.smoothstep
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Which lateral slot a [RunwayItem] occupies on the runway. */
enum class RunwaySpan {
    /** Spans the full card rail `u in [-0.369, +0.369]` (`0.738` of the runway width). */
    Full,
    /** Left half-width plinth `u in [-0.369, -0.0074]` (`0.3616` of the runway width). */
    Left,
    /** Right half-width plinth `u in [+0.0074, +0.369]` (`0.3616` of the runway width). */
    Right,
}

/** One raised 3D card plinth on the runway. */
data class RunwayItem(
    val image: ImageBitmap,
    val title: String,
    val subtitle: String = "",
    val callout: String = title,
    val sectionIndex: Int = 0,
    val span: RunwaySpan = RunwaySpan.Full,
)

/** One of the 11 colour-coded sections tiling the runway. */
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
internal const val PAGE_WIDTH_PX = 993.0f
private const val TAP_SLOP = 8f

/** Left-margin HUD index dot column and vertical row pitch on the 1080x1080 reference frame. */
private const val HUD_DOT_X_NORM = 116.0f / 1080.0f
private const val HUD_TOP_Y_NORM = 404.0f / 1080.0f
private const val HUD_ROW_PITCH_NORM = 26.0f / 1080.0f

/*
 * Paper buildings. With them on, the runway is retiled into one band of colour
 * per item, no two neighbours alike, and each band holds one platform with a
 * paper pagoda on its centre. Flat, the pagoda is its own plan drawn on the
 * platform; as the platform comes to the middle of the frame it telescopes up
 * storey by storey, and it folds back down as the platform leaves. Nothing is
 * written anywhere: the runway is only the bands, the platforms and the towers.
 */

/** Screen row a pagoda's axis rests on when its platform is the one in focus. */
private const val FOCUS_AXIS_YN = 0.60f

/** Runway px from the focus row inside which a pagoda stands square, and past which it is flat. */
private const val FOLD_UP_PX = 140f
private const val FOLD_FLAT_PX = 780f

/** Length of one band, and how many bare bands run up to the first so the runway has a far end to show. */
private const val PAPER_BAND_PX = 1500f
private const val PAPER_LEAD_IN_BANDS = 2

/** A platform: half its width in runway widths, and its length in runway px. */
private const val PAPER_PLATFORM_U = 0.28f
private const val PAPER_PLATFORM_PX = 600f

/** Storeys above the hall, item by item, so that no pagoda is its neighbour's twin. */
private val PAGODA_STOREYS = intArrayOf(4, 3, 4, 2, 4, 3)

/** Colours close enough to pass for one another on two bands side by side. */
private fun alike(a: Color, b: Color): Boolean =
    abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) < 0.12f

/**
 * The runway of [sections] cut again into equal bands, taking the sections'
 * colours in turn and passing over any that would repeat the band before.
 */
private fun paperBands(sections: List<RunwaySection>): List<RunwaySection> {
    val count = ceil(sections.last().endPx / PAPER_BAND_PX).toInt()
    val bands = ArrayList<RunwaySection>(count + PAPER_LEAD_IN_BANDS)
    var pick = 0
    for (k in -PAPER_LEAD_IN_BANDS until count) {
        var section = sections[pick++ % sections.size]
        var passes = 0
        while (bands.isNotEmpty() && alike(section.color, bands.last().color) && passes++ < sections.size) {
            section = sections[pick++ % sections.size]
        }
        bands.add(RunwaySection(section.name, section.color, k * PAPER_BAND_PX, (k + 1) * PAPER_BAND_PX))
    }
    return bands
}

/**
 * A colour-banded 3D perspective runway of raised architectural card plinths
 * that curves upward into the distance, flanked by a 500-px coordinate ruler,
 * wireframe callouts, and a left-margin section index tethered to the active
 * cards by live Bezier splines.
 *
 * Drag up or down to run along the 44,071-px runway, flick to coast and settle
 * on the measured damped spring (`zeta = 0.791, stiffness = 275.4`), scroll with
 * a wheel or trackpad, tap any section in the left-margin index to jump there,
 * or tap the `start ↑` plinth at the finish line to fold it and launch.
 *
 * With [paperBuildings] the runway becomes a street of paper pagodas: one band
 * of colour per item, one platform on it, and a pagoda that lies flat as its
 * own plan until the platform nears the middle of the frame and folds up to
 * stand there. The runway then scrolls in steps, one platform at a time, and
 * draws no text at all. Pair it with `rememberRunwayScrollState(autoplay = false)`.
 */
@Composable
fun RunwayScroll(
    items: List<RunwayItem>,
    modifier: Modifier = Modifier,
    sections: List<RunwaySection> = DefaultRunwaySections,
    background: Color = Color(0xFF050507),
    state: RunwayScrollState = rememberRunwayScrollState(),
    paperBuildings: Boolean = false,
) {
    if (items.isEmpty() || sections.isEmpty()) {
        Box(modifier)
        return
    }

    val measurer = rememberTextMeasurer()
    val bakedImages = remember(items, paperBuildings) {
        if (paperBuildings) emptyList() else items.map { cardBitmap(it.image, 1.45f, 0.045f) }
    }
    val bands = remember(sections, paperBuildings) { if (paperBuildings) paperBands(sections) else sections }
    val layout = remember(items, bands, paperBuildings) { RunwayLayout(items, bands, paperBuildings) }
    val projector = remember { RunwayProjector() }
    val pagoda = remember { PaperPagoda() }

    LaunchedEffect(state, layout, paperBuildings) {
        if (paperBuildings) {
            // One stop per platform, where its pagoda's axis sits on the focus row, and a last one at the finish.
            val focusXi = projector.evalPhiCard(FOCUS_AXIS_YN)
            val rows = layout.rowAnchors.size
            state.setSnapStops(
                FloatArray(rows + 1) { if (it < rows) layout.rowAnchors[it] - focusXi else RUNWAY_FINISH_PX },
                IntArray(rows + 1) { if (it < rows) layout.rowSections[it] else bands.lastIndex },
            )
        } else {
            state.setSnapStops(FloatArray(0), IntArray(0))
        }
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
                .pointerInput(state) {
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
                            if (travel > TAP_SLOP) {
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
                        } else {
                            handleTap(down.position, size.width.toFloat(), size.height.toFloat(), if (paperBuildings) 0 else sections.size, state)
                        }
                    }
                }
                .pointerInput(state) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val scrollY = event.changes.firstOrNull()?.scrollDelta?.y ?: continue
                            if (scrollY == 0f) continue
                            state.onWheel(scrollY * 320f)
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
                sections = bands,
                projector = projector,
                measurer = measurer,
                pagoda = if (paperBuildings) pagoda else null,
            )
        }
    }
}

private fun handleTap(
    pos: Offset,
    width: Float,
    height: Float,
    sectionCount: Int,
    state: RunwayScrollState,
) {
    if (width <= 0f || height <= 0f) return
    val xNorm = pos.x / width
    val yNorm = pos.y / height

    // 1. Check tap on the left-margin HUD index rows
    if (xNorm <= 0.16f) {
        val rowFloat = (yNorm - HUD_TOP_Y_NORM) / HUD_ROW_PITCH_NORM
        val idx = rowFloat.roundToInt()
        if (idx in 0 until sectionCount && abs(rowFloat - idx) <= 0.65f) {
            state.scrollToSection(idx)
            return
        }
    }

    // 2. Check tap on the end-of-runway `start ↑` plinth when near the finish
    if (state.scrollPx >= RUNWAY_FINISH_PX - 1200f && xNorm in 0.34f..0.66f && yNorm in 0.52f..0.82f) {
        state.triggerLaunch()
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

    fun dPhiCardDyNorm(yNorm: Float): Float {
        val eps = 0.002f
        return (evalPhiCard(yNorm + eps) - evalPhiCard(yNorm - eps)) / (2f * eps)
    }

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
    paper: Boolean = false,
) {
    val placedCards: List<PlacedCard>

    /** The middle and section of each row of cards, far to near; a pair of half-cards is one row. */
    val rowAnchors: FloatArray
    val rowSections: IntArray

    init {
        val list = ArrayList<PlacedCard>(items.size * 3)
        // Populate each of the 11 sections with raised 3D card plinths (alternating full-width and paired half-width plinths)
        var cursor = 0
        if (paper) {
            // One platform in the middle of every band that lies on the runway proper.
            for (secIdx in sections.indices) {
                val sec = sections[secIdx]
                if (sec.startPx < 0f) continue
                val middle = 0.5f * (sec.startPx + sec.endPx)
                list.add(
                    PlacedCard(
                        itemIndex = cursor,
                        item = items[cursor % items.size],
                        sectionIndex = secIdx,
                        sTop = middle - PAPER_PLATFORM_PX * 0.5f,
                        sBot = middle + PAPER_PLATFORM_PX * 0.5f,
                        uLeft = -PAPER_PLATFORM_U,
                        uRight = PAPER_PLATFORM_U,
                        calloutRight = false,
                    )
                )
                cursor++
            }
        }
        for (secIdx in if (paper) IntRange.EMPTY else sections.indices) {
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
        rowSections = IntArray(rows.size) { rows[it].sectionIndex }
    }
}

// --- Scene Drawing -----------------------------------------------------------

private fun DrawScope.drawRunwayScene(
    state: RunwayScrollState,
    layout: RunwayLayout,
    bakedImages: List<ImageBitmap>,
    sections: List<RunwaySection>,
    projector: RunwayProjector,
    measurer: TextMeasurer,
    pagoda: PaperPagoda?,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val S = state.scrollPx
    val cx = w * 0.5f
    // Scale horizontal span proportionally to the smaller dimension so portrait and landscape both frame cleanly
    val runwayScale = min(w, h * 1.12f)

    fun xAt(u: Float, yNorm: Float): Float =
        cx + u * projector.widthFraction(yNorm) * runwayScale

    // 1. Background 3D ground grid, twin curved trajectory arcs, wireframe blocks & math equations
    val labels = pagoda == null
    drawGroundEnvironment(S, w, h, cx, runwayScale, projector, measurer, labels, ::xAt)

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
                        color = Color.White.copy(alpha = 0.28f),
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
            drawPath(railPath, Color.White.copy(alpha = 0.08f), style = Stroke(1f))
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
    drawPath(leftBorder, Color.White.copy(alpha = 0.42f), style = Stroke(1.4f))
    drawPath(rightBorder, Color.White.copy(alpha = 0.42f), style = Stroke(1.4f))

    // 3. 500-px coordinate ruler ticks & labels on both outside borders of the runway
    drawBorderRulerTicks(S, h, projector, measurer, labels, ::xAt)

    // 4. Section titles, constellation node clusters & 4-column feature bullets on the runway floor
    val tetherTargets = ArrayList<Offset>(8)
    drawSectionHeadersAndClusters(S, h, sections, state.activeSection, projector, measurer, labels, ::xAt, tetherTargets)

    // 5. Raised 3D card plinths, wireframe dimension callouts & projective-warped images (drawn back-to-front: top of screen to bottom)
    val corners = FloatArray(8)
    val matrix = Matrix()

    // Pagodas: where the focus row is along the runway, and how many screen px a runway px of
    // height is worth. Heights are drawn straight up the screen at the scale of the ground they
    // stand on. A flat plane seen at the focus row's pitch makes a standing wall 1.6 times as tall
    // as the same wall lying down; a tall frame stretches the floor, and the towers take half of
    // that stretch rather than all of it, which would leave them needles.
    val focusXi = projector.evalPhiCard(FOCUS_AXIS_YN)
    val lateral = projector.widthFraction(FOCUS_AXIS_YN + PLINTH_THICKNESS_NORM) * runwayScale / PAGE_WIDTH_PX
    val longitudinal = h / max(projector.dPhiCardDyNorm(FOCUS_AXIS_YN), 1f)
    val pagodaLift = runwayScale / PAGE_WIDTH_PX *
        (0.95f * sqrt(1.6f * longitudinal / max(lateral, 1e-3f))).clamped(0.7f, 1.5f)

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
            labels = labels,
        )

        if (pagoda != null) {
            // The pagoda takes the runway's projection as linear about its own axis: the row the
            // axis stands on, and how fast rows and the runway's width change toward the viewer.
            val xi = 0.5f * (card.sTop + card.sBot) - S
            val yn = projector.xiToYNormCard(xi)
            val ynPerS = (projector.xiToYNormCard(xi + 150f) - projector.xiToYNormCard(xi - 150f)) / 300f
            val ground = yn + PLINTH_THICKNESS_NORM
            val widthPerYn = (projector.widthFraction(ground + 0.01f) - projector.widthFraction(ground - 0.01f)) / 0.02f
            val storeys = PAGODA_STOREYS[card.itemIndex % PAGODA_STOREYS.size]
            pagoda.draw(
                scope = this,
                fold = 1f - smoothstep(FOLD_UP_PX, FOLD_FLAT_PX, abs(xi - focusXi)),
                storeys = storeys,
                // A tenth over life size fills the platform; the shorter towers are drawn a little
                // larger again, so theirs never looks under-filled.
                scale = 1.1f * (1f + 0.07f * (PAGODA_MAX_STOREYS - storeys)),
                originX = cx,
                u = 0.5f * (card.uLeft + card.uRight),
                yn = yn,
                ynPerS = ynPerS,
                widthFraction = projector.widthFraction(ground),
                widthPerS = widthPerYn * ynPerS,
                across = runwayScale,
                lift = pagodaLift,
            )
        }

        if (card.sectionIndex == state.activeSection && ynTop in 0.05f..0.92f) {
            val ynMid = 0.5f * (ynTop + ynBot)
            tetherTargets.add(Offset(xAt(card.uLeft, ynMid + PLINTH_THICKNESS_NORM), ynMid * h))
        }
    }

    // 6. End-of-runway `start ↑` plinth and 3D control bar at s = 44,071 px
    drawFinishControlBar(state, S, w, h, projector, measurer, labels, ::xAt)

    // 7. Left-margin 11-section HUD index and dynamic Bezier spline tethers to active section elements
    if (labels) drawLeftHudAndTethers(state.activeSection, sections, w, h, measurer, tetherTargets)
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
    cx: Float,
    runwayScale: Float,
    projector: RunwayProjector,
    measurer: TextMeasurer,
    labels: Boolean,
    xAt: (Float, Float) -> Float,
) {
    val gridColor = Color.White.copy(alpha = 0.07f)
    val arcColor = Color.White.copy(alpha = 0.32f)

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
        color = Color.White.copy(alpha = 0.36f),
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
    )
    val dimStyle = TextStyle(
        color = Color.White.copy(alpha = 0.42f),
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
                    color = Color.White.copy(alpha = 0.22f),
                    topLeft = Offset(bx - bw * 0.5f, topY),
                    size = Size(bw, bh),
                    style = Stroke(1f),
                )
                drawRect(
                    color = Color.White.copy(alpha = 0.14f),
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
    labels: Boolean,
    xAt: (Float, Float) -> Float,
) {
    val firstTick = (floor((S + projector.xiFloorMin) / 500f).toInt() * 500).coerceAtLeast(0)
    val lastTick = (ceil((S + projector.xiFloorMax) / 500f).toInt() * 500).coerceAtMost(45000)
    val tickStyle = TextStyle(
        color = Color.White.copy(alpha = 0.40f),
        fontSize = 8.5.sp,
        fontFamily = FontFamily.Monospace,
    )
    val tickColor = Color.White.copy(alpha = 0.45f)

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
                    drawLine(Color.White.copy(alpha = 0.28f), Offset(cx, cy), Offset(nx, ny), strokeWidth = 1f)
                    drawCircle(Color.White.copy(alpha = 0.75f), radius = 2.2f, center = Offset(nx, ny))
                }
                drawRect(
                    color = Color.White,
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
                    color = Color.White,
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
                    drawCircle(Color.White, radius = 2.8f, center = Offset(bx, yBulletsPx))
                    // Simulated crisp multi-line blueprint spec bars beside each bullet dot
                    val barW = (xAt(uCol + 0.12f, ynBullets) - bx).coerceAtLeast(18f)
                    for (line in 0..3) {
                        val ly = yBulletsPx + line * 4.5f * fontScale
                        val lw = barW * (if (line == 3) 0.62f else 0.92f)
                        drawLine(
                            color = Color.White.copy(alpha = if (line == 0) 0.55f else 0.26f),
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
    labels: Boolean,
) {
    val yTopPx = ynTop * h
    val yBotPx = ynBot * h
    val thicknessPx = (PLINTH_THICKNESS_NORM * h * (0.55f + 0.65f * ynBot)).clamped(6f, 32f)

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
    drawPath(topFace, Color.White.copy(alpha = 0.08f))
    drawPath(topFace, Color.White.copy(alpha = 0.38f), style = Stroke(1.2f))

    // 2. Plinth front vertical wall (`z(t)`) between yBotPx and yBaseBotPx
    val frontFace = Path().apply {
        moveTo(xBL, yBotPx)
        lineTo(xBR, yBotPx)
        lineTo(xBR, yBaseBotPx)
        lineTo(xBL, yBaseBotPx)
        close()
    }
    drawPath(frontFace, Color.Black.copy(alpha = 0.28f))
    drawPath(frontFace, Color.White.copy(alpha = 0.42f), style = Stroke(1.2f))

    // Wireframe vertical corner struts & `Δx`, `z(t)` dimension lines
    drawLine(Color.White.copy(alpha = 0.30f), Offset(xTL, yTopPx), Offset(xTL, yBaseTopPx), strokeWidth = 1f)
    drawLine(Color.White.copy(alpha = 0.30f), Offset(xTR, yTopPx), Offset(xTR, yBaseTopPx), strokeWidth = 1f)
    drawLine(
        Color.White.copy(alpha = 0.22f),
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
        drawPath(shadowPath, Color.Black.copy(alpha = 0.35f))
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
            color = Color.White.copy(alpha = 0.92f),
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
                color = Color.White.copy(alpha = 0.68f),
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

        drawCircle(Color.White.copy(alpha = 0.75f), radius = 2.4f, center = Offset(anchorX, anchorY))
        drawLine(Color.White.copy(alpha = 0.50f), Offset(anchorX, anchorY), Offset(elbowX, elbowY), strokeWidth = 1f)
        drawLine(Color.White.copy(alpha = 0.50f), Offset(elbowX, elbowY), Offset(endX, elbowY), strokeWidth = 1f)

        val calloutStyle = TextStyle(
            color = Color.White.copy(alpha = 0.92f),
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

// --- Finish `start ↑` Plinth & 3D Wireframe Control Bar ----------------------

private fun DrawScope.drawFinishControlBar(
    state: RunwayScrollState,
    S: Float,
    w: Float,
    h: Float,
    projector: RunwayProjector,
    measurer: TextMeasurer,
    labels: Boolean,
    xAt: (Float, Float) -> Float,
) {
    val sFinish = 45000f
    val xiFinish = sFinish - S
    if (xiFinish > projector.xiFloorMax + 600f) return

    val ynFinish = projector.xiToYNormFloor(xiFinish.clamped(projector.xiFloorMin, projector.xiFloorMax))
    val fold = state.launchProgress

    // 1. White `start ↑` block sitting centred below the 45000 finish line
    val ynBtnTop = (ynFinish + 0.08f + 0.04f * fold).clamped(0.05f, 0.92f)
    val ynBtnBot = (ynFinish + 0.145f - 0.018f * fold).clamped(ynBtnTop + 0.008f, 0.95f)
    val yBtnTop = ynBtnTop * h
    val yBtnBot = ynBtnBot * h
    val xBtnL = xAt(-0.134f, ynBtnBot)
    val xBtnR = xAt(0.134f, ynBtnBot)

    drawRect(
        color = Color(0xFFF2F2F2),
        topLeft = Offset(xBtnL, yBtnTop),
        size = Size(xBtnR - xBtnL, yBtnBot - yBtnTop),
    )
    drawRect(
        color = Color(0xFF3A3A3E),
        topLeft = Offset(xBtnL, yBtnBot),
        size = Size(xBtnR - xBtnL, 6f * (1f - 0.6f * fold)),
    )
    if (!labels) {
        // No lettering: the block says "back to the start" with an arrow, and the bar of keys is left out.
        val midX = (xBtnL + xBtnR) * 0.5f
        val midY = (yBtnTop + yBtnBot) * 0.5f
        val reach = ((yBtnBot - yBtnTop) * 0.28f).clamped(3f, 12f)
        val ink = Color(0xFF111114)
        drawLine(ink, Offset(midX, midY + reach), Offset(midX, midY - reach), strokeWidth = 2f)
        drawLine(ink, Offset(midX, midY - reach), Offset(midX - reach * 0.7f, midY - reach * 0.3f), strokeWidth = 2f)
        drawLine(ink, Offset(midX, midY - reach), Offset(midX + reach * 0.7f, midY - reach * 0.3f), strokeWidth = 2f)
        return
    }
    if (yBtnBot - yBtnTop > 18f) {
        val startStyle = TextStyle(
            color = Color(0xFF111114),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
        )
        drawText(
            textMeasurer = measurer,
            text = "start  \u2191",
            topLeft = Offset((xBtnL + xBtnR) * 0.5f - 28f, (yBtnTop + yBtnBot) * 0.5f - 8f),
            style = startStyle,
        )
    }

    // 2. 3D wireframe control bar below the `start ↑` button (`page W = 993 px`, `to the finish: ↑ 44,071 px`)
    val ynBarTop = (ynFinish + 0.19f).clamped(0.10f, 0.94f)
    val ynBarBot = (ynBarTop + 0.055f).clamped(ynBarTop + 0.02f, 0.98f)
    if (ynBarTop < 0.92f) {
        val barL = w * 0.102f
        val barR = w * 0.898f
        val barTop = ynBarTop * h
        val barBot = ynBarBot * h
        val barW = barR - barL
        val barH = barBot - barTop

        drawRect(
            color = Color(0xFF0A0A0D).copy(alpha = 0.88f),
            topLeft = Offset(barL, barTop),
            size = Size(barW, barH),
        )
        drawRect(
            color = Color.White.copy(alpha = 0.55f),
            topLeft = Offset(barL, barTop),
            size = Size(barW, barH),
            style = Stroke(1.2f),
        )

        val headers = arrayOf("hop", "steer", "faster", "brake", "speed", "to the finish")
        val speedVal = max(350, state.velocityPxPerSec.roundToInt().let { if (it == 0) 350 else abs(it) })
        val values = arrayOf(
            "space",
            "\u2190 \u2192 A D",
            "\u2191 W scroll up",
            "\u2193 S scroll down",
            "v \u2265 $speedVal px/s",
            "\u2191 ${state.scrollPx.roundToInt()} / 44,071 px",
        )
        val colFracs = floatArrayOf(0f, 0.11f, 0.24f, 0.43f, 0.65f, 0.82f, 1f)
        val headStyle = TextStyle(color = Color.White.copy(alpha = 0.48f), fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
        val valStyle = TextStyle(color = Color.White.copy(alpha = 0.88f), fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)

        for (c in headers.indices) {
            val cx0 = barL + colFracs[c] * barW
            if (c > 0) {
                drawLine(Color.White.copy(alpha = 0.22f), Offset(cx0, barTop), Offset(cx0, barBot), strokeWidth = 1f)
            }
            drawText(measurer, headers[c], Offset(cx0 + 8f, barTop + 5f), style = headStyle)
            drawText(measurer, values[c], Offset(cx0 + 8f, barTop + barH * 0.52f), style = valStyle)
        }

        // `page W = 993 px` dimension line underneath
        val pageStyle = TextStyle(color = Color.White.copy(alpha = 0.45f), fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
        drawText(
            measurer,
            "page  W = ${PAGE_WIDTH_PX.roundToInt()} px",
            Offset(w * 0.5f - 46f, (barBot + 10f).coerceAtMost(h - 14f)),
            style = pageStyle,
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
    tetherTargets: List<Offset>,
) {
    val dotX = HUD_DOT_X_NORM * w
    val labelX = 14f / 1080f * w

    for (i in sections.indices) {
        val rowY = (HUD_TOP_Y_NORM + i * HUD_ROW_PITCH_NORM) * h
        val isActive = i == activeSection
        val textStyle = TextStyle(
            color = Color.White.copy(alpha = if (isActive) 0.95f else 0.45f),
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
        )
        drawText(measurer, sections[i].name, Offset(labelX, rowY - 6f), style = textStyle)

        if (isActive) {
            drawCircle(Color.White, radius = 3.2f, center = Offset(dotX, rowY))
        } else {
            drawCircle(
                color = Color.White.copy(alpha = 0.48f),
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
            drawPath(path, Color.White.copy(alpha = 0.58f), style = Stroke(1.15f))
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
