package io.github.mejdi14.oddscroll.gallerywall

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.exp

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
 * Where the hang has slid to along the wall, in card heights.
 *
 * The whole gallery is one number: each painting hangs a fixed way along the
 * wall from the one before, so where the hang has slid to says where every
 * painting is. Input moves where it is going; the number springs after it.
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

    internal var hang: Hang? = null

    /** Slides painting [index] to the middle. Unwrapped, so it can go either way round. */
    fun scrollTo(index: Int) {
        val hang = hang ?: return
        target = hang.centre(index)
    }

    /** [by] is in card heights, along the wall. */
    internal fun nudge(by: Float) {
        target += by
    }

    internal fun advance(deltaMs: Float) {
        val e = position - target
        if (abs(e) < REST && abs(velocity) < REST_SPEED) {
            if (position == target && velocity == 0f) return
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
        tick++
        val nearest = hang?.nearest(position) ?: 0
        if (nearest != centred) centred = nearest
    }
}
