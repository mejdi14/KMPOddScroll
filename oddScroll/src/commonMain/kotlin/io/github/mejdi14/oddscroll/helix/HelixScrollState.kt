package io.github.mejdi14.oddscroll.helix

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.github.mejdi14.oddscroll.internal.clamped
import io.github.mejdi14.oddscroll.internal.frameRate
import io.github.mejdi14.oddscroll.internal.inOutCubic
import io.github.mejdi14.oddscroll.internal.wrapDelta
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/* Navigation. Drag and wheel both advance a destination value that the column
   chases; once input has stayed quiet long enough to count as finished, the
   destination is rounded to the nearest card. */
private const val DRAG = 0.007f
private const val WHEEL = 0.0022f
private const val FLING = 4f
private const val EASE = 0.075f
private const val SNAP_IDLE_MS = 300f
private const val SNAP_EASE = 0.055f

/* Bringing a card to the front runs on a fixed duration rather than a decay
   rate, so the traversal takes equally long over one slot or six. */
private const val TAP_MS = 1300f

private const val BEND_EASE = 0.12f
private const val BEND_MAX = 0.07f

/* The arrival is one gesture applied to the whole column rather than a
   per-card schedule: it glides in from behind and should still be slowing as it
   reaches the facing slot. */
private const val ENTRY_SPIN = 9.4f
private const val ENTRY_SPIN_MS = 2400f

/** Slots over which the dimming of unselected cards ramps to full. */
private const val FOCUS_FALLOFF = 1.35f

/**
 * Everything that moves in a [HelixScroll]: where the column is, where it is
 * heading, and how far each card has fallen out of focus.
 *
 * Driven from a `withFrameNanos` loop rather than from `Animatable`, because one
 * progression value has to stay in step across the whole frame — the geometry,
 * the trailing deformation and the depth cueing are all read off it.
 */
class HelixScrollState internal constructor(
    internal val count: Int,
    playEntry: Boolean,
) {
    /** Fractional position of the column, in cards. */
    internal var progress by mutableFloatStateOf(0f)
        private set

    /** Where the column is heading. Input moves this; [progress] chases it. */
    private var goal = 0f

    /** Smoothed rotation rate, feeding the trailing deformation. */
    internal var bend by mutableFloatStateOf(0f)
        private set

    /** Index of the card currently facing the camera. */
    var active by mutableIntStateOf(0)
        internal set

    /**
     * Bumped once per frame. The draw pass reads it so the column keeps
     * repainting while the arrival, the focus or the snap are still settling,
     * none of which show up in [progress] alone.
     */
    internal var tick by mutableIntStateOf(0)
        private set

    /** 0 = in focus, 1 = fully attenuated. */
    internal val dim = FloatArray(count)

    /** The slot that faces the camera. Everything is measured from here. */
    internal val front: Int = floor(count * 0.5f).toInt()

    internal var nowMs = 0f
        private set

    private var lastInputMs = 0f
    private var snapped = true
    private var tween: Tween? = null
    private var dragVelocity = 0f

    private var entryStartMs = -1f
    private var entryPlaying = playEntry

    private class Tween(val from: Float, val to: Float, val atMs: Float)

    // --- input ---------------------------------------------------------------

    internal fun onDrag(deltaPx: Float) {
        tween = null
        goal += deltaPx * DRAG
        dragVelocity = deltaPx * DRAG
        lastInputMs = nowMs
        snapped = false
    }

    internal fun onDragEnd() {
        goal += dragVelocity * FLING
        dragVelocity = 0f
        lastInputMs = nowMs
        snapped = false
    }

    internal fun onWheel(deltaPx: Float) {
        tween = null
        goal += deltaPx * WHEEL
        lastInputMs = nowMs
        snapped = false
    }

    /** Advance to the facing slot along whichever direction is shorter. */
    internal fun bringToFront(index: Int) {
        val want = (((index - front) % count) + count) % count
        val to = want + ((goal - want) / count).roundToInt() * count
        tween = Tween(goal, to.toFloat(), nowMs)
        lastInputMs = nowMs
        snapped = true
    }

    /** Step [by] cards, for keyboard and for programmatic moves. */
    fun step(by: Int) {
        tween = Tween(goal, goal.roundToInt() + by.toFloat(), nowMs)
        lastInputMs = nowMs
        snapped = true
    }

    // --- frame ---------------------------------------------------------------

    internal fun advance(nowMs: Float, deltaMs: Float) {
        this.nowMs = nowMs
        tick++

        if (entryPlaying) {
            if (entryStartMs < 0f) entryStartMs = nowMs
            val elapsed = nowMs - entryStartMs
            val spin = (elapsed / ENTRY_SPIN_MS).clamped(0f, 1f)
            progress = goal - ENTRY_SPIN * (1f - inOutCubic(spin))
            if (spin >= 1f) entryPlaying = false
            syncActive()
            settleFocus(deltaMs)
            return
        }

        val running = tween
        if (running != null) {
            val t = ((nowMs - running.atMs) / TAP_MS).clamped(0f, 1f)
            goal = running.from + (running.to - running.from) * inOutCubic(t)
            if (t >= 1f) tween = null
        } else if (!snapped && nowMs - lastInputMs > SNAP_IDLE_MS) {
            goal = goal.roundToInt().toFloat()
            snapped = true
        }

        val before = progress
        progress += (goal - progress) *
            frameRate(if (snapped) SNAP_EASE else EASE, deltaMs)

        val velocity = (progress - before).clamped(-BEND_MAX, BEND_MAX)
        bend += (velocity - bend) * frameRate(BEND_EASE, deltaMs)

        syncActive()
        settleFocus(deltaMs)
    }

    private fun syncActive() {
        val near = (((goal.roundToInt() + front) % count) + count) % count
        if (active != near) active = near
    }

    /**
     * Attenuation for cards away from the front. Taken the short way round the
     * loop, so the far side of the spiral is dim however the column got there.
     */
    private fun settleFocus(deltaMs: Float) {
        val focused = active
        val rate = frameRate(0.09f, deltaMs)
        for (i in 0 until count) {
            val gap = abs(wrapDelta(i.toFloat(), focused.toFloat(), count))
            val want = (gap / FOCUS_FALLOFF).clamped(0f, 1f)
            dim[i] += (want - dim[i]) * rate
        }
    }
}
