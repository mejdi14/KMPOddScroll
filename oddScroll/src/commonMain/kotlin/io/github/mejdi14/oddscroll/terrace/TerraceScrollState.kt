package io.github.mejdi14.oddscroll.terrace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.mejdi14.oddscroll.internal.frameRate
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The settle, fitted to the stack's position recovered frame by frame from a
 * recording of the reference. It is not a spring: a free damped-spring fit
 * comes back critically damped with no oscillation in four windows of five,
 * and a spring from rest would have to start eight frames before the stack
 * visibly moves. What it is is a first-order approach — the stack covers the
 * same share of what is left every frame — at 5.47 a second (5.03 to 5.97
 * over five separate settles, 0.3 px each). The share a frame takes is the
 * style's `settle` parameter, 0.0871 by default.
 */

/**
 * Seconds of parting speed a throw carries the stack on by. A first-order
 * settle that leaves at speed v comes to rest v / 5.47 further on, so aiming
 * the throw that far hands over at exactly the speed the finger let go at.
 */
private const val THROW_REACH = 0.183f

/** Wheel quiet for this long, in seconds, and the stack settles on a card. */
private const val WHEEL_REST = 0.12f

/** Below this, in cards, the stack is treated as arrived. */
private const val ARRIVED = 0.0005f

/** Drag samples kept to read a throw off the end of a gesture. */
private const val SAMPLES = 5

/** Longest a sample stays worth counting towards the throw, in seconds. */
private const val WINDOW = 0.1f

/**
 * Creates and remembers a [TerraceScrollState].
 *
 * @param initialIndex the item that starts at the front.
 */
@Composable
fun rememberTerraceScrollState(initialIndex: Int = 0): TerraceScrollState =
    remember { TerraceScrollState(initialIndex) }

/**
 * How far the stack has run, in cards.
 *
 * The stack is one number: card `i` sits `i - position` levels from the front,
 * and its size and height are fixed functions of that. Unbounded, so the run
 * has no end.
 *
 * A drag or the wheel moves the number directly, as the reference's wheel
 * does; once the input stops, the stack settles on the nearest card.
 */
class TerraceScrollState internal constructor(initialIndex: Int = 0) {

    internal var position = initialIndex.toFloat()
        private set

    /** How many items the stack is made of; set by the style from its list. */
    internal var itemCount = 1

    /** Share of the distance left that the stack covers each sixtieth of a second. */
    internal var settle = 0.0871f

    /** Bumped once per frame, so the stack repaints while anything moves. */
    internal var tick by mutableIntStateOf(0)
        private set

    /** The card at the front, unwrapped. Changes only as one passes halfway. */
    internal var front by mutableIntStateOf(initialIndex)
        private set

    /**
     * Index, wrapped into the items, of the card at the front. It changes as
     * a card passes halfway, so mid-run as well as at rest.
     */
    val currentIndex: Int get() = wrapCard(front, itemCount)

    private var dragging by mutableStateOf(false)
    private var settling by mutableStateOf(false)

    /** True while the stack is dragged, thrown, scrolled or settling on a card. */
    val isScrollInProgress: Boolean get() = dragging || settling

    private var target = 0f
    private var wheeling = false
    private var sinceWheel = 0f

    private val at = FloatArray(SAMPLES)
    private val of = FloatArray(SAMPLES)
    private var taken = 0

    /** Brings the unwrapped card [index] to the front, whichever way round it lies. */
    internal fun goTo(index: Int) {
        if (dragging) return
        wheeling = false
        aim(index.toFloat())
    }

    /** Which way and how far, in cards, to run to reach item [index] from where it is heading. */
    private fun distanceTo(index: Int): Int {
        val from = (if (settling) target else position).roundToInt()
        var by = wrapCard(index - from, itemCount)
        if (by > itemCount / 2) by -= itemCount
        return by
    }

    /** Runs the stack to item [index] with its own settle, the short way round. */
    fun animateScrollTo(index: Int) {
        if (dragging) return
        step(distanceTo(index))
    }

    /** Puts item [index] at the front at once, the short way round. */
    fun scrollTo(index: Int) {
        if (dragging) return
        wheeling = false
        val to = (if (settling) target else position).roundToInt() + distanceTo(index)
        target = to.toFloat()
        position = target
        settling = false
        front = to
    }

    /** Runs the stack on by [by] cards from wherever it is heading. */
    fun step(by: Int) {
        if (by == 0 || dragging) return
        wheeling = false
        aim((if (settling) target else position).roundToInt() + by.toFloat())
    }

    // --- input ---------------------------------------------------------------

    internal fun onDragStart(seconds: Float) {
        dragging = true
        settling = false
        wheeling = false
        taken = 0
        sample(seconds)
    }

    /** [by] is the drag up the frame, already in cards. */
    internal fun onDrag(by: Float, seconds: Float) {
        if (!dragging) return
        position += by
        sample(seconds)
    }

    internal fun onDragEnd() {
        if (!dragging) return
        dragging = false
        aim((position + partingSpeed() * THROW_REACH).roundToInt().toFloat())
    }

    /**
     * [by] is in cards. The wheel runs the stack straight on — a notch is a
     * card — and only lets the settle take it once it goes quiet.
     */
    internal fun onWheel(by: Float) {
        if (dragging) return
        if (!wheeling) target = if (settling) target else position
        wheeling = true
        sinceWheel = 0f
        target += by
        settling = true
    }

    private fun aim(to: Float) {
        target = to
        settling = abs(target - position) > ARRIVED
        if (!settling) position = target
    }

    // --- frame ---------------------------------------------------------------

    internal fun advance(deltaMs: Float) {
        tick++
        if (wheeling) {
            sinceWheel += deltaMs / 1000f
            if (sinceWheel > WHEEL_REST) {
                wheeling = false
                target = target.roundToInt().toFloat()
            }
        }
        if (settling && !dragging) {
            position += (target - position) * frameRate(settle, deltaMs)
            if (!wheeling && abs(target - position) < ARRIVED) {
                position = target
                settling = false
            }
        }
        val nearest = position.roundToInt()
        if (nearest != front) front = nearest
    }

    // --- throw ---------------------------------------------------------------

    private fun sample(seconds: Float) {
        at[taken % SAMPLES] = seconds
        of[taken % SAMPLES] = position
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

/** Index of card [index] of the endless stack, wrapped into [count]. */
internal fun wrapCard(index: Int, count: Int): Int = ((index % count) + count) % count
