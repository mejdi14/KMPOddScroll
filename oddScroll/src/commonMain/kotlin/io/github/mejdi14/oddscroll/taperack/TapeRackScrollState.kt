package io.github.mejdi14.oddscroll.taperack

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
 * Where the rack has slid to, in tapes.
 *
 * The rack is one number: tape `i` stands `i - position` pitches up the row
 * from the slot, and how high it is lifted is a fixed function of that. So
 * there is nothing per tape to animate — moving the number moves everything.
 */
class TapeRackScrollState internal constructor(intro: Int) {

    /** Unbounded; wrapped only when a tape is drawn. */
    internal var position = 0f
        private set

    /** Bumped once per frame, so the rack repaints while anything moves. */
    internal var tick by mutableIntStateOf(0)
        private set

    /** The tape nearest the slot, unwrapped. Changes only as one passes it. */
    var slotted by mutableIntStateOf(0)
        private set

    private var dragging = false
    private var moving = false
    private var from = 0f
    private var target = 0
    private var seconds = 0f
    private var clock = 0f
    private var ease = travelEase

    /** Tapes a second, as of the last frame — what a retarget starts from. */
    private var speed = 0f

    private val at = FloatArray(SAMPLES)
    private val of = FloatArray(SAMPLES)
    private var taken = 0

    init {
        if (intro != 0) aim(intro, 0f)
    }

    /** Slides tape [index] into the slot. Unwrapped, so it can go either way round. */
    fun scrollTo(index: Int) {
        if (dragging) return
        aim(index, speed)
    }

    /** Slides the rack on by [by] tapes from wherever it is heading. */
    fun step(by: Int) {
        if (by == 0 || dragging) return
        aim((if (moving) target else position.roundToInt()) + by, speed)
    }

    // --- input ---------------------------------------------------------------

    internal fun onDragStart(seconds: Float) {
        dragging = true
        moving = false
        speed = 0f
        taken = 0
        sample(seconds)
    }

    /** [by] is the drag down the row, already in tapes. */
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
        val nearest = position.roundToInt()
        if (nearest != slotted) slotted = nearest
    }

    // --- throw ---------------------------------------------------------------

    private fun sample(seconds: Float) {
        at[taken % SAMPLES] = seconds
        of[taken % SAMPLES] = position
        taken++
    }

    /** Tapes a second over the tail of the drag, as it was let go. */
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

/** Index of tape [index] of the endless rack, wrapped into [count]. */
internal fun wrapTape(index: Int, count: Int): Int = ((index % count) + count) % count
