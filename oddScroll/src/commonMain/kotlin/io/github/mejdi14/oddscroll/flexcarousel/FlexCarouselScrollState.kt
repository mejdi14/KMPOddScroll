package io.github.mejdi14.oddscroll.flexcarousel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.mejdi14.oddscroll.internal.clamped
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/* The row is a spring, not a scroller: input moves a goal and the position
   chases it, so a flick, a wheel notch and a tap all arrive through the same
   settling. The wheel is stiffer because it comes in as a stream of discrete
   notches that would otherwise feel like it is lagging the hand. */
private const val WHEEL_STIFFER = 80f / 55f
private const val SUBSTEP = 1f / 240f

/** Quiet time after a wheel notch before the row commits to a card. */
private const val WHEEL_SETTLE_MS = 150f

/** Over this, a throw that would land where it started still moves one card. */
private const val FLICK = 400f

/* Scroll energy, which the cards narrow against. It rises almost at once and
   falls away slowly, so the row tightens the moment it is pushed and relaxes
   well after it stops. */
private const val ENERGY_FULL = 2600f
private const val ENERGY_RISE = 0.07f
private const val ENERGY_FALL = 0.35f

/** Layout of the row for one viewport: every card's width and centre. */
internal class FlexMetrics(
    val cardH: Float,
    val widths: FloatArray,
    val centers: FloatArray,
    val gap: Float,
    val loop: Float,
)

/** Signed distance to [value] around a cycle of [size], nearest way round. */
internal fun wrapAround(value: Float, size: Float): Float =
    ((((value + size / 2f) % size) + size) % size) - size / 2f

/**
 * Creates a [FlexCarouselScrollState] that is remembered across compositions.
 *
 * @param initialIndex the card centred on the first frame. Wrapped into the
 *   items once the row is laid out, so any value is safe.
 */
@Composable
fun rememberFlexCarouselScrollState(initialIndex: Int = 0): FlexCarouselScrollState =
    remember { FlexCarouselScrollState(initialIndex) }

/**
 * Where a [FlexCarouselScroll] sits, and the handle to move it from outside.
 *
 * Position is unbounded and the row is wrapped only when it is drawn, so there
 * is no seam to cross and no bookkeeping at the ends — the loop is a property
 * of the layout, not something the scroll has to be kept inside. The row's
 * own frame loop runs every motion, so none of the functions here suspend.
 *
 * Only [currentIndex] and [isScrollInProgress] are observable. The position
 * moves every frame and is read by the draw, never by composition.
 */
class FlexCarouselScrollState internal constructor(initialIndex: Int) {
    /** Set by the composable whenever the viewport or the items change. */
    internal var metrics: FlexMetrics? = null
        set(value) {
            if (value === field) return
            val hadLayout = field != null
            field = value
            if (value == null) return
            val pending = pendingIndex
            pendingIndex = null
            // A new layout moves every centre, so the row is re-anchored on
            // the card it was showing rather than left at a stale offset.
            when {
                pending != null -> jump(value, pending)
                hadLayout -> jump(value, currentIndex)
            }
        }

    internal var position = 0f
        private set

    /** Index of the card facing the middle of the row, `0 until items.size`. */
    var currentIndex by mutableIntStateOf(initialIndex.coerceAtLeast(0))
        private set

    /** True while the row is being dragged, wheeled or is still settling. */
    var isScrollInProgress by mutableStateOf(false)
        private set

    /** Bumped once per frame, so the row repaints while it is settling. */
    internal var tick by mutableIntStateOf(0)
        private set

    /** 0 when still, 1 when moving fast enough to narrow the cards fully. */
    internal var energy = 0f
        private set

    /** Spring stiffness of the row, set by the composable. */
    internal var stiffness = 55f

    /** Seconds of a throw's own speed it is projected along, set by the composable. */
    internal var flingReach = 0.32f

