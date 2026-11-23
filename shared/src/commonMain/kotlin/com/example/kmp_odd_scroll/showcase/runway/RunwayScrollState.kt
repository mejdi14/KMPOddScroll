package com.example.kmp_odd_scroll.showcase.runway

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.kmp_odd_scroll.showcase.internal.clamped
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
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

/** Drag samples kept to read a throw off the end of a gesture. */
private const val SAMPLES = 5
private const val WINDOW = 0.1f
private const val MAX_STEP = 0.004f

/** Parting speed, in runway px a second, past which a release steps on rather than settling back. */
private const val STEP_FLING = 300f

/** The most of a throw a step keeps, so the spring lands on the stop instead of sailing past it. */
private const val STEP_MAX_SPEED = 3500f

/** Wheel travel that makes one step, and the pause before the wheel may step again. */
private const val WHEEL_STEP_PX = 200f
private const val WHEEL_STEP_COOLDOWN = 0.5f
private const val WHEEL_IDLE_RESET = 0.25f

@Composable
fun rememberRunwayScrollState(
    autoplay: Boolean = true,
    initialScrollPx: Float = RUNWAY_START_2S_PX,
): RunwayScrollState = remember {
    RunwayScrollState(autoplay = autoplay, initialScrollPx = initialScrollPx)
}

/**
 * State of [RunwayScroll].
 *
 * The entire scene — the colour-banded runway floor, the 500-px border ruler,
 * the 3D extruded card plinths, the inner UI panels, and the active section's
 * Bezier tether splines — is driven by a single scalar scroll coordinate
 * [scrollPx] in native runway pixels (`0 .. 44,071`, where the runway width is
 * `page W = 993 px`).
 */
