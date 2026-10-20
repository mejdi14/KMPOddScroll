package io.github.mejdi14.oddscroll.flexcarousel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
private const val SPRING = 55f
private const val WHEEL_SPRING = 80f
private const val SUBSTEP = 1f / 240f

/** Quiet time after a wheel notch before the row commits to a card. */
private const val WHEEL_SETTLE_MS = 150f

/** How far a throw is projected when choosing which card to land on. */
private const val THROW_REACH = 0.32f

/** Over this, a throw that would land where it started still moves one card. */
private const val FLICK = 400f

/* Scroll energy, which the cards narrow against. It rises almost at once and
   falls away slowly, so the row tightens the moment it is pushed and relaxes
   well after it stops. */
private const val ENERGY_FULL = 2600f
private const val ENERGY_RISE = 0.07f
private const val ENERGY_FALL = 0.35f

private const val FOCUS_SPRING = 64f

/**
 * Softer than the opening, so the move aside reads as a second, more
 * deliberate step rather than a continuation of the first.
 */
private const val ASIDE_SPRING = 42f

/** How far open the card has to be before it starts moving aside. */
private const val ASIDE_AFTER = 0.88f

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
 * Where the row sits, how hard it is moving, and whether a card is open.
 *
 * Position is unbounded and the row is wrapped only when it is drawn, so there
 * is no seam to cross and no bookkeeping at the ends — the loop is a property
 * of the layout, not something the scroll has to be kept inside.
 */
class FlexCarouselScrollState internal constructor(
    internal val count: Int,
) {
    /** Set by the composable whenever the viewport or the items change. */
    internal var metrics: FlexMetrics? = null

    internal var position = 0f
        private set

    /** Index facing the middle of the row. */
    var active by mutableIntStateOf(0)
        internal set

    /**
     * How open the focused card is, 0 to 1. Observable, unlike the rest: the
     * lens reads it, and the lens is a modifier that should be rebuilt when it
     * changes and at no other time.
     */
    internal var focusAmount by mutableFloatStateOf(0f)
        private set

    /**
     * How far the opened card has moved out of the middle, 0 to 1.
     *
     * A second value rather than a later stretch of the first: the card has to
     * finish arriving in the middle before it begins to leave it, and one
     * spring cannot be made to stop and start again partway through.
     */
    internal var asideAmount by mutableFloatStateOf(0f)
        private set

    internal var focusIndex by mutableIntStateOf(-1)
        private set

    /** Bumped once per frame, so the row repaints while it is settling. */
    internal var tick by mutableIntStateOf(0)
        private set

    /** 0 when still, 1 when moving fast enough to narrow the cards fully. */
    internal var energy = 0f
        private set

    internal var introAge = 0f
        private set

    /** Where the cards landed last frame, for hit testing. */
    internal val placed = ArrayList<PlacedCard>()

    internal class PlacedCard(val index: Int, val left: Float, val right: Float, val rel: Float)

    private var goal = 0f
    private var velocity = 0f
    private var lastPosition = 0f
    private var wheeling = false
    private var wheelAtMs = 0f
    private var dragging = false
    private var focusTarget = 0f
    private var focusVelocity = 0f
    private var asideVelocity = 0f
    private var nowMs = 0f
    private var autoplayAtMs = 0f

    // --- input ---------------------------------------------------------------

    internal fun onDragStart() {
        dragging = true
        wheeling = false
    }

    internal fun onDrag(dx: Float) {
        position -= dx
    }

    /** [throwVelocity] is in pixels a second, positive when flicked leftward. */
    internal fun onDragEnd(throwVelocity: Float) {
        dragging = false
        val m = metrics ?: return
        velocity = throwVelocity
        val landing = snapTo(m, position + throwVelocity * THROW_REACH)
        goal = landing
        // A hard flick that would settle back onto the card it started from is
        // read as intent to move on, not as a wasted gesture.
        if (abs(throwVelocity) > FLICK && abs(landing - position) < 1f) {
            goal = stepFrom(m, landing, if (throwVelocity > 0f) 1 else -1)
        }
    }

    internal fun onWheel(delta: Float) {
        wheeling = true
        wheelAtMs = nowMs
        goal += delta
    }

    /** Centres [index], or opens it when it is already centred. */
    internal fun onTap(index: Int, rel: Float, focusOnClick: Boolean) {
        val m = metrics ?: return
        if (focusAmount > 0.01f || focusTarget == 1f) {
            closeFocus()
            return
        }
        if (abs(rel) < m.widths[index] * 0.5f && focusOnClick) {
            focusIndex = index
            focusTarget = 1f
            goal = snapTo(m, position + rel)
        } else {
            goal = snapTo(m, position + rel)
        }
    }

    internal fun closeFocus() {
        focusTarget = 0f
    }

    /** Move [by] cards. Public so a host can drive the row from its own chrome. */
    fun step(by: Int) {
        val m = metrics ?: return
        goal = stepFrom(m, snapTo(m, goal), by)
    }

    // --- frame ---------------------------------------------------------------

    internal fun advance(nowMs: Float, deltaMs: Float, autoplay: Boolean, intervalMs: Float) {
        this.nowMs = nowMs
        tick++
        val m = metrics ?: return
        val dt = (deltaMs / 1000f).clamped(0.001f, 0.05f)
        introAge += dt

        if (wheeling && nowMs - wheelAtMs > WHEEL_SETTLE_MS) {
            goal = snapTo(m, goal)
            wheeling = false
        }

        if (!dragging) {
            val stiffness = if (wheeling) WHEEL_SPRING else SPRING
            val damping = 2f * sqrt(stiffness)
            // Fixed substeps: one 50ms frame integrated in a single step would
            // overshoot far enough to wind the spring up instead of settling it.
            val steps = ceil(dt / SUBSTEP).toInt().coerceAtLeast(1)
            val h = dt / steps
            repeat(steps) {
                velocity += (stiffness * (goal - position) - damping * velocity) * h
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

        focusVelocity += (FOCUS_SPRING * (focusTarget - focusAmount) -
            2f * sqrt(FOCUS_SPRING) * focusVelocity) * dt
        focusAmount += focusVelocity * dt
        if (abs(focusTarget - focusAmount) < 0.0005f && abs(focusVelocity) < 0.001f) {
            focusAmount = focusTarget
            focusVelocity = 0f
        }

        // Only once the card has all but arrived. On the way back both run
        // together, so closing is one motion rather than the opening replayed
        // backwards a step at a time.
        val asideTarget = if (focusTarget == 1f && focusAmount > ASIDE_AFTER) 1f else 0f
        asideVelocity += (ASIDE_SPRING * (asideTarget - asideAmount) -
            2f * sqrt(ASIDE_SPRING) * asideVelocity) * dt
        asideAmount += asideVelocity * dt
        if (abs(asideTarget - asideAmount) < 0.0005f && abs(asideVelocity) < 0.001f) {
            asideAmount = asideTarget
            asideVelocity = 0f
        }

        if (autoplay && !dragging && focusTarget == 0f && abs(velocity) < 1f) {
            if (autoplayAtMs == 0f) autoplayAtMs = nowMs
            if (nowMs - autoplayAtMs > intervalMs) {
                autoplayAtMs = nowMs
                goal = stepFrom(m, snapTo(m, goal), 1)
            }
        } else {
            autoplayAtMs = nowMs
        }

        val near = nearest(m, position)
        if (active != near) active = near
    }

    // --- layout queries ------------------------------------------------------

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
