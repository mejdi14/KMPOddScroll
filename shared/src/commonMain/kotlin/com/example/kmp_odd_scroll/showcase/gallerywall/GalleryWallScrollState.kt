package com.example.kmp_odd_scroll.showcase.gallerywall

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.kmp_odd_scroll.showcase.internal.clamped
import com.example.kmp_odd_scroll.showcase.internal.frameRate
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

private const val PI_F = kotlin.math.PI.toFloat()

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

/** Turning the bare painting over like a sheet of paper, one way or the other. */
internal const val FLIP_MS = 1800f

/** Stepping into the painting, and back out of it. */
private const val ENTRY_WAVE_MS = 640f
private const val ENTRY_WAVES = 2f
private const val STEP_IN_MS = 2600f
private const val STEP_OUT_MS = 1600f

/** A painting rebuilt as a place: the walk in along its route, and how much faster it walks back out. */
private const val ROUTE_MS = 2800f
private const val ROUTE_BACK = 3f

/** How far stepped in before the walk in starts, once the room round the frame has gone dark. */
private const val ROUTE_FROM = 0.4f

/** The head easing after a drag inside the painting, per frame at 60Hz. */
private const val LOOK_EASE = 0.12f

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

    /** The wall is centred and idle during the pause before the Look button appears. */
    internal var resting by mutableStateOf(false)
        private set

    /** Walking up to, or standing in front of, a painting; [leave] steps back. */
    var looking by mutableStateOf(false)
        private set

    /** Standing in front of the painting walked up to. */
    var arrived by mutableStateOf(false)
        private set

    /** Its frame and glass taken off by [inspect]; they go back on with [uninspect] or [leave]. */
    var inspected by mutableStateOf(false)
        private set

    /** Up close with the frame still on, so [inspect] will take it and the glass off. */
    val canInspect by derivedStateOf {
        looking && arrived && !inspected && !stepping && apart == 0f
    }

    /** The frame and glass are off and out of sight, so [flip] will turn the painting over, or back. */
    var canFlip by mutableStateOf(false)
        private set

    internal val hasFlipAction: Boolean get() = supportsHardwareMeshes && hang?.framed(visited) == true

    /** The inspection stage stays open while the bare painting turns over. */
    internal var studyVisible by mutableStateOf(false)
        private set

    /** The controls share the wall while the glass lifts, and give way to the interior camera. */
    internal val showingInspection by derivedStateOf {
        arrived && inspected && !stepping && (!inside || entryWave < ENTRY_WAVES)
    }

    /** The unframed front is stationary and ready for the inspection tools on every renderer. */
    internal val canStudy: Boolean get() = studyVisible && !inside && !flipping && !over

    /** The inspected painting is lying face up and still, so [stepInside] will go into it. */
    var canStepInside by mutableStateOf(false)
        private set

    /** Inside the painting walked up to, or on the way in; [leave] steps back out of it. */
    var inside by mutableStateOf(false)
        private set

    /** In the painting at all, or on the way in or out of it. */
    private var stepping by mutableStateOf(false)

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
    internal var apart by mutableFloatStateOf(0f)
        private set

    /** How far its glass has slid away once the frame is off, 0 to 1, linear in time. */
    internal var lift by mutableFloatStateOf(0f)
        private set

    /** How far the bare painting has turned over, 0 to 1, linear in time; 0 lying still. */
    internal var fold by mutableFloatStateOf(0f)
        private set

    private var flipping by mutableStateOf(false)

    /** The bare painting lies turned over, its back showing, until it is flipped again or stepped away from. */
    internal var over by mutableStateOf(false)
        private set

    /** Consecutive blue (0 to 1) and purple (1 to 2) pulses before the camera moves. */
    internal var entryWave by mutableFloatStateOf(0f)
        private set

    /** How far into the painting, 0 in front of it and 1 inside, linear in time. */
    internal var stepIn = 0f
        private set

    /**
     * Inside, the head turned across and up or down, in radians, easing after
     * where the drags have put it; how far walked on, in the painting's
     * distance; and how long since stepping in, in seconds.
     */
    internal var yaw = 0f
        private set
    internal var pitch = 0f
        private set
    internal var wander = 0f
        private set
    internal var insideTime = 0f
        private set

    private val look = FloatArray(2)
    private var wanderTo = 0f

    /**
     * The painting walked up to is rebuilt as a place, [GalleryWallItem.scene]:
     * stepping in walks its route, a drag turns within its bounds and a scroll
     * walks on or back along it. Set as it is drawn.
     */
    internal var scene: GalleryWallScene? = null

    /** How far along the scene's route, 0 at the painting and 1 at its end, linear in time; and where it is going. */
    internal var route = 0f
        private set
    private var routeTo = 0f
    private var walkPending = false

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

    /** Restores the glass and frame while staying in front of the painting. */
    fun uninspect() {
        if (!inspected || inside || stepping) return
        endInspection()
    }

    /** Turns the bare painting over like a sheet of paper to its back, or back again, once [canFlip]. */
    fun flip() {
        if (!canFlip) return
        flipping = true
        canFlip = false
        canStepInside = false
    }

    /**
     * Goes into the painting walked up to, once [canStepInside]: it takes on
     * depth, the room fades to dark round it and the eye goes in, where a drag
     * looks round and a scroll walks in or back.
     */
    fun stepInside() {
        if (!canStepInside) return
        inside = true
        canFlip = false
        canStepInside = false
        walkPending = scene != null
    }

    /**
     * Steps back out of the painting if inside it; otherwise steps back from
     * it to the hang, putting its frame and glass back on.
     */
    fun leave() {
        if (inside) {
            inside = false
            entryWave = 0f
            look[0] = 0f
            look[1] = 0f
            wanderTo = 0f
            routeTo = 0f
            walkPending = false
            return
        }
        looking = false
        endInspection()
    }

    private fun endInspection() {
        inspected = false
        canFlip = false
        studyVisible = false
        canStepInside = false
        flipping = false
        fold = 0f
        over = false
    }

    /** [by] is in card heights, along the wall. */
    internal fun nudge(by: Float) {
        if (!input()) return
        target += by
    }

    /** A drag of [dx], [dy] pixels inside the painting, seen through a focal length of [focal]. */
    internal fun lookAround(dx: Float, dy: Float, focal: Float) {
        if (!inside || stepIn == 0f) return
        val scene = scene
        if (scene == null) {
            lookBy(dx, dy, focal, look[0], look[1], look)
        } else {
            // Into the scene: a drag right turns right, towards the cliff, and a drag up looks up.
            if (scene.yawMax >= PI_F) {
                // Built all the way round, it turns on for ever, kept within half a turn of ahead so
                // stepping out turns back the short way.
                look[0] += dx / focal
                if (look[0] > PI_F) { look[0] -= 2f * PI_F; yaw -= 2f * PI_F }
                if (look[0] < -PI_F) { look[0] += 2f * PI_F; yaw += 2f * PI_F }
            } else {
                look[0] = (look[0] + dx / focal).clamped(-scene.yawMax, scene.yawMax)
            }
            look[1] = (look[1] - dy / focal).clamped(-scene.pitchDown, scene.pitchUp)
        }
    }

    /** A scroll of [by] inside the painting walks in, or back. */
    internal fun walkInside(by: Float) {
        if (!inside || stepIn == 0f) return
        if (scene != null) {
            routeTo = (routeTo - by * 0.04f).clamped(0f, 1f)
            walkPending = false
        } else {
            wanderTo = wanderBy(by, wanderTo)
        }
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

        if (flipping) {
            fold += deltaMs / FLIP_MS
            if (fold >= 1f) {
                // Turned all the way over, it lies the other way up.
                fold = 0f
                over = !over
                flipping = false
            }
        }

        // Into the painting and back out, the head easing after the drags.
        val deep = when {
            inside && entryWave < ENTRY_WAVES -> {
                entryWave = min(ENTRY_WAVES, entryWave + deltaMs / ENTRY_WAVE_MS)
                0f
            }
            inside -> min(1f, stepIn + deltaMs / STEP_IN_MS)
            else -> max(0f, stepIn - deltaMs / STEP_OUT_MS)
        }
        if (deep > 0f || stepIn > 0f) {
            stepIn = deep
            val k = frameRate(LOOK_EASE, deltaMs)
            yaw += (look[0] - yaw) * k
            pitch += (look[1] - pitch) * k
            wander += (wanderTo - wander) * k
            insideTime = if (deep > 0f) insideTime + deltaMs / 1000f else 0f
            if (walkPending && inside && stepIn >= ROUTE_FROM) {
                routeTo = 1f
                walkPending = false
            }
            val step = deltaMs / ROUTE_MS * if (inside) 1f else ROUTE_BACK
            route = if (deep == 0f) 0f else if (route < routeTo) min(routeTo, route + step) else max(routeTo, route - step)
            // Inside, the head never quite stops drifting.
            moved = true
        }
        if ((stepIn > 0f) != stepping) stepping = stepIn > 0f

        if (moved) tick++
        val hang = hang ?: return
        studyVisible = showingInspection && lift >= 1f
        canFlip = studyVisible && !inside && !flipping && hasFlipAction
        canStepInside = canStudy && supportsHardwareMeshes
        val nearest = hang.nearest(position)
        if (nearest != centred) centred = nearest

        resting = entry == 1f && !looking && walk == 0f && apart == 0f && settled && !held &&
            abs(position - hang.centre(nearest)) < CENTRED
        canLook = resting && idleMs >= LOOK_AFTER_MS
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
