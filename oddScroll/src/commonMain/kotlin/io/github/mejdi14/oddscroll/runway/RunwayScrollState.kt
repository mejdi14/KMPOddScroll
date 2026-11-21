package io.github.mejdi14.oddscroll.runway

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.mejdi14.oddscroll.internal.clamped
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Total calibrated world travel from the top of the runway to the finish hold, in native page px (where page W = 993 px). */
internal const val RUNWAY_FINISH_PX = 44071f

/** Scroll offset at the 2.000s mark (Frame 61), where the main runway tour begins. */
internal const val RUNWAY_START_2S_PX = 1632.95f

/** Scroll offset where Segment 1 comes to rest at the Developer boundary (Frame 447, t = 14.867s). */
internal const val RUNWAY_DEV_PAUSE_PX = 36321.17f

/** Scroll offset where Segment 2 resumes after the Developer hold (Frame 455, t = 15.133s). */
internal const val RUNWAY_DEV_RESUME_PX = 36352.27f

/** Measured final-settle damped spring parameters: omega_n = 16.5944 rad/s (stiffness = 275.37f), zeta = 0.7907f. */
internal const val SETTLE_DAMPING = 0.7907f
internal const val SETTLE_STIFFNESS = 275.37f

/** Seconds of parting speed a throw carries before the settle spring takes over. */
internal const val FLING_COAST_SEC = 0.42f

/** Drag samples kept to read a throw off the end of a gesture. */
private const val SAMPLES = 5
private const val WINDOW = 0.1f
private const val MAX_STEP = 0.004f

/** Tour clock at which the finish hold ends and the tour rewinds to the top. */
private const val TOUR_END_SEC = 24.433f

/** Runway px ahead of the scroll position that counts as the middle of the runway, for the margin index. */
private const val SECTION_LOOK_AHEAD_PX = 1450f

/** How far before a section's start a margin-index jump lands, so the section sits mid-frame. */
private const val SECTION_LEAD_PX = 450f

/**
 * Creates and remembers a [RunwayScrollState].
 *
 * @param initialIndex item the runway rests on at first, its card in the middle
 *   of the frame. Used only when [autoplay] is off; the tour always starts at
 *   the top of the runway.
 * @param autoplay whether the measured reference tour plays on its own until the
 *   first drag, wheel or programmatic scroll.
 */
@Composable
fun rememberRunwayScrollState(
    initialIndex: Int = 0,
    autoplay: Boolean = true,
): RunwayScrollState = remember {
    RunwayScrollState(initialIndex = initialIndex, autoplay = autoplay)
}

/**
 * State of [RunwayScroll].
 *
 * The whole scene — the colour-banded runway floor, the 500-px border ruler,
 * the raised card plinths and the margin index's tethers — is driven by one
 * scroll coordinate, [scrollPx], in native runway pixels (`0 .. 44,071`, where
 * the runway is 993 px wide).
 *
 * Items repeat down the runway to fill every section, so [currentIndex] and the
 * scroll functions work in wrapped item indices (`0 until items.size`): the
 * current item is the card nearest the middle of the frame, and [scrollTo] and
 * [animateScrollTo] go to the nearest card showing that item. [step] moves in
 * rows of cards, a left/right pair counting as one row.
 *
 * Programmatic scrolling works whether or not the user may scroll, and stops
 * the autoplay tour.
 */
