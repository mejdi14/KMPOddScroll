package io.github.mejdi14.oddscroll.cdrack

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.mejdi14.oddscroll.internal.clamped
import io.github.mejdi14.oddscroll.internal.cubicBezierEase
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Every timing below was fitted to the rack's position, recovered frame by
 * frame from a recording of the reference: the top-left corner of every tape,
 * pooled, collapses onto one line with one pitch and one number per frame, to
 * a pixel. The reference moves in two ways and they want different curves.
 */

/**
 * A throw: the rack leaves at full speed and eases into the slot. Fitted
 * against a 3.13-tape run in 0.823 s to 0.50 px. The curve starts at
 * 0.501 / 0.283 = 1.770 of its distance per unit of its duration, so a
 * duration of [THROW_SLOPE] x distance / speed hands over at exactly the
 * speed the finger let go at.
 */
private val throwEase = cubicBezierEase(0.283f, 0.501f, 0.698f, 0.955f)
private const val THROW_SLOPE = 1.770f

/**
 * Seconds of parting speed a throw carries the rack on by: the same run went
 * 3.13 tapes from 6.73 tapes a second.
 */
private const val THROW_REACH = 0.465f

/**
 * A run from rest, as a tap or a wheel notch makes: an ease-in-out. The two
 * the reference has, 4.10 tapes in 1.381 s and 6.80 in 1.682 s, fit to 0.6 px
 * each; this is their mean, and the duration grows as the root of the
 * distance, which both come to within five percent of.
 */
private val travelEase = cubicBezierEase(0.280f, 0.027f, 0.248f, 0.935f)
private const val TRAVEL_SECONDS = 0.664f

/** Below this, in tapes a second, a let-go is a drop rather than a throw. */
private const val THROW_FLOOR = 0.4f

/** Drag samples kept to read a throw off the end of a gesture. */
private const val SAMPLES = 5

/** Longest a sample stays worth counting towards the throw, in seconds. */
private const val WINDOW = 0.1f

/**
 * Creates a [CdRackScrollState] that starts with case [initialIndex] in the
 * slot.
 */
@Composable
fun rememberCdRackScrollState(initialIndex: Int = 0): CdRackScrollState =
    remember { CdRackScrollState(initialIndex) }

/**
 * Where the rack has slid to, and the way to slide it from outside.
 *
 * The rack is one number: case `i` stands `i - position` places up the row
 * from the slot, and how high it is lifted is a fixed function of that. So
 * there is nothing per case to animate — moving the number moves everything.
 * The rack has no end, so every index here is wrapped into the list, and a
 * run to one goes whichever way round the rack is shorter.
 *
 * Each call below takes over from a finger that is dragging the rack: the
 * rest of that drag is ignored until it lifts.
 */
class CdRackScrollState internal constructor(initialIndex: Int) {

    /** Unbounded; wrapped only when a case is drawn. */
    internal var position = initialIndex.toFloat()
        private set

    /** Bumped once per frame, so the rack repaints while anything moves. */
    internal var tick by mutableIntStateOf(0)
        private set

    /** How many cases the rack holds, as of its last composition. */
    internal var count by mutableIntStateOf(0)

    /** The case nearest the slot, unwrapped. Changes only as one passes it. */
    private var slotted by mutableIntStateOf(initialIndex)

    /**
     * The case in the slot, or the one nearest it while the rack moves, in
     * `0 until items.size`. Changes only as a case passes the slot.
     */
    val currentIndex: Int
        get() {
            val n = count
            return if (n > 0) wrapCase(slotted, n) else slotted
        }

    /** Whether a finger is dragging the rack or it is on its way to a slot. */
    var isScrollInProgress by mutableStateOf(false)
        private set

    /**
     * How far the rack has got drawing its cases, from 0 to 1. Each case is
     * drawn once, off the main thread, when the rack first has a size and
     * again whenever that size changes; until this reaches 1, cases not drawn
     * yet are missing from the row. A host that wants a loader can cover the
     * rack with its own until then.
     */
    var loaded by mutableFloatStateOf(0f)
        internal set

    private var dragging = false
    private var moving = false
    private var from = 0f
    private var target = initialIndex
    private var seconds = 0f
    private var clock = 0f
    private var ease = travelEase

    /** Cases a second, as of the last frame — what a retarget starts from. */
    private var speed = 0f

    private val at = FloatArray(SAMPLES)
    private val of = FloatArray(SAMPLES)
    private var taken = 0

