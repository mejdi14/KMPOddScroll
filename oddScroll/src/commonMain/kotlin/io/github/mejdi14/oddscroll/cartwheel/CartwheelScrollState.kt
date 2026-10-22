package io.github.mejdi14.oddscroll.cartwheel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.github.mejdi14.oddscroll.internal.clamped
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Drag samples kept to read a throw off the end of a gesture. */
private const val SAMPLES = 5

/** Longest a sample stays worth counting towards the throw, in seconds. */
private const val WINDOW = 0.1f

/** Below this the wheel is treated as stopped, in cards a second. */
private const val AT_REST = 0.004f

/** Below this the wheel is treated as arrived, in cards. */
private const val ARRIVED = 0.0004f

/** Longest a spring step may be, so a dropped frame cannot wind it up. */
private const val MAX_STEP = 0.004f

/**
 * How far the wheel has turned, in cards.
 *
 * Unbounded, and wrapped only when a card is placed, so the wheel keeps going
 * the same way for as long as it is thrown and has no end to reach.
 *
 * A drag turns the wheel directly. Letting go hands it to a spring aimed
 * [throwSeconds] of the parting speed further on, which is what gives the long
 * coast and then the small bounce back at the far end of it.
 */
class CartwheelScrollState internal constructor(
    internal var dampingRatio: Float,
    internal var stiffness: Float,
    internal var throwSeconds: Float,
) {
    /** Wheel angle, in cards. */
    internal var phase = 0f
        private set

    /** Card currently nearest the middle. */
    var active by mutableIntStateOf(0)
        internal set

    /** Bumped once per frame, so the wheel repaints while it turns. */
    internal var tick by mutableIntStateOf(0)
        private set

    internal var dragging = false
        private set

    private var velocity = 0f
    private var target = 0f
    private var settling = false

    private val at = FloatArray(SAMPLES)
    private val of = FloatArray(SAMPLES)
    private var taken = 0

    // --- input ---------------------------------------------------------------

    internal fun onDragStart(seconds: Float) {
        dragging = true
        settling = false
        velocity = 0f
        taken = 0
        sample(seconds)
    }

    /** [by] is the drag along the rim, already in cards. */
    internal fun onDrag(by: Float, seconds: Float) {
        phase += by
        sample(seconds)
    }

    internal fun onDragEnd() {
        dragging = false
        velocity = partingSpeed()
        target = phase + velocity * throwSeconds
        settling = true
    }

    internal fun onWheel(by: Float) {
        // A notch is a nudge, not a throw: aim a little further on from
        // wherever the wheel is already headed and let the same spring take it.
        aim(by)
    }

    /** Turn by [by] cards. Public so a host can drive it from its own chrome. */
    fun step(by: Int) = aim(by.toFloat())

    private fun aim(by: Float) {
        if (!settling) target = phase
        target += by
        settling = true
    }

    // --- frame ---------------------------------------------------------------

    internal fun advance(deltaMs: Float, count: Int) {
        tick++
        if (settling) {
            // A curve that overshoots cannot be written as an easing, so the
            // settle is integrated: these are the damping and stiffness read
            // off the recording, in real seconds.
            val dt = (deltaMs / 1000f).clamped(0.0005f, 0.05f)
            val omega = sqrt(stiffness.coerceAtLeast(0.01f))
            val steps = (dt / MAX_STEP).toInt().coerceIn(1, 16)
            val h = dt / steps
            repeat(steps) {
                velocity += (-omega * omega * (phase - target) -
                    2f * dampingRatio * omega * velocity) * h
                phase += velocity * h
            }
            if (abs(velocity) < AT_REST && abs(phase - target) < ARRIVED) {
                phase = target
                velocity = 0f
                settling = false
            }
        }
        if (count > 0) {
            val near = ((phase.roundToInt() % count) + count) % count
            if (active != near) active = near
        }
    }

    // --- throw ---------------------------------------------------------------

    private fun sample(seconds: Float) {
        at[taken % SAMPLES] = seconds
        of[taken % SAMPLES] = phase
        taken++
    }

    /** Cards a second over the tail of the drag, as it was let go. */
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

/** Index of the card [steps] along from the wheel's zero, wrapped into [count]. */
internal fun wrapIndex(steps: Int, count: Int): Int =
    ((steps % count) + count) % count

/** Whole cards the wheel has already turned past. */
internal fun cardsBelow(phase: Float): Int = floor(phase).toInt()
