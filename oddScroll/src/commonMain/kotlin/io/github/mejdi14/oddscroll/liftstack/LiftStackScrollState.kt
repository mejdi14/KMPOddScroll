package io.github.mejdi14.oddscroll.liftstack

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.mejdi14.oddscroll.internal.clamped
import io.github.mejdi14.oddscroll.internal.cubicBezierEase
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/*
 * Every timing below was read off a recording of the reference, fitted to the
 * deck's phase and to the lifted card's pose frame by frame. The deck moves in
 * two pieces, and one curve will not do for both: a run through several cards
 * that starts from rest and is still moving when it hands over, then the last
 * card on a long ease-out. The lift and the turn upright are their own clocks
 * again, the turn started a little way into the lift.
 */

/** The run through the deck: six cards in this long, from rest. */
private const val SPIN_SECONDS = 1.496f
private const val SPIN_CARDS = 6f
private val spinEase = cubicBezierEase(0.719f, 0.034f, 0.249f, 0.717f)

/** The last card in: most of it in the first second, the rest let down. */
private const val SETTLE_SECONDS = 3.062f
private val settleEase = cubicBezierEase(0.055f, 0.157f, 0.100f, 0.910f)

/** The rise off the deck, with the slight dip before it that the reference has. */
internal const val LIFT_SECONDS = 2.517f
private val liftEase = cubicBezierEase(0.603f, -0.164f, 0.025f, 1.000f)

/** The turn upright starts this long into the rise, and takes this long. */
private const val STAND_DELAY = 0.332f
private const val STAND_SECONDS = 1.602f
private val standEase = cubicBezierEase(0.483f, 0.018f, 0.130f, 1.034f)

/**
 * Seconds of parting speed a throw is aimed on by. The settle curve starts at
 * 0.93 of its distance a second, so a throw this long leaves the deck moving
 * at the speed the finger let go of it.
 */
private const val THROW_SECONDS = 1.07f

/** Drag samples kept to read a throw off the end of a gesture. */
private const val SAMPLES = 5

/** Longest a sample stays worth counting towards the throw, in seconds. */
private const val WINDOW = 0.1f

/** No card selected. */
internal const val NONE = Int.MIN_VALUE

/**
 * How far the deck has run, in cards, and which card is up off it.
 *
 * The deck is one number: card `i` sits [phase] cards from the top slot, at
 * `i - phase`, and the whole stack is a function of that.
 *
 * Tapping the card in the top slot selects it: it lifts off the deck and turns
 * upright. Tapping again runs the same animation backwards and lays it back in
 * its slot. From the moment a card is selected until it is all the way back
 * down, the deck does not scroll.
 */
class LiftStackScrollState internal constructor(intro: Int) {

    /** Deck position, in cards. Unbounded; wrapped only when a card is drawn. */
    internal var phase = 0f
        private set

    /** Card the deck is resting on, or heading for. */
    private var target = intro

    /** The card that is up, getting up, or going back down. */
    internal val selected = Raised()

    /** Bumped once per frame, so the stack repaints while anything moves. */
    internal var tick by mutableIntStateOf(0)
        private set

    /** Index, unwrapped, of the card standing up for its caption; [NONE] when none. */
    internal var standing by mutableIntStateOf(NONE)
        private set

    /** Counts each time a card gets up, so its caption is let in afresh. */
    internal var stood by mutableIntStateOf(0)
        private set

    internal var dragging by mutableStateOf(false)
        private set

    /** True while a card is selected or still on its way back down. */
    val locked: Boolean get() = selected.index != NONE

    // A move is a run to [runTo], then the last card to [target]. A move
    // that has only the one card to go starts at the settle.
    private var moving = false
    private var runFrom = 0f
    private var runTo = 0f
    private var runSeconds = 0f
    private var settleFrom = 0f
    private var clock = 0f

    private val at = FloatArray(SAMPLES)
    private val of = FloatArray(SAMPLES)
    private var taken = 0

    init {
        if (intro != 0) plan(intro.toFloat(), fromRest = true)
    }

    /** The selected card and where it is in getting up. */
    internal class Raised {
        var index = NONE

