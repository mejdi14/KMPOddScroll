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
private const val LOOK_AFTER_MS = 2000f

/** Close enough to the middle to call it there, in card heights — under a pixel. */
private const val CENTRED = 0.01f

/** Walking up to a painting, and back. */
private const val WALK_IN_MS = 2000f
private const val WALK_OUT_MS = 1400f

/** Walking into the room when it is first shown. */
private const val ENTRY_MS = 2400f

/** Inspecting: the frame's four sticks part and fly off, then the glass slides up and away. */
private const val FRAME_OFF_MS = 1000f
private const val GLASS_OFF_MS = 800f

/** Stepping away, the glass and the frame go back on this much faster. */
private const val BACK_ON = 2f

/**
 * Where the hang has slid to along the wall, in card heights.
 *
 * The whole gallery is one number: each painting hangs a fixed way along the
 * wall from the one before, so where the hang has slid to says where every
 * painting is. Input moves where it is going; the number springs after it.
 * When the input stops, where it is going is rounded to a painting, so the
 * hang always comes to rest with one in the middle.
 */
class GalleryWallScrollState internal constructor(walkIn: Boolean = true) {

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
     * The hang has stood still for two seconds with a painting in the
     * middle, so [look] will walk up to it.
     */
    var canLook by mutableStateOf(false)
        private set

    /** Walking up to, or standing in front of, a painting; [leave] steps back. */
    var looking by mutableStateOf(false)
        private set

    /** Standing in front of the painting walked up to. */
    var arrived by mutableStateOf(false)
        private set

    /** Its frame and glass taken off by [inspect]; they go back on when stepped away from. */
    var inspected by mutableStateOf(false)
        private set

    /** Up close with the frame still on, so [inspect] will take it and the glass off. */
    val canInspect: Boolean get() = arrived && !inspected

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
    private var visited = 0

    /** The walk up to it, 0 at the hang and 1 in front of it, linear in time. */
    internal var walk = 0f
        private set

    /** How far the frame has come off the painting walked up to, 0 to 1, linear in time. */
    internal var apart = 0f
        private set

    /** How far its glass has slid away once the frame is off, 0 to 1, linear in time. */
    internal var lift = 0f
        private set

    /** The walk into the room, 0 in the doorway and 1 where the hang is seen from, linear in time. */
    internal var entry = if (walkIn) 0f else 1f
        private set

    /** The painting the walk is towards, or would be: the one in the middle until it starts. */
    internal val subject: Int get() = if (looking || walk > 0f) visited else centred

    /** Slides painting [index] to the middle. Unwrapped, so it can go either way round. */
    fun scrollTo(index: Int) {
        val hang = hang ?: return
        if (!input()) return
        target = hang.centre(index)
    }

    /** Walks up to the painting in the middle, once [canLook]. */
    fun look() {
        if (!canLook) return
        visited = centred
        looking = true
        canLook = false
    }

    /** Takes the frame off the painting walked up to, then its glass, once [canInspect]. */
    fun inspect() {
        if (!canInspect) return
        inspected = true
    }

    /** Steps back from the painting to the hang, putting its frame and glass back on. */
    fun leave() {
        looking = false
        inspected = false
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
        if (looking || walk > 0f || entry < 1f) return false
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

        if (entry < 1f) {
            entry = min(1f, entry + deltaMs / ENTRY_MS)
            // The wait for the button starts once in.
            idleMs = 0f
            moved = true
        }

        val goal = if (looking) 1f else 0f
        if (walk != goal) {
            walk = if (looking) min(1f, walk + deltaMs / WALK_IN_MS) else max(0f, walk - deltaMs / WALK_OUT_MS)
            moved = true
        }
        val there = looking && walk >= 1f
        if (there != arrived) arrived = there

        // Off the frame first and then the glass, and back on the other way round.
        if (inspected) {
            if (apart < 1f) {
                apart = min(1f, apart + deltaMs / FRAME_OFF_MS)
                moved = true
            } else if (lift < 1f) {
                lift = min(1f, lift + deltaMs / GLASS_OFF_MS)
                moved = true
            }
        } else if (lift > 0f) {
            lift = max(0f, lift - deltaMs * BACK_ON / GLASS_OFF_MS)
            moved = true
        } else if (apart > 0f) {
            apart = max(0f, apart - deltaMs * BACK_ON / FRAME_OFF_MS)
            moved = true
        }

        if (moved) tick++
        val hang = hang ?: return
        val nearest = hang.nearest(position)
        if (nearest != centred) centred = nearest

        val ready = !looking && walk == 0f && apart == 0f && settled && idleMs >= LOOK_AFTER_MS &&
            abs(position - hang.centre(nearest)) < CENTRED
        if (ready != canLook) canLook = ready
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