class RunwayScrollState internal constructor(
    initialIndex: Int = 0,
    autoplay: Boolean = true,
) {
    /**
     * Scroll position in native runway pixels (`0 .. 44,071`). It changes every
     * frame while the runway moves, so read it while drawing or in a graphics
     * layer, not in composition.
     */
    var scrollPx by mutableFloatStateOf(RUNWAY_START_2S_PX)
        private set

    /** Index of the section across the middle of the runway: the one the margin index tethers. */
    var activeSection by mutableIntStateOf(0)
        private set

    /** Wrapped index of the item whose card is nearest the middle of the frame. */
    var currentIndex by mutableIntStateOf(0)
        private set

    /** True while the runway is dragged, coasting, travelling to a target, or playing the tour. */
    var isScrollInProgress by mutableStateOf(autoplay)
        private set

    /** Whether the reference tour is playing. A drag, wheel or programmatic scroll turns it off. */
    var autoplay by mutableStateOf(autoplay)

    internal var tick by mutableIntStateOf(0)
        private set

    internal var dragging = false
        private set

    /** Settle spring and throw, set by [RunwayScroll] from its parameters. */
    internal var settleStiffness = SETTLE_STIFFNESS
    internal var settleDampingRatio = SETTLE_DAMPING
    internal var flingCoastSec = FLING_COAST_SEC

    private var velocityPxPerSec = 0f
    private var settling = false
    private var rewinding = false
    private var targetPx = scrollPx

    /** Clock for the measured reference tour (starts at t = 2.000s, ignoring the first 2s). */
    private var tourTimeSec = 2.0f

    /**
     * What the layout placed, as rest positions: the scroll position that puts
     * each card, and each row, in the middle of the frame, pinned to the ends
     * of the runway. See [bind].
     */
    private var itemCount = 0
    private var cardRests = FloatArray(0)
    private var cardItems = IntArray(0)
    private var rowRests = FloatArray(0)
    private var sectionStarts = FloatArray(0)
    private var pendingIndex: Int? = if (autoplay) null else initialIndex

    private val sampleTime = FloatArray(SAMPLES)
    private val samplePos = FloatArray(SAMPLES)
    private var sampleCount = 0

    /** Jumps to the nearest card showing item [index], with no animation. */
    fun scrollTo(index: Int) {
        if (cardItems.isEmpty()) {
            pendingIndex = index
            return
        }
        stopAutoplay()
        val px = itemTarget(index) ?: return
        scrollPx = px
        targetPx = px
        velocityPxPerSec = 0f
        settling = false
        updateDerived()
    }

    /** Travels to the nearest card showing item [index] on the settle spring. */
    fun animateScrollTo(index: Int) {
        if (cardItems.isEmpty()) {
            pendingIndex = index
            return
        }
        travelTo(itemTarget(index) ?: return)
    }

    /** Travels [by] rows of cards: positive further down the runway, negative back toward the top. */
    fun step(by: Int) {
        if (rowRests.isEmpty()) return
        val from = nearest(rowRests, if (settling) targetPx else scrollPx)
        val to = (from + by).coerceIn(0, rowRests.lastIndex)
        travelTo(rowRests[to])
    }

    /** Travels to [target], in native runway px (`0 .. 44,071`), on the settle spring. */
    fun animateScrollToPx(target: Float) = travelTo(target)

    /** Travels to the start of section [sectionIndex], as a tap on the margin index does. */
    fun animateScrollToSection(sectionIndex: Int) {
        if (sectionStarts.isEmpty()) return
        val i = sectionIndex.coerceIn(0, sectionStarts.lastIndex)
        travelTo(if (i == 0) RUNWAY_START_2S_PX else sectionStarts[i] - SECTION_LEAD_PX)
    }

    /**
     * Hands the state what the layout placed: how many items there are, the
     * middle and item of every card, the middle of every row, the sections'
     * starts, and the runway coordinate of the middle of the frame relative to
     * [scrollPx].
     */
    internal fun bind(
        itemCount: Int,
        cardAnchors: FloatArray,
        cardItems: IntArray,
        rowAnchors: FloatArray,
        sectionStarts: FloatArray,
        focusXi: Float,
    ) {
        // Cards too near the top to reach the middle of the frame all rest at 0; the first of them counts there.
        this.itemCount = itemCount
        this.cardRests = FloatArray(cardAnchors.size) { (cardAnchors[it] - focusXi).clamped(0f, RUNWAY_FINISH_PX) }
        this.cardItems = cardItems
        this.rowRests = FloatArray(rowAnchors.size) { (rowAnchors[it] - focusXi).clamped(0f, RUNWAY_FINISH_PX) }
        this.sectionStarts = sectionStarts
        pendingIndex?.let {
            pendingIndex = null
            scrollTo(it)
        }
        updateDerived()
    }

    /** Travels to placed card [card] (an index into the layout, not an item index). */
    internal fun animateScrollToCard(card: Int) {
        if (card in cardRests.indices) travelTo(cardRests[card])
    }

    private fun itemTarget(index: Int): Float? {
        val item = index.mod(max(itemCount, 1))
        var best = -1
        for (i in cardItems.indices) {
            if (cardItems[i] != item) continue
            if (best < 0 || abs(cardRests[i] - scrollPx) < abs(cardRests[best] - scrollPx)) best = i
        }
        return if (best < 0) null else cardRests[best]
    }

    private fun travelTo(target: Float) {
        stopAutoplay()
        targetPx = target.clamped(0f, RUNWAY_FINISH_PX)
        settling = true
        updateDerived()
    }

    private fun stopAutoplay() {
        autoplay = false
        rewinding = false
    }

    internal fun onDragStart(seconds: Float) {
        dragging = true
        stopAutoplay()
        settling = false
        velocityPxPerSec = 0f
        sampleCount = 0
        recordSample(seconds)
    }

    internal fun onDrag(deltaWorldPx: Float, seconds: Float) {
        if (!dragging) return
        scrollPx = (scrollPx + deltaWorldPx).clamped(0f, RUNWAY_FINISH_PX)
        recordSample(seconds)
    }

    internal fun onDragEnd() {
        if (!dragging) return
        dragging = false
        val v = partingSpeed()
        velocityPxPerSec = v
        // Aim a throw forward by the coast time of parting speed and let the settle spring bring it to rest.
        targetPx = (scrollPx + v * flingCoastSec).clamped(0f, RUNWAY_FINISH_PX)
        settling = true
    }

    internal fun onWheel(deltaWorldPx: Float) {
        if (dragging) return
        stopAutoplay()
        if (!settling) targetPx = scrollPx
        targetPx = (targetPx + deltaWorldPx).clamped(0f, RUNWAY_FINISH_PX)
        settling = true
    }

    internal fun advance(deltaMs: Float) {
        tick++
        val dt = (deltaMs / 1000f).clamped(0.0005f, 0.05f)

        if (autoplay && !dragging) {
            val prev = scrollPx
            tourTimeSec += dt
            scrollPx = evalMeasuredTourPx(tourTimeSec)
            velocityPxPerSec = (scrollPx - prev) / dt
            if (tourTimeSec >= TOUR_END_SEC) {
                // After the finish hold, rewind to the top on the settle spring and play again.
                autoplay = false
                rewinding = true
                targetPx = RUNWAY_START_2S_PX
                settling = true
            }
        } else if (settling && !dragging) {
            // Integrate the damped settle spring.
            val omega = sqrt(settleStiffness)
            val steps = (dt / MAX_STEP).toInt().coerceIn(1, 16)
            val h = dt / steps
            repeat(steps) {
                val acc = -omega * omega * (scrollPx - targetPx) - 2f * settleDampingRatio * omega * velocityPxPerSec
                velocityPxPerSec += acc * h
                scrollPx = (scrollPx + velocityPxPerSec * h).clamped(0f, RUNWAY_FINISH_PX)
            }
            if (abs(velocityPxPerSec) < 2f && abs(scrollPx - targetPx) < 0.5f) {
                scrollPx = targetPx
                velocityPxPerSec = 0f
                settling = false
                if (rewinding) {
                    rewinding = false
                    tourTimeSec = 2.0f
                    autoplay = true
                }
            }
        } else if (!dragging) {
            velocityPxPerSec = 0f
        }

        updateDerived()
    }

    private fun updateDerived() {
        val moving = dragging || settling || autoplay
        if (isScrollInProgress != moving) isScrollInProgress = moving

        // The section across the middle of the runway.
        val lookAtS = scrollPx + SECTION_LOOK_AHEAD_PX
        var sec = 0
        for (i in 1 until sectionStarts.size) {
            if (lookAtS >= sectionStarts[i]) sec = i else break
        }
        if (activeSection != sec) activeSection = sec

        if (cardRests.isNotEmpty()) {
            val item = cardItems[nearest(cardRests, scrollPx)]
            if (currentIndex != item) currentIndex = item
        }
    }

    private fun nearest(anchors: FloatArray, px: Float): Int {
        var best = 0
        for (i in 1 until anchors.size) {
            if (abs(anchors[i] - px) < abs(anchors[best] - px)) best = i
        }
        return best
    }

    private fun recordSample(seconds: Float) {
        sampleTime[sampleCount % SAMPLES] = seconds
        samplePos[sampleCount % SAMPLES] = scrollPx
        sampleCount++
    }

    private fun partingSpeed(): Float {
        if (sampleCount < 2) return 0f
        val newest = (sampleCount - 1) % SAMPLES
        var oldest = newest
        for (i in 1 until min(sampleCount, SAMPLES)) {
            val idx = (sampleCount - 1 - i + SAMPLES * SAMPLES) % SAMPLES
            if (sampleTime[newest] - sampleTime[idx] > WINDOW) break
            oldest = idx
        }
        val span = sampleTime[newest] - sampleTime[oldest]
        return if (span <= 1e-4f) 0f else (samplePos[newest] - samplePos[oldest]) / span
    }
}