    /** Where the cards landed last frame, for hit testing. */
    internal val placed = ArrayList<PlacedCard>()

    internal class PlacedCard(val index: Int, val left: Float, val right: Float, val rel: Float)

    private var pendingIndex: Int? = initialIndex
    private var goal = 0f
    private var velocity = 0f
    private var lastPosition = 0f
    private var wheeling = false
    private var wheelAtMs = 0f
    private var dragging = false
    private var dragged = false
    private var nowMs = 0f
    private var autoplayAtMs = 0f

    // --- public control ------------------------------------------------------

    /** Centres [index] at once, with no motion. Any value is wrapped into the items. */
    fun scrollTo(index: Int) {
        val m = metrics
        if (m == null) {
            pendingIndex = index
            return
        }
        jump(m, index)
    }

    /**
     * Travels to [index] through the row's own spring, the nearest way round
     * the loop. Any value is wrapped into the items.
     */
    fun animateScrollTo(index: Int) {
        val m = metrics
        if (m == null) {
            pendingIndex = index
            return
        }
        val i = wrapIndex(index, m)
        wheeling = false
        goal += wrapAround(m.centers[i] - goal, m.loop)
    }

    /** Moves [by] cards along, forward when positive, through the row's spring. */
    fun step(by: Int) {
        val m = metrics
        if (m == null) {
            pendingIndex = (pendingIndex ?: currentIndex) + by
            return
        }
        wheeling = false
        goal = stepFrom(m, snapTo(m, goal), by)
    }

    // --- input ---------------------------------------------------------------

    /** A finger is down: the row stops under it. */
    internal fun onDragStart() {
        dragging = true
        dragged = false
        wheeling = false
    }

    internal fun onDrag(dx: Float) {
        dragged = true
        position -= dx
    }

    /** [throwVelocity] is in pixels a second, positive when flicked leftward. */
    internal fun onDragEnd(throwVelocity: Float) {
        dragging = false
        dragged = false
        val m = metrics ?: return
        velocity = throwVelocity
        val landing = snapTo(m, position + throwVelocity * flingReach)
        goal = landing
        // A hard flick that would settle back onto the card it started from is
        // read as intent to move on, not as a wasted gesture.
        if (abs(throwVelocity) > FLICK && abs(landing - position) < 1f) {
            goal = stepFrom(m, landing, if (throwVelocity > 0f) 1 else -1)
        }
    }

    /** A press that never became a drag lets go; the row picks up its goal again. */
    internal fun onRelease() {
        dragging = false
        dragged = false
        velocity = 0f
    }

    internal fun onWheel(delta: Float) {
        wheeling = true
        wheelAtMs = nowMs
        goal += delta
    }

    /** Travels to the card drawn [rel] pixels from the middle. */
    internal fun travelBy(rel: Float) {
        val m = metrics ?: return
        goal = snapTo(m, position + rel)
    }

    // --- frame ---------------------------------------------------------------

    internal fun advance(nowMs: Float, deltaMs: Float, autoplay: Boolean, intervalMs: Float) {
        this.nowMs = nowMs
        tick++
        val m = metrics ?: return
        val dt = (deltaMs / 1000f).clamped(0.001f, 0.05f)

        if (wheeling && nowMs - wheelAtMs > WHEEL_SETTLE_MS) {
            goal = snapTo(m, goal)
            wheeling = false
        }

        if (!dragging) {
            val k = stiffness.coerceAtLeast(1f)
            val springK = if (wheeling) k * WHEEL_STIFFER else k
            val damping = 2f * sqrt(springK)
            // Fixed substeps: one 50ms frame integrated in a single step would
            // overshoot far enough to wind the spring up instead of settling it.
            val steps = ceil(dt / SUBSTEP).toInt().coerceAtLeast(1)
            val h = dt / steps
            repeat(steps) {
                velocity += (springK * (goal - position) - damping * velocity) * h
                position += velocity * h
            }
            if (abs(goal - position) < 0.05f && abs(velocity) < 0.5f) {
                position = goal
                velocity = 0f
            }
        }

        val travel = abs(position - lastPosition) / dt
        lastPosition = position
        val want = min(travel / ENERGY_FULL, 1f)
        energy += (want - energy) *
            (1f - exp(-dt / if (want > energy) ENERGY_RISE else ENERGY_FALL))

        if (autoplay && !dragging && abs(velocity) < 1f) {
            if (autoplayAtMs == 0f) autoplayAtMs = nowMs
            if (nowMs - autoplayAtMs > intervalMs) {
                autoplayAtMs = nowMs
                goal = stepFrom(m, snapTo(m, goal), 1)
            }
        } else {
            autoplayAtMs = nowMs
        }

        val near = nearest(m, position)
        if (currentIndex != near) currentIndex = near
        val moving = dragged || wheeling || goal != position || velocity != 0f
        if (isScrollInProgress != moving) isScrollInProgress = moving
    }

