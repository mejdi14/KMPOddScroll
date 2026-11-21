package io.github.mejdi14.oddscroll.edgedistortion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.pow

/** Share of the remaining distance an animated scroll closes per 60Hz frame. */
private const val GLIDE_EASE = 0.12f

/**
 * Where the viewport sits over the lattice, and how it is still moving.
 *
 * The grid is infinite, so there is nothing to scroll *within* — the camera is
 * the whole of the state, and it is unbounded on both axes. Positions are in
 * undistorted scene pixels with y running down.
 *
 * A drag moves the camera and then stops dead; the idle drift takes straight
 * back over. There is deliberately no throw. The drift is always running
 * underneath, so a throw decaying into it would read as the gallery refusing to
 * settle rather than as momentum.
 */
class EdgeDistortionScrollState internal constructor(
    drift: Offset,
    initialOffset: Offset,
) {
    /** Idle movement per second, as a share of the viewport's height. */
    internal var drift: Offset = drift

    internal var cameraX by mutableFloatStateOf(initialOffset.x)
        private set

    internal var cameraY by mutableFloatStateOf(initialOffset.y)
        private set

    /**
     * Where the camera is, in lattice pixels. There is no current item on an
     * endless 2D lattice, so there is no `currentIndex`; this is the position
     * to read and to hand back to [scrollTo]. It changes every frame while the
     * lattice drifts, so read it in a draw or layout block rather than in
     * composition.
     */
    val offset: Offset get() = Offset(cameraX, cameraY)

    /** True while a drag is held or an [animateScrollTo] is still travelling. */
    var isScrollInProgress by mutableStateOf(false)
        private set

    private var glideTo: Offset? = null

    /**
     * Bumped once per frame. The draw pass reads it so the grid keeps
     * repainting while it drifts, which the camera alone would not force when
     * the drift is slow enough to round to nothing between frames.
     */
    internal var tick by mutableIntStateOf(0)
        private set

    /**
     * Last viewport height the draw pass saw. The drift is a share of it, and
     * the frame loop advances the camera without a layout to ask, so the draw
     * pass leaves it here.
     */
    internal var lastViewportHeight: Float = 0f

    private var dragging = false

    // --- input ---------------------------------------------------------------

    internal fun onDragStart() {
        dragging = true
        glideTo = null
        isScrollInProgress = true
    }

    /**
     * [dx] and [dy] are pointer movement in pixels. The camera moves against
     * them, so the lattice follows the finger.
     */
    internal fun onDrag(dx: Float, dy: Float, panSpeed: Float) {
        cameraX -= dx * panSpeed
        cameraY -= dy * panSpeed
    }

    internal fun onDragEnd() {
        dragging = false
        isScrollInProgress = glideTo != null
    }

    /** Moves the camera by [delta] lattice pixels with no animation. */
    fun scrollBy(delta: Offset) {
        cameraX += delta.x
        cameraY += delta.y
    }

    /** Puts the camera at [offset], in lattice pixels, with no animation. */
    fun scrollTo(offset: Offset) {
        glideTo = null
        cameraX = offset.x
        cameraY = offset.y
        isScrollInProgress = dragging
    }

    /**
     * Eases the camera to [offset], in lattice pixels. The idle drift waits
     * until it arrives, and a drag cancels it.
     */
    fun animateScrollTo(offset: Offset) {
        glideTo = offset
        isScrollInProgress = true
    }

    // --- frame ---------------------------------------------------------------

    internal fun advance(deltaMs: Float, viewportHeight: Float) {
        tick++
        val goal = glideTo
        if (goal != null && !dragging) {
            val ease = 1f - (1f - GLIDE_EASE).pow(deltaMs / 16.67f)
            cameraX += (goal.x - cameraX) * ease
            cameraY += (goal.y - cameraY) * ease
            if (abs(goal.x - cameraX) < 0.5f && abs(goal.y - cameraY) < 0.5f) {
                cameraX = goal.x
                cameraY = goal.y
                glideTo = null
                isScrollInProgress = false
            }
            return
        }
        if (dragging || viewportHeight <= 0f) return

        // The drift is given as a share of the viewport per second so it reads
        // the same on a phone as in a desktop window, where a fixed pixel rate
        // would crawl on one and race on the other.
        val seconds = deltaMs / 1000f
        cameraX += drift.x * viewportHeight * seconds
        cameraY += drift.y * viewportHeight * seconds
    }
}