class RunwayScrollState internal constructor(
    autoplay: Boolean = true,
    initialScrollPx: Float = RUNWAY_START_2S_PX,
) {
    /** Current longitudinal scroll position in native runway pixels (`0 .. 44,071`). */
    var scrollPx by mutableFloatStateOf(initialScrollPx.clamped(0f, RUNWAY_FINISH_PX))
        internal set

    /** Current instantaneous scroll speed in native runway pixels per second. */
    var velocityPxPerSec by mutableFloatStateOf(0f)
        internal set

    /** Index of the currently active section (`0 .. 10`). */
    var activeSection by mutableIntStateOf(0)
        internal set

    /** Whether the reference tour is playing automatically. */
    var autoplay by mutableStateOf(autoplay)

    /** Progress (`0 .. 1`) of the end-of-runway `start ↑` button fold-and-launch sequence. */
    var launchProgress by mutableFloatStateOf(0f)
        internal set

    internal var tick by mutableIntStateOf(0)
        private set

    internal var lastDeltaSec: Float = 1f / 60f
        private set

    internal var dragging = false
        private set

    private var settling = false
    private var targetPx = scrollPx

    /** Clock for the measured reference tour (starts at t = 2.000s, ignoring the first 2s). */
    private var tourTimeSec = 2.0f
    private var launching = false
    private var launchClockSec = 0f

    /**
     * Scroll positions the runway comes to rest on, ascending, and the section
     * each belongs to. Empty means free scrolling.
     */
    private var snapStops = FloatArray(0)
    private var snapSections = IntArray(0)
    private var minScrollPx = 0f
    private var clockSec = 0f
    private var wheelAcc = 0f
    private var lastWheelSec = -10f
    private var lastWheelStepSec = -10f

    private val sampleTime = FloatArray(SAMPLES)
    private val samplePos = FloatArray(SAMPLES)
    private var sampleCount = 0

    /**
     * Turns step scrolling on ([stops] ascending, [sections] the section of
     * each) or, with an empty array, back off. The first stop may sit before
     * the top of the runway, which is how the first card gets to the middle
     * of the frame.
     */
    internal fun setSnapStops(stops: FloatArray, sections: IntArray) {
        if (stops.contentEquals(snapStops)) return
        snapStops = stops
        snapSections = sections
        minScrollPx = if (stops.isEmpty()) 0f else min(0f, stops.first())
        if (stops.isEmpty()) {
            scrollPx = scrollPx.clamped(0f, RUNWAY_FINISH_PX)
        } else if (!autoplay && !dragging) {
            scrollPx = if (scrollPx <= 0f) stops.first() else stops[nearestStop(scrollPx)]
            targetPx = scrollPx
            velocityPxPerSec = 0f
            settling = false
        }
    }

    private fun nearestStop(px: Float): Int {
        var best = 0
        for (i in 1 until snapStops.size) {
            if (abs(snapStops[i] - px) < abs(snapStops[best] - px)) best = i
        }
        return best
    }

    /** Smoothly scrolls the runway to [target] (in `0 .. 44,071` native px). */
    fun scrollToPx(target: Float) {
        autoplay = false
        launching = false
        launchProgress = 0f
        targetPx = if (snapStops.isEmpty()) {
            target.clamped(0f, RUNWAY_FINISH_PX)
        } else {
            snapStops[nearestStop(target)]
        }
        settling = true
    }

    /** Smoothly scrolls the runway to the start of section [sectionIndex] (`0 .. 10`). */
    fun scrollToSection(sectionIndex: Int) {
        val first = snapSections.indexOf(sectionIndex)
        if (first >= 0) {
            scrollToPx(snapStops[first])
            return
        }
        val idx = sectionIndex.coerceIn(0, SECTION_STARTS_PX.lastIndex)
        scrollToPx(SECTION_STARTS_PX[idx])
    }

    /** Triggers the end-of-runway `start ↑` button fold and reverse launch back to the top. */
    fun triggerLaunch() {
        autoplay = false
        settling = false
        launching = true
        launchClockSec = 0f
    }

    internal fun onDragStart(seconds: Float) {
        dragging = true
        autoplay = false
        settling = false
        launching = false
        launchProgress = 0f
        velocityPxPerSec = 0f
        sampleCount = 0
        recordSample(seconds)
    }

    internal fun onDrag(deltaWorldPx: Float, seconds: Float) {
        if (!dragging) return
        scrollPx = (scrollPx + deltaWorldPx).clamped(minScrollPx, RUNWAY_FINISH_PX)
        recordSample(seconds)
    }

    internal fun onDragEnd() {
        if (!dragging) return
        dragging = false
        val v = partingSpeed()
        if (snapStops.isNotEmpty()) {
            // A step: rest on the nearest stop, or the next one along if the release was a flick.
            var i = nearestStop(scrollPx)
            if (v > STEP_FLING && snapStops[i] < scrollPx) i++
            if (v < -STEP_FLING && snapStops[i] > scrollPx) i--
            targetPx = snapStops[i.coerceIn(0, snapStops.lastIndex)]
            velocityPxPerSec = v.clamped(-STEP_MAX_SPEED, STEP_MAX_SPEED)
            settling = true
            return
        }
        velocityPxPerSec = v
        // Aim a throw forward by 0.42s of parting speed and let the measured settle spring bring it to rest.
        targetPx = (scrollPx + v * 0.42f).clamped(0f, RUNWAY_FINISH_PX)
        settling = true
    }

    internal fun onWheel(deltaWorldPx: Float) {
        if (dragging) return
        autoplay = false
        launching = false
        launchProgress = 0f
        if (snapStops.isNotEmpty()) {
            // One step per push of the wheel: a trackpad's long tail of small deltas is
            // gathered up, spent on a single step, and ignored until the pause is over.
            if (clockSec - lastWheelSec > WHEEL_IDLE_RESET) wheelAcc = 0f
            lastWheelSec = clockSec
            if (clockSec - lastWheelStepSec < WHEEL_STEP_COOLDOWN) return
            wheelAcc += deltaWorldPx
            if (abs(wheelAcc) < WHEEL_STEP_PX) return
            val from = nearestStop(if (settling) targetPx else scrollPx)
            val to = (from + if (wheelAcc > 0f) 1 else -1).coerceIn(0, snapStops.lastIndex)
            wheelAcc = 0f
            lastWheelStepSec = clockSec
            targetPx = snapStops[to]
            settling = true
            return
        }
        if (!settling) targetPx = scrollPx
        targetPx = (targetPx + deltaWorldPx).clamped(0f, RUNWAY_FINISH_PX)
        settling = true
    }

    internal fun advance(deltaMs: Float) {
        tick++
        val dt = (deltaMs / 1000f).clamped(0.0005f, 0.05f)
        lastDeltaSec = dt
        clockSec += dt

        if (launching) {
            launchClockSec += dt
            // Measured Segment 3 (0.00..0.73s fold) + Segment 4 (0.73..2.37s launch) then reset to top
            val fold = (launchClockSec / 0.733f).clamped(0f, 1f)
            launchProgress = fold
            if (launchClockSec in 0.733f..2.367f) {
                val tau = ((launchClockSec - 0.733f) / 1.633f).clamped(0f, 1f)
                val p = evalCubicBezier(tau, 0.6282f, 0.4770f, 0.8539f, 1.0878f)
                val prev = scrollPx
                scrollPx = RUNWAY_FINISH_PX - 676.96f * p
                velocityPxPerSec = (scrollPx - prev) / dt
            } else if (launchClockSec > 2.367f) {
                // Loop cleanly back to the t = 2.000s start of the runway tour
                launching = false
                launchProgress = 0f
                tourTimeSec = 2.0f
                if (snapStops.isNotEmpty()) {
                    // Stepping has no tour to resume: land back on the first stop.
                    scrollPx = snapStops.first()
                    targetPx = scrollPx
                    velocityPxPerSec = 0f
                } else {
                    scrollPx = RUNWAY_START_2S_PX
                    autoplay = true
                }
            }
        } else if (autoplay && !dragging) {
            val prev = scrollPx
            tourTimeSec += dt
            scrollPx = evalMeasuredTourPx(tourTimeSec)
            velocityPxPerSec = (scrollPx - prev) / dt
            if (tourTimeSec >= 24.433f) {
                // Hold at the finish line for 0.73s, then fold & launch and loop
                launching = true
                launchClockSec = 0f
            }
        } else if (settling && !dragging) {
            // Integrate the measured damped spring: omega_n = 16.5944 rad/s, zeta = 0.7907
            val omega = sqrt(SETTLE_STIFFNESS)
            val steps = (dt / MAX_STEP).toInt().coerceIn(1, 16)
            val h = dt / steps
            repeat(steps) {
                val acc = -omega * omega * (scrollPx - targetPx) - 2f * SETTLE_DAMPING * omega * velocityPxPerSec
                velocityPxPerSec += acc * h
                scrollPx = (scrollPx + velocityPxPerSec * h).clamped(minScrollPx, RUNWAY_FINISH_PX)
            }
            if (abs(velocityPxPerSec) < 2f && abs(scrollPx - targetPx) < 0.5f) {
                scrollPx = targetPx
                velocityPxPerSec = 0f
                settling = false
            }
        } else if (!dragging) {
            velocityPxPerSec = 0f
        }

        // Update active section from the section visible across the middle of the runway (s = scrollPx + 1450)
        val lookAtS = scrollPx + 1450f
        var sec = 0
        for (i in 0 until SECTION_BOUNDARIES_PX.size) {
            if (lookAtS >= SECTION_BOUNDARIES_PX[i]) sec = i + 1 else break
        }
        if (activeSection != sec) activeSection = sec
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

/** Calibrated world coordinates of the 10 section boundaries (in native runway px out of 44,071 px). */
internal val SECTION_BOUNDARIES_PX = floatArrayOf(
    7151.5f,   // 01 Agentic -> 02 Sidekick
    9997.4f,   // 02 Sidekick -> 03 Online
    13343.6f,  // 03 Online -> 04 Merchandising
    15927.3f,  // 04 Merchandising -> 05 Retail
    19360.8f,  // 05 Retail -> 06 Payments
    24839.8f,  // 06 Payments -> 07 Operations
    28799.4f,  // 07 Operations -> 08 Shop
    31847.5f,  // 08 Shop -> 09 Marketing
    34389.4f,  // 09 Marketing -> 10 Fulfillment
    37802.3f,  // 10 Fulfillment -> 11 Developer
)

/** Scroll targets for jumping to each of the 11 sections. */
internal val SECTION_STARTS_PX = floatArrayOf(
    RUNWAY_START_2S_PX,
    6700f,
    9550f,
    12900f,
    15500f,
    18900f,
    24400f,
    28350f,
    31400f,
    33950f,
    37350f,
)