    // --- layout queries ------------------------------------------------------

    private fun jump(m: FlexMetrics, index: Int) {
        val i = wrapIndex(index, m)
        position += wrapAround(m.centers[i] - position, m.loop)
        goal = position
        lastPosition = position
        velocity = 0f
        wheeling = false
        currentIndex = i
    }

    private fun wrapIndex(index: Int, m: FlexMetrics): Int {
        val n = m.centers.size
        return ((index % n) + n) % n
    }

    private fun nearest(m: FlexMetrics, at: Float): Int {
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (i in m.centers.indices) {
            val d = abs(wrapAround(m.centers[i] - at, m.loop))
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return best
    }

    private fun snapTo(m: FlexMetrics, at: Float): Float =
        at + wrapAround(m.centers[nearest(m, at)] - at, m.loop)

    /**
     * One card along from [from]. Cards are not a fixed width, so a step is the
     * half of this one plus the gap plus the half of the next — not a constant.
     */
    private fun stepFrom(m: FlexMetrics, from: Float, by: Int): Float {
        var at = from
        var moved = 0
        val n = m.widths.size
        val direction = if (by >= 0) 1 else -1
        while (moved < abs(by)) {
            val index = nearest(m, at)
            val next = ((index + direction) % n + n) % n
            at += if (direction > 0) {
                m.widths[index] / 2f + m.gap + m.widths[next] / 2f
            } else {
                -(m.widths[next] / 2f + m.gap + m.widths[index] / 2f)
            }
            moved++
        }
        return snapTo(m, at)
    }
}

internal fun buildMetrics(
    aspects: FloatArray,
    viewportWidth: Float,
    viewportHeight: Float,
    cardHeight: Float,
    gap: Float,
    fixedAspect: Float?,
    maxCardWidth: Float,
): FlexMetrics {
    // Height alone cannot size a card. On a tall phone a half-height card is
    // wider than the frame, which swallows its neighbours and leaves nothing
    // for the row to read as a row, so the widest card is also held to a share
    // of the width and the height follows it down. On a wide frame the cap is
    // never reached and the height is the only thing that matters, which is why
    // landscape is untouched by this.
    var cardH = (cardHeight * viewportHeight).coerceAtLeast(24f)
    var widest = 0f
    for (i in aspects.indices) widest = max(widest, fixedAspect ?: aspects[i])
    if (widest > 0f && viewportWidth > 0f) {
        cardH = min(cardH, maxCardWidth * viewportWidth / widest).coerceAtLeast(24f)
    }
    val widths = FloatArray(aspects.size) { (fixedAspect ?: aspects[it]) * cardH }
    val centers = FloatArray(widths.size)
    var cursor = 0f
    for (i in widths.indices) {
        centers[i] = cursor + widths[i] / 2f
        cursor += widths[i] + gap
    }
    return FlexMetrics(cardH, widths, centers, gap, cursor.coerceAtLeast(1f))
}
