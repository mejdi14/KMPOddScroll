package io.github.mejdi14.oddscroll.infinitespiral

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import io.github.mejdi14.oddscroll.internal.PI_F
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

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
class InfiniteSpiralScrollState internal constructor(initialIndex: Int) {
    internal var progress = initialIndex.toFloat()
        private set

    /** Wrapped index of the card nearest the middle. */
    var currentIndex by mutableIntStateOf(initialIndex.coerceAtLeast(0))
        private set

    /** True while a drag is held or the spiral is still catching up to where it is aimed. */
    var isScrollInProgress by mutableStateOf(false)
        private set

    /** Cards in the spiral, so an index can be wrapped. Kept up to date by the composable. */
    internal var itemCount = 0

    /** Bumped once per frame, so the spiral repaints while it turns. */
    internal var tick by mutableIntStateOf(0)
        private set

    internal var dragging = false
        private set

    private var target = initialIndex.toFloat()
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

    /** Turns the spiral by [by] cards, easing there. Negative goes the other way. */
    fun step(by: Int) {
        target += by.toFloat()
    }

    /**
     * Puts card [index] at the middle with no animation, taking the way round
     * that is nearest to where the spiral is now.
     */
    fun scrollTo(index: Int) {
        if (itemCount <= 0) return
        progress += wrapHalf(index - progress, itemCount.toFloat())
        target = progress
        wrapIndexNow()
    }

    /**
     * Turns the spiral to put card [index] at the middle, by the nearer way
     * round and with the spiral's own easing.
     */
    fun animateScrollTo(index: Int) {
        if (itemCount <= 0) return
        target += wrapHalf(index - target, itemCount.toFloat())
    }

    /** Card whose centre contains [at], in dp, nearest to the viewer first; -1 if none. */
    internal fun pick(at: Offset): Int {
        var found = -1
        // Drawn far side first, so the last match is the one on top.
        for (hit in hits) {
            val dx = at.x - hit.x
            val dy = at.y - hit.y
            val a = -hit.tilt * PI_F / 180f
            val lx = dx * cos(a) - dy * sin(a)
            val ly = dx * sin(a) + dy * cos(a)
            if (abs(lx) <= hit.halfW && abs(ly) <= hit.halfH) found = hit.index
        }
        return found
    }

    /** Where the cards landed last frame, in dp, in the order they were drawn. */
    internal val hits = ArrayList<Hit>()

    internal class Hit(
        val index: Int,
        val x: Float,
        val y: Float,
        val halfW: Float,
        val halfH: Float,
        val tilt: Float,
    )

    private fun wrapIndexNow() {
        if (itemCount <= 0) return
        val near = ((progress.roundToInt() % itemCount) + itemCount) % itemCount
        if (currentIndex != near) currentIndex = near
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

        itemCount = count
        wrapIndexNow()
        val moving = dragging || abs(target - progress) > 0.01f
        if (isScrollInProgress != moving) isScrollInProgress = moving
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