/**
 * Evaluates the exact measured scroll trajectory S(t) from the reference recording
 * for t in [2.000s, 24.433s]:
 * - Segment 1 (t = 2.000s .. 14.867s, Frames 61..447):
 *     Phase 1A (2.000s .. 6.000s): CubicBezier(0.1291, 0.1000, 0.8861, 0.9000) from 1632.95 to 15594.90 px
 *     Phase 1B (6.000s .. 14.867s): CubicBezier(0.6158, 0.9117, 0.8609, 0.9756) from 15594.90 to 36321.17 px
 * - Developer Pause Hold (t = 14.867s .. 15.133s, Frames 447..455): 36321.17 .. 36352.27 px
 * - Segment 2 (t = 15.133s .. 23.967s, Frames 455..720):
 *     Phase 2A (15.133s .. 17.900s): CubicBezier(0.3754, 0.1375, 0.8121, 0.8365) from 36352.27 to 39004.06 px
 *     Phase 2B (17.900s .. 23.500s): CubicBezier(0.0980, 0.1000, 0.9123, 0.9000) from 39004.06 to 43883.70 px
 *     Phase 2C (23.500s .. 23.967s): CubicBezier(0.4104, 1.0719, 0.1715, 0.9904) from 43883.70 to 44071.00 px
 * - Finish Hold (t = 23.967s .. 24.433s, Frames 720..734): 44071.00 px
 */