        /** Seconds into the rise; run backwards to lay it down again. */
        var seconds = 0f
        var raising = false

        val lift: Float get() = liftEase((seconds / LIFT_SECONDS).clamped(0f, 1f))
        val stand: Float get() = standEase(((seconds - STAND_DELAY) / STAND_SECONDS).clamped(0f, 1f))
        val up: Float get() = (seconds / LIFT_SECONDS).clamped(0f, 1f)
    }

    /** The card nearest the top slot. */
    internal val top: Int get() = phase.roundToInt()

    // --- input ---------------------------------------------------------------

    /**
     * A tap on the card in the top slot. Selects it if nothing is; otherwise
     * turns whatever is selected round, up or back down, from wherever it is.
     */
    internal fun onTap(onTop: Boolean) {
        when {
            selected.index != NONE -> selected.raising = !selected.raising
            // Only the card the deck is coming to rest on: one tapped on its
            // way through would be left standing over a slot that has moved on.
            onTop && !dragging && top == target -> {
                selected.index = top
                selected.seconds = 0f
                selected.raising = true
            }
        }
    }

    internal fun onDragStart(seconds: Float) {
        if (locked) return
        dragging = true
        moving = false
        taken = 0
        sample(seconds)
    }

    /** [by] is the drag up the deck, already in cards. */
    internal fun onDrag(by: Float, seconds: Float) {
        if (!dragging) return
        phase += by
        sample(seconds)
    }

    internal fun onDragEnd() {
        if (!dragging) return
        dragging = false
        val aim = phase + partingSpeed() * THROW_SECONDS
        target = aim.roundToInt()
        plan(target.toFloat(), fromRest = false)
    }

    /**
     * Run the deck on by [by] cards. Public so a host can drive it from its own
     * chrome; does nothing while a card is selected.
     */
    fun step(by: Int) {
        if (by == 0 || dragging || locked) return
        target += by
        plan(target.toFloat(), fromRest = !moving)
    }

    private fun plan(to: Float, fromRest: Boolean) {
        val distance = to - phase
        moving = true
        clock = 0f
        runFrom = phase
        if (fromRest && abs(distance) > 1f) {
            // From rest the deck runs up to speed through all but the last
            // card, and hands that one to the settle while still moving.
            runTo = to - sign(distance)
            runSeconds = (SPIN_SECONDS * sqrt((abs(distance) - 1f) / SPIN_CARDS))
                .clamped(0.4f, 2.4f)
        } else {
            // Out of a throw the deck is already moving, so it goes straight
            // to the long ease-out over the whole of the distance.
            runTo = phase
            runSeconds = 0f
        }
        settleFrom = runTo
    }

    // --- frame ---------------------------------------------------------------

    internal fun advance(deltaMs: Float) {
        tick++
        val dt = deltaMs / 1000f

        if (moving) {
            clock += dt
            if (clock < runSeconds) {
                phase = runFrom + (runTo - runFrom) * spinEase(clock / runSeconds)
            } else {
                val t = ((clock - runSeconds) / SETTLE_SECONDS).clamped(0f, 1f)
                phase = settleFrom + (target - settleFrom) * settleEase(t)
                if (t >= 1f) {
                    phase = target.toFloat()
                    moving = false
                }
            }
        }

        if (selected.index != NONE) {
            // Down is the same animation run backwards, at the same pace.
            selected.seconds = if (selected.raising) {
                (selected.seconds + dt).coerceAtMost(LIFT_SECONDS)
            } else {
                (selected.seconds - dt).coerceAtLeast(0f)
            }
            if (!selected.raising && selected.seconds <= 0f) selected.index = NONE
        }

        // The caption comes in once the card is most of the way up, and stays
        // until it is all but down again.
        val up = when {
            selected.index == NONE -> NONE
            selected.raising && selected.stand > 0.62f -> selected.index
            standing == selected.index && selected.stand > 0.02f -> selected.index
            else -> NONE
        }
        if (up != standing) {
            if (up != NONE) stood++
            standing = up
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

/** Index of card [index] of the endless deck, wrapped into [count]. */
internal fun wrapItem(index: Int, count: Int): Int = ((index % count) + count) % count
