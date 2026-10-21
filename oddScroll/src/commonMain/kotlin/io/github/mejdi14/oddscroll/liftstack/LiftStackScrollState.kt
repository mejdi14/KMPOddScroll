package io.github.mejdi14.oddscroll.liftstack

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.mejdi14.oddscroll.internal.clamped
import io.github.mejdi14.oddscroll.internal.cubicBezierEase
import io.github.mejdi14.oddscroll.internal.frameRate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/*
 * Every timing below was read off a recording of the reference, fitted to the
 * deck's phase and to the lifted card's pose frame by frame. The deck moves in
 * two pieces, and one curve will not do for both: a run through several cards
 * that starts from rest and is still moving when it hands over, then the last
 * card on a long ease-out. The lift and the turn upright are their own clocks
 * again, started off the last card's.
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

/** A card lays back down this many times faster than it got up. */
private const val LOWER_RATE = 3f

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

/**
 * How far the deck has run, in cards, and which card is standing up off it.
 *
 * The deck is one number: card `i` sits [phase] cards from the top slot, at
 * `i - phase`, and the whole stack is a function of that. The card the deck has
 * just run past — one before the [target] it rests on — is the one that stands
 * up, held in the top slot rather than fading out above it.
 */
class LiftStackScrollState internal constructor(intro: Int) {

    /** Deck position, in cards. Unbounded; wrapped only when a card is drawn. */
    internal var phase = 0f
        private set

    /** Card the deck is resting on, or heading for. */
    private var target = intro

    /** The card standing, or getting up. */
    internal val current = Raised(target - 1)

    /** The card that stood before it, on its way back down. */
    internal val leaving = Raised(Int.MIN_VALUE)

    /** Bumped once per frame, so the stack repaints while anything moves. */
    internal var tick by mutableIntStateOf(0)
        private set

    /** Index, unwrapped, of the card standing fully up; [Int.MIN_VALUE] when none. */
    internal var standing by mutableIntStateOf(Int.MIN_VALUE)
        private set

    /** Counts each time a card finishes getting up, so its caption is let in afresh. */
    internal var stood by mutableIntStateOf(0)
        private set

    internal var dragging by mutableStateOf(false)
        private set

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
        if (intro != 0) {
            plan(intro.toFloat(), fromRest = true)
        } else {
            // No opening run: the card before the first is already up.
            current.seconds = LIFT_SECONDS
            current.raising = true
            current.pin = 1f
        }
    }

    /** One card that is up, or getting up, or lying back down. */
    internal class Raised(var index: Int) {
        /** Seconds into the rise; run backwards to lower it. */
        var seconds = 0f
        var raising = false

        /** How firmly it is held in the top slot rather than riding the deck. */
        var pin = 0f

        val lift: Float get() = liftEase((seconds / LIFT_SECONDS).clamped(0f, 1f))
        val stand: Float get() = standEase(((seconds - STAND_DELAY) / STAND_SECONDS).clamped(0f, 1f))
        val up: Float get() = (seconds / LIFT_SECONDS).clamped(0f, 1f)
    }

    // --- input ---------------------------------------------------------------

    internal fun onDragStart(seconds: Float) {
        dragging = true
        moving = false
        current.raising = false
        taken = 0
        sample(seconds)
    }

    /** [by] is the drag up the deck, already in cards. */
    internal fun onDrag(by: Float, seconds: Float) {
        phase += by
        sample(seconds)
    }

    internal fun onDragEnd() {
        dragging = false
        val aim = phase + partingSpeed() * THROW_SECONDS
        aimAt(aim.roundToInt(), fromRest = false)
    }

    /** Run the deck on by [by] cards. Public so a host can drive it from its own chrome. */
    fun step(by: Int) {
        if (by == 0 || dragging) return
        aimAt(target + by, fromRest = !moving)
    }

    private fun aimAt(next: Int, fromRest: Boolean) {
        if (next - 1 != current.index) {
            // The card standing gives way to the one the deck will now rest
            // past. Only one can be lowering at once; if another was still on
            // its way down it simply goes the rest of the way at once.
            leaving.index = current.index
            leaving.seconds = current.seconds
            leaving.pin = current.pin
            leaving.raising = false
            current.index = next - 1
            current.seconds = 0f
            current.pin = 0f
        }
        current.raising = false
        target = next
        plan(next.toFloat(), fromRest)
    }

    private fun plan(to: Float, fromRest: Boolean) {
        val distance = to - phase
        moving = true
        clock = 0f
        if (fromRest && abs(distance) > 1f) {
            // From rest the deck runs up to speed through all but the last
            // card, and hands that one to the settle while still moving.
            runFrom = phase
            runTo = to - sign(distance)
            runSeconds = (SPIN_SECONDS * sqrt((abs(distance) - 1f) / SPIN_CARDS))
                .clamped(0.4f, 2.4f)
        } else {
            // Out of a throw the deck is already moving, so it goes straight
            // to the long ease-out over the whole of the distance.
            runFrom = phase
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
                // The rise is timed off the last card: it starts as that card
                // starts in.
                current.raising = true
                if (t >= 1f) {
                    phase = target.toFloat()
                    moving = false
                }
            }
        }

        val pull = frameRate(0.2f, deltaMs)
        current.seconds = if (current.raising) {
            (current.seconds + dt).coerceAtMost(LIFT_SECONDS)
        } else {
            (current.seconds - dt * LOWER_RATE).coerceAtLeast(0f)
        }
        val held = !dragging
        current.pin += ((if (held) 1f else 0f) - current.pin) * pull
        // A card that is not held lets go of the top slot no faster than it
        // gets down, so it never slides out from under itself mid-air.
        if (!held) current.pin = minOf(current.pin, max(current.up, 0f))

        if (leaving.index != Int.MIN_VALUE) {
            leaving.seconds = (leaving.seconds - dt * LOWER_RATE).coerceAtLeast(0f)
            leaving.pin = minOf(leaving.pin, leaving.up)
            if (leaving.seconds <= 0f && leaving.pin <= 0.001f) leaving.index = Int.MIN_VALUE
        }

        // The caption comes in once the card is most of the way up, and stays
        // until it is all but down again.
        val up = when {
            current.raising && current.stand > 0.62f -> current.index
            standing == current.index && current.stand > 0.02f -> current.index
            standing == leaving.index && leaving.stand > 0.02f -> leaving.index
            else -> Int.MIN_VALUE
        }
        if (up != standing) {
            if (up != Int.MIN_VALUE) stood++
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
