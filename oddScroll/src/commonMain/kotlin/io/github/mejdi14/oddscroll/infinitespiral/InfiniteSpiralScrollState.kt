package io.github.mejdi14.oddscroll.infinitespiral

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How fast the drift gets up to speed, and how fast it gives way to a hand. */
private const val SPEED_BLEND = 7f

/** How closely the spiral follows its target — tighter while being dragged. */
private const val FOLLOW = 11f
private const val FOLLOW_DRAGGING = 22f

/**
 * Where the spiral has turned to.
 *
 * Two values rather than one: a target that input moves directly, and a
 * position that chases it. A drag can then be taken at its own speed while the
 * cards still arrive smoothly, and the drift is simply a target that keeps
 * moving on its own.
 *
 * Position is unbounded and wrapped only when a card is placed, so the spiral
 * has no end to reach and nothing to reset.
 */
class InfiniteSpiralScrollState internal constructor() {
    internal var progress = 0f
        private set

    /** Card currently nearest the middle. */
    var active by mutableIntStateOf(0)
        internal set

    /** Bumped once per frame, so the spiral repaints while it turns. */
    internal var tick by mutableIntStateOf(0)
        private set

    internal var dragging = false
        private set

    private var target = 0f
    private var autoSpeed = 0f

    // --- input ---------------------------------------------------------------

    internal fun onDragStart() {
        dragging = true
        // The target is pulled back to where the spiral actually is, so a drag
        // starts from what is on screen rather than from wherever the drift had
        // already aimed.
        target = progress
    }

    internal fun onDrag(dy: Float, spacing: Float) {
        target -= dy / max(spacing, 1f)
    }

    internal fun onDragEnd() {
        dragging = false
    }

    internal fun onWheel(delta: Float, spacing: Float, speedScale: Float) {
        target += (delta * speedScale / max(spacing * 2f, 1f)).coerceIn(-1.5f, 1.5f)
    }

    /** Turn by [by] cards. Public so a host can drive it from its own chrome. */
    fun step(by: Int) {
        target += by.toFloat()
    }

    // --- frame ---------------------------------------------------------------

    internal fun advance(deltaMs: Float, speed: Float, drift: Boolean, count: Int) {
        tick++
        val dt = min(0.05f, deltaMs / 1000f)

        val wanted = if (drift && !dragging) speed else 0f
        autoSpeed += (wanted - autoSpeed) * (1f - exp(-dt * SPEED_BLEND))
        target += autoSpeed * dt

        val follow = 1f - exp(-dt * if (dragging) FOLLOW_DRAGGING else FOLLOW)
        progress += (target - progress) * follow

        if (count > 0) {
            val near = ((progress.roundToInt() % count) + count) % count
            if (active != near) active = near
        }
    }
}

/** Signed distance from [value] to zero around a cycle of [size]. */
internal fun wrapHalf(value: Float, size: Float): Float {
    val half = size / 2f
    return ((value + half) % size + size) % size - half
}

internal fun smoothstepAt(from: Float, to: Float, value: Float): Float {
    val span = to - from
    val x = (if (abs(span) < 1e-6f) 0f else (value - from) / span).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}