internal fun evalMeasuredTourPx(t: Float): Float = when {
    t <= 2.000f -> RUNWAY_START_2S_PX
    t < 6.000f -> {
        val tau = (t - 2.000f) / 4.000f
        1632.95f + (15594.90f - 1632.95f) * evalCubicBezier(tau, 0.1291f, 0.1000f, 0.8861f, 0.9000f)
    }
    t < 14.8667f -> {
        val tau = (t - 6.000f) / (14.8667f - 6.000f)
        15594.90f + (RUNWAY_DEV_PAUSE_PX - 15594.90f) * evalCubicBezier(tau, 0.6158f, 0.9117f, 0.8609f, 0.9756f)
    }
    t < 15.1333f -> {
        val tau = (t - 14.8667f) / (15.1333f - 14.8667f)
        RUNWAY_DEV_PAUSE_PX + (RUNWAY_DEV_RESUME_PX - RUNWAY_DEV_PAUSE_PX) * tau
    }
    t < 17.900f -> {
        val tau = (t - 15.1333f) / (17.900f - 15.1333f)
        RUNWAY_DEV_RESUME_PX + (39004.06f - RUNWAY_DEV_RESUME_PX) * evalCubicBezier(tau, 0.3754f, 0.1375f, 0.8121f, 0.8365f)
    }
    t < 23.500f -> {
        val tau = (t - 17.900f) / (23.500f - 17.900f)
        39004.06f + (43883.70f - 39004.06f) * evalCubicBezier(tau, 0.0980f, 0.1000f, 0.9123f, 0.9000f)
    }
    t < 23.9667f -> {
        val tau = (t - 23.500f) / (23.9667f - 23.500f)
        43883.70f + (RUNWAY_FINISH_PX - 43883.70f) * evalCubicBezier(tau, 0.4104f, 1.0719f, 0.1715f, 0.9904f)
    }
    else -> RUNWAY_FINISH_PX
}

/** Exact Newton-Raphson solver for CSS/Compose cubic-bezier(x1, y1, x2, y2) at normalized time [tau]. */
internal fun evalCubicBezier(tau: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val t = tau.clamped(0f, 1f)
    var s = t
    repeat(8) {
        val om = 1f - s
        val x = 3f * om * om * s * x1 + 3f * om * s * s * x2 + s * s * s
        val dx = 3f * om * om * x1 + 6f * om * s * (x2 - x1) + 3f * s * s * (1f - x2)
        s = (s - (x - t) / max(dx, 1e-6f)).clamped(0f, 1f)
    }
    val om = 1f - s
    return 3f * om * om * s * y1 + 3f * om * s * s * y2 + s * s * s
}
