package io.github.mejdi14.oddscroll.gallerywall

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/*
 * The timing was fitted to the hang's position, recovered frame by frame from
 * a recording of the reference: every painting, in every frame, sits where one
 * number says, to 1.8 px. The reference is driven by a trackpad, and what it
 * does after each swipe ends is a critically damped spring — pooled over all
 * seven let-gos, ζ = 1.02 ± 0.01 and ωn = 7.0 ± 0.2 a second, to 1.3 px; held
 * at ζ = 1 it is ωn = 6.74 to the same 1.3 px, which is the default
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

/**
 * Where the hang has slid to along the wall, in card heights, and which
 * painting is in the middle of the back wall.
 *
 * The whole gallery is one number: each painting hangs a fixed way along the
 * wall from the one before, so where the hang has slid to says where every
 * painting is. Input moves where it is going; the number springs after it.
 * When the input stops, where it is going is rounded to a painting, so the
 * hang always comes to rest with one in the middle.
 *
 * [scrollTo], [animateScrollTo] and [step] work whether or not the user can
 * scroll it; the [GalleryWallScroll] showing it runs the motion.
 */
class GalleryWallScrollState internal constructor(initialIndex: Int) {

    /** Unbounded; the hang repeats, so it is only wrapped when drawn. */
    internal var position = 0f
        private set

    private var target = 0f
    private var velocity = 0f

    /** ωn of the spring the hang follows its target on, a second; set from the stiffness it is shown with. */
    internal var omega = OMEGA

    /** Bumped on each frame that moves anything, so the wall repaints. */
    internal var tick by mutableIntStateOf(0)
        private set

    /** The painting nearest the middle of the back wall, unwrapped. */
    private var centred = 0

    /**
     * The painting nearest the middle of the back wall, as an index into the
     * items, `0 until items.size`. Changes as a painting passes the middle,
     * not every frame.
     */
    var currentIndex by mutableIntStateOf(initialIndex)
        private set

    /** The hang is being dragged, or is still sliding to where it was sent. */
    var isScrollInProgress by mutableStateOf(false)
        private set

    private var hang: Hang? = null

    /** A jump asked for before there was a hang to make it on, as an item index. */
    private var pending: Int? = initialIndex

    /** A finger is down, so the input has not stopped however still it is. */
    private var held = false

    /** Since the last input moved the hang. */
    private var idleMs = 0f

    /** Where the hang was going when this run of input began. */
    private var anchor = 0f

    /** The target is a painting's middle, with no input since. */
    private var settled = true

    /** Jumps painting [index] (of the items) to the middle, with no motion. */
    fun scrollTo(index: Int) {
        val hang = hang
        if (hang == null) {
            pending = index
            return
        }
        target = hang.centre(nearestCopy(hang, index, position))
        position = target
        velocity = 0f
        settled = true
        tick++
        update(hang)
    }

    /** Slides painting [index] (of the items) to the middle, the short way round, on the hang's own spring. */
    fun animateScrollTo(index: Int) {
        val hang = hang
        if (hang == null) {
            pending = index
            return
        }
        travel(nearestCopy(hang, index, target))
    }

    /**
     * Slides [by] paintings along from the one the hang is going to: positive
     * brings the next ones in from the right-hand wall, negative the ones before.
     */
    fun step(by: Int) {
        val hang = hang ?: return
        travel(hang.nearest(target) + by)
    }

    /** Sends the hang to painting [index], unwrapped. */
    internal fun travel(index: Int) {
        val hang = hang ?: return
        target = hang.centre(index)
        settled = true
        idleMs = 0f
    }

    /** The copy of item [index], round the repeating hang, whose middle is nearest [from]. */
    private fun nearestCopy(hang: Hang, index: Int, from: Float): Int {
        val wrapped = hang.wrap(index)
        val laps = ((from - hang.centre(wrapped)) / hang.lap).roundToInt()
        return laps * hang.count + wrapped
    }

    /** Shown with [hang]; a new one, from changed items or spacing, keeps the same painting in the middle. */
    internal fun attach(hang: Hang) {
        val previous = this.hang
        if (previous === hang) return
        this.hang = hang
        val jump = pending
        pending = null
        when {
            jump != null -> scrollTo(jump)
            previous != null -> travel(nearestCopy(hang, previous.wrap(centred), target))
        }
    }

    /** [by] is in card heights, along the wall. */
    internal fun nudge(by: Float) {
        if (settled) {
            anchor = target
            settled = false
        }
        idleMs = 0f
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
        if (spring(deltaMs)) tick++
        hang?.let(::update)
    }

    private fun update(hang: Hang) {
        val nearest = hang.nearest(position)
        if (nearest != centred) centred = nearest
        val index = hang.wrap(nearest)
        if (index != currentIndex) currentIndex = index
        val moving = !settled || position != target
        if (moving != isScrollInProgress) isScrollInProgress = moving
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
            val c = velocity + omega * e
            val decay = exp(-omega * dt)
            position = target + (e + c * dt) * decay
            velocity = (velocity - omega * c * dt) * decay
        }
        return true
    }
}