    /** Puts case [index] in the slot at once, with no slide. */
    fun scrollTo(index: Int) {
        dragging = false
        val to = nearest(index)
        moving = false
        speed = 0f
        target = to
        position = to.toFloat()
        settle()
    }

    /** Slides case [index] into the slot with the rack's own ease-in-out. */
    fun animateScrollTo(index: Int) {
        dragging = false
        aim(nearest(index), speed)
    }

    /**
     * Slides the rack on by [by] cases from wherever it is heading — what a
     * wheel notch does. Positive brings the cases further up the row down to
     * the slot.
     */
    fun step(by: Int) {
        if (by == 0) return
        dragging = false
        aim(heading() + by, speed)
    }

    /** Slides unwrapped case [index] into the slot, the way a tap on it does. */
    internal fun travelTo(index: Int) {
        if (dragging) return
        aim(index, speed)
    }

    /** The case the rack is on its way to, or the one nearest the slot at rest; unwrapped. */
    private fun heading(): Int = if (moving) target else position.roundToInt()

    /** The unwrapped case nearest where the rack is heading that is case [index] of the list. */
    private fun nearest(index: Int): Int {
        val n = count
        val here = heading()
        if (n <= 0) return index
        val ahead = wrapCase(index - here, n)
        return here + if (ahead > n / 2) ahead - n else ahead
    }

    // --- input ---------------------------------------------------------------

    internal fun onDragStart(seconds: Float) {
        dragging = true
        moving = false
        speed = 0f
        taken = 0
        sample(seconds)
        settle()
    }

    /** [by] is the drag down the row, already in cases. */
    internal fun onDrag(by: Float, seconds: Float) {
        if (!dragging) return
        position += by
        sample(seconds)
    }

    internal fun onDragEnd() {
        if (!dragging) return
        dragging = false
        val parting = partingSpeed()
        aim((position + parting * THROW_REACH).roundToInt(), parting)
    }

    private fun aim(to: Int, startSpeed: Float) {
        val distance = to - position
        target = to
        from = position
        clock = 0f
        if (abs(distance) < 1e-4f) {
            moving = false
            position = to.toFloat()
            settle()
            return
        }
        moving = true
        if (startSpeed * distance > 0f && abs(startSpeed) > THROW_FLOOR) {
            // Already going the right way: leave at that speed.
            ease = throwEase
            seconds = (THROW_SLOPE * abs(distance) / abs(startSpeed)).clamped(0.25f, 1.4f)
        } else {
            ease = travelEase
            seconds = (TRAVEL_SECONDS * sqrt(abs(distance))).clamped(0.35f, 2.2f)
        }
        settle()
    }

    // --- frame ---------------------------------------------------------------

    internal fun advance(deltaMs: Float) {
        tick++
        val dt = deltaMs / 1000f
        val before = position
        if (moving) {
            clock += dt
            val t = (clock / seconds).clamped(0f, 1f)
            position = from + (target - from) * ease(t)
            if (t >= 1f) {
                position = target.toFloat()
                moving = false
            }
        }
        if (!dragging) speed = if (dt > 0f) (position - before) / dt else 0f
        settle()
    }

    /** Brings the observable values up to date; each is written only when it changes. */
    private fun settle() {
        val nearest = position.roundToInt()
        if (nearest != slotted) slotted = nearest
        val busy = dragging || moving
        if (busy != isScrollInProgress) isScrollInProgress = busy
    }

    // --- throw ---------------------------------------------------------------

    private fun sample(seconds: Float) {
        at[taken % SAMPLES] = seconds
        of[taken % SAMPLES] = position
        taken++
    }

    /** Cases a second over the tail of the drag, as it was let go. */
    private fun partingSpeed(): Float {
        if (taken < 2) return 0f
        val newest = (taken - 1) % SAMPLES
        var oldest = newest
        for (i in 1 until minOf(taken, SAMPLES)) {
            val index = (taken - 1 - i + SAMPLES * SAMPLES) % SAMPLES
            if (at[newest] - at[index] > WINDOW) break
            oldest = index
        }
        val span = at[newest] - at[oldest]
        return if (span <= 1e-4f) 0f else (of[newest] - of[oldest]) / span
    }
}

/** Index of case [index] of the endless rack, wrapped into [count]. */
internal fun wrapCase(index: Int, count: Int): Int = ((index % count) + count) % count
