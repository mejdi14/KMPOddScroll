package io.github.mejdi14.oddscroll.gallerywall

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/*
 * The timing was fitted to the hang's position, recovered frame by frame from
 * a recording of the reference: every painting, in every frame, sits where one
 * number says, to 1.8 px. The reference is driven by a trackpad, and what it
 * does after each swipe ends is a critically damped spring — pooled over all
 * seven let-gos, ζ = 1.02 ± 0.01 and ωn = 7.0 ± 0.2 a second, to 1.3 px; held
 * at ζ = 1 it is ωn = 6.74 to the same 1.3 px, which is what this uses
 * (Compose's `spring(dampingRatio = 1f, stiffness = 45.5f)`, just under
 * `Spring.StiffnessVeryLow`).
 *
 * It carries on past the let-go by no more than a spring trailing a moving
 * target would — 0.21 to 0.30 s of the parting speed against the 2/ωn = 0.29 s
 * such a lag comes to — so there is no throw on top: the input moves a target,
 * the hang springs after it, and the coast is the hang catching up.
 */
private const val OMEGA = 6.744f

/** Below these, in card heights and card heights a second, it has arrived. */
private const val REST = 1e-4f
private const val REST_SPEED = 1e-3f

/**
 * A wheel or trackpad has no let-go, so a pause this long stands in for one.
 * Trackpad momentum arrives every frame; a wheel's notches come closer than
 * this while it is being spun.
 */
private const val SETTLE_MS = 150f

/**
 * Moved this far along the wall, in card heights, and the hang goes on to the
 * next painting rather than back to the one it left — so a single notch of
 * the wheel, or a short flick, still turns the page.
 */
private const val FLICK = 0.2f

/** Still this long, with a painting in the middle, before it can be walked up to. */
private const val INSPECT_AFTER_MS = 1000f

/** Close enough to the middle to call it there, in card heights — under a pixel. */
private const val CENTRED = 0.01f

/** Walking up to a painting, and back. */
private const val WALK_IN_MS = 2000f
private const val WALK_OUT_MS = 1400f

/**
 * Where the hang has slid to along the wall, in card heights.
 *
 * The whole gallery is one number: each painting hangs a fixed way along the
 * wall from the one before, so where the hang has slid to says where every
 * painting is. Input moves where it is going; the number springs after it.
 * When the input stops, where it is going is rounded to a painting, so the
 * hang always comes to rest with one in the middle.
 */
class GalleryWallScrollState internal constructor() {

    /** Unbounded; the hang repeats, so it is only wrapped when drawn. */
    internal var position = 0f
        private set

    private var target = 0f
    private var velocity = 0f

    /** Bumped on each frame that moves anything, so the wall repaints. */
    internal var tick by mutableIntStateOf(0)
        private set

    /** The painting nearest the middle of the back wall, unwrapped. */
    var centred by mutableIntStateOf(0)
        private set

    /**
     * The hang has stood still for a second with a painting in the middle,
     * so [inspect] will walk up to it.
     */
    var canInspect by mutableStateOf(false)
        private set

    /** Walking up to, or standing in front of, a painting; [leave] steps back. */
    var inspecting by mutableStateOf(false)
        private set

    internal var hang: Hang? = null

    /** A finger is down, so the input has not stopped however still it is. */
    private var held = false

    /** Since the last input moved the hang. */
    private var idleMs = 0f

    /** Where the hang was going when this run of input began. */
    private var anchor = 0f

    /** The target is a painting's middle, with no input since. */
    private var settled = true

    /** The painting walked up to, unwrapped. */
    private var inspected = 0

    /** The walk up to it, 0 at the hang and 1 in front of it, linear in time. */
    internal var walk = 0f
        private set

    /** The painting the walk is towards, or would be: the one in the middle until it starts. */
    internal val subject: Int get() = if (inspecting || walk > 0f) inspected else centred

    /** Slides painting [index] to the middle. Unwrapped, so it can go either way round. */
    fun scrollTo(index: Int) {
        val hang = hang ?: return
        if (!input()) return
        target = hang.centre(index)
    }

    /** Walks up to the painting in the middle, once [canInspect]. */
    fun inspect() {
        if (!canInspect) return
        inspected = centred
        inspecting = true
        canInspect = false
    }

    /** Steps back from the painting to the hang. */
    fun leave() {
        inspecting = false
    }

    /** [by] is in card heights, along the wall. */
    internal fun nudge(by: Float) {
        if (!input()) return
        target += by
    }

    internal fun press() {
        held = true
    }

    /** The finger is up; [moved] if it dragged rather than tapped. */
    internal fun release(moved: Boolean) {
        held = false
        if (moved && !settled) {
            idleMs = 0f
            settle()
        }
    }

    /** Input is about to move the target; false while away from the hang. */
    private fun input(): Boolean {
        if (inspecting || walk > 0f) return false
        if (settled) {
            anchor = target
            settled = false
        }
        idleMs = 0f
        return true
    }

    /** Rounds where the hang is going to a painting. */
    private fun settle() {
        val hang = hang ?: return
        settled = true
        val from = hang.nearest(anchor)
        val moved = target - anchor
        var to = hang.nearest(target)
        if (to == from && abs(moved) > FLICK) to += sign(moved).toInt()
        target = hang.centre(to)
    }

    internal fun advance(deltaMs: Float) {
        idleMs = min(idleMs + deltaMs, 1e6f)
        if (!held && !settled && idleMs >= SETTLE_MS) settle()

        var moved = spring(deltaMs)

        val goal = if (inspecting) 1f else 0f
        if (walk != goal) {
            walk = if (inspecting) min(1f, walk + deltaMs / WALK_IN_MS) else max(0f, walk - deltaMs / WALK_OUT_MS)
            moved = true
        }

        if (moved) tick++
        val hang = hang ?: return
        val nearest = hang.nearest(position)
        if (nearest != centred) centred = nearest

        val ready = !inspecting && walk == 0f && settled && idleMs >= INSPECT_AFTER_MS &&
            abs(position - hang.centre(nearest)) < CENTRED
        if (ready != canInspect) canInspect = ready
    }

    /** One frame of the hang springing after its target; false if it was already there. */
    private fun spring(deltaMs: Float): Boolean {
        val e = position - target
        if (abs(e) < REST && abs(velocity) < REST_SPEED) {
            if (position == target && velocity == 0f) return false
            position = target
            velocity = 0f
        } else {
            // The spring's own solution over the frame, so it lands the same
            // at any frame rate.
            val dt = deltaMs / 1000f
            val c = velocity + OMEGA * e
            val decay = exp(-OMEGA * dt)
            position = target + (e + c * dt) * decay
            velocity = (velocity - OMEGA * c * dt) * decay
        }
        return true
    }
}
