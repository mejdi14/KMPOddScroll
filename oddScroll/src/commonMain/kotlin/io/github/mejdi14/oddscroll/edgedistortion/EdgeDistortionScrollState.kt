package io.github.mejdi14.oddscroll.edgedistortion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import io.github.mejdi14.oddscroll.internal.FRAME_MS
import io.github.mejdi14.oddscroll.internal.lerp
import kotlin.math.pow

/** Per-frame survival of the throw. Lower stops sooner. */
private const val DECAY = 0.94f

/** How fast the measured drag speed is allowed to follow the pointer. */
private const val VELOCITY_TRACK = 0.4f

/** Under this the throw is spent and the idle drift takes back over. */
private const val REST = 0.02f

/**
 * Where the viewport sits over the lattice, and how it is still moving.
 *
 * The grid is infinite, so there is nothing to scroll *within* — the camera is
 * the whole of the state, and it is unbounded on both axes. Positions are in
 * undistorted scene pixels with y running down, the same units the viewport is
 * measured in, which is what lets a drag track the finger one for one.
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
     * Last viewport height the draw pass saw. The drift is expressed as a share
     * of it, and the frame loop advances the camera without having a layout to
     * ask, so the draw pass leaves it here.
     */
    internal var lastViewportHeight: Float = 0f

    private var velocityX = 0f
    private var velocityY = 0f
    private var dragging = false

    // --- input ---------------------------------------------------------------

    internal fun onDragStart() {
        dragging = true
        velocityX = 0f
        velocityY = 0f
    }

    /**
     * [dx] and [dy] are pointer movement in pixels. The camera moves against
     * them, so the card under the finger stays under the finger.
     */
    internal fun onDrag(dx: Float, dy: Float) {
        cameraX -= dx
        cameraY -= dy
        velocityX = lerp(velocityX, -dx, VELOCITY_TRACK)
        velocityY = lerp(velocityY, -dy, VELOCITY_TRACK)
    }

    internal fun onDragEnd() {
        dragging = false
    }

    /** Jump the viewport, for programmatic moves. */
    fun offsetBy(dx: Float, dy: Float) {
        cameraX += dx
        cameraY += dy
    }

    // --- frame ---------------------------------------------------------------

    internal fun advance(deltaMs: Float, viewportHeight: Float) {
        tick++
        if (dragging) return

        val steps = deltaMs / FRAME_MS
        val decay = DECAY.pow(steps)
        velocityX *= decay
        velocityY *= decay
        if (velocityX * velocityX + velocityY * velocityY < REST * REST) {
            velocityX = 0f
            velocityY = 0f
        }

        // The drift is given as a share of the viewport per second so it reads
        // the same on a phone as on a desktop window, where a fixed pixel rate
        // would crawl on one and race on the other.
        val seconds = deltaMs / 1000f
        cameraX += velocityX * steps + drift.x * viewportHeight * seconds
        cameraY += velocityY * steps + drift.y * viewportHeight * seconds
    }
}
