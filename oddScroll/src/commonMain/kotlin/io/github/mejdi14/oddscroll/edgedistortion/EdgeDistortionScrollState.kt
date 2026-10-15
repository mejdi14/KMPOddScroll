package io.github.mejdi14.oddscroll.edgedistortion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

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
    internal val drift: Offset,
) {
    internal var cameraX by mutableFloatStateOf(0f)
        private set

    internal var cameraY by mutableFloatStateOf(0f)
        private set

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
    }

    /** Jump the viewport, for the wheel and for programmatic moves. */
    fun offsetBy(dx: Float, dy: Float) {
        cameraX += dx
        cameraY += dy
    }

    // --- frame ---------------------------------------------------------------

    internal fun advance(deltaMs: Float, viewportHeight: Float) {
        tick++
        if (dragging || viewportHeight <= 0f) return

        // The drift is given as a share of the viewport per second so it reads
        // the same on a phone as in a desktop window, where a fixed pixel rate
        // would crawl on one and race on the other.
        val seconds = deltaMs / 1000f
        cameraX += drift.x * viewportHeight * seconds
        cameraY += drift.y * viewportHeight * seconds
    }
}
