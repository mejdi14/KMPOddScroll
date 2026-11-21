package io.github.mejdi14.oddscroll.cartwheel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.setValue
import io.github.mejdi14.oddscroll.internal.clamped
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
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
    initialIndex: Int,
) {
    /** Wheel angle, in cards. */
    internal var phase = initialIndex.toFloat()
        private set

    /** Wrapped index of the card nearest the middle. */
    var currentIndex by mutableIntStateOf(initialIndex.coerceAtLeast(0))
        private set

    /** True while a drag is held or the wheel is still coasting to where it was aimed. */
    var isScrollInProgress by mutableStateOf(false)
        private set

    /** Cards on the wheel, so an index can be wrapped. Kept up to date by the composable. */
    internal var itemCount = 0

    /** Bumped once per frame, so the wheel repaints while it turns. */
    internal var tick by mutableIntStateOf(0)
        private set

    internal var dragging = false
        private set

    private var velocity = 0f
    private var target = initialIndex.toFloat()
    private var settling = false

    private val at = FloatArray(SAMPLES)
    private val of = FloatArray(SAMPLES)
    private var taken = 0

    // --- input ---------------------------------------------------------------

    internal fun onDragStart(seconds: Float) {
        dragging = true
        settling = false
        isScrollInProgress = true
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
        isScrollInProgress = true
    }

    internal fun onWheel(by: Float) {
        // A notch is a nudge, not a throw: aim a little further on from
        // wherever the wheel is already headed and let the same spring take it.
        aim(by)
    }

    /** Turns the wheel by [by] cards, with the same spring a throw settles on. */
    fun step(by: Int) = aim(by.toFloat())

    /**
     * Puts card [index] at the middle with no animation, taking the way round
     * that is nearest to where the wheel is now.
     */
    fun scrollTo(index: Int) {
        if (itemCount <= 0) return
        phase += wrapHalf(index - phase, itemCount)
        velocity = 0f
        settling = false
        if (!dragging) isScrollInProgress = false
        updateIndex()
    }

    /**
     * Turns the wheel to put card [index] at the middle, by the nearer way
     * round and on the same spring a throw settles on.
     */
    fun animateScrollTo(index: Int) {
        if (itemCount <= 0) return
        if (!settling) target = phase
        aim(wrapHalf(index - target, itemCount))
    }

    private fun aim(by: Float) {
        if (!settling) target = phase
        target += by
        settling = true
        isScrollInProgress = true
    }

    /** Card whose square contains [at], in dp; the nearest one if they overlap; -1 if none. */
    internal fun pick(at: Offset): Int {
        var found = -1
        // Drawn in order, so the last match is the one on top.
        for (hit in hits) {
            val dx = at.x - hit.x
            val dy = at.y - hit.y
            val c = cos(-hit.angle)
            val s = sin(-hit.angle)
            val lx = dx * c - dy * s
            val ly = dx * s + dy * c
            if (abs(lx) <= hit.half && abs(ly) <= hit.half) found = hit.index
        }
        return found
    }

    /** Where the cards landed last frame, in dp, in the order they were drawn. */
    internal val hits = ArrayList<Hit>()

    internal class Hit(
        val index: Int,
        val x: Float,
        val y: Float,
        val half: Float,
        val angle: Float,
    )

    private fun updateIndex() {
        if (itemCount <= 0) return
        val near = ((phase.roundToInt() % itemCount) + itemCount) % itemCount
        if (currentIndex != near) currentIndex = near
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
                if (!dragging) isScrollInProgress = false
            }
        }
        itemCount = count
        updateIndex()
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

/** Signed distance from [value] to zero around a cycle of [size]. */
private fun wrapHalf(value: Float, size: Int): Float {
    val half = size / 2f
    return ((value + half) % size + size) % size - half
}

/** Index of the card [steps] along from the wheel's zero, wrapped into [count]. */
internal fun wrapIndex(steps: Int, count: Int): Int =
    ((steps % count) + count) % count

/** Whole cards the wheel has already turned past. */
internal fun cardsBelow(phase: Float): Int = floor(phase).toInt()
