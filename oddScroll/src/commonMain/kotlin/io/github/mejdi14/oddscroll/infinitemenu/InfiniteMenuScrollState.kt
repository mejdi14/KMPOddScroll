package io.github.mejdi14.oddscroll.infinitemenu

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import io.github.mejdi14.oddscroll.internal.PI_F
import io.github.mejdi14.oddscroll.internal.Quat
import io.github.mejdi14.oddscroll.internal.clamped
import io.github.mejdi14.oddscroll.internal.fibonacciSphere
import kotlin.math.acos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/** Radius of the sphere, in world units. Everything else is measured off it. */
internal const val SPHERE_RADIUS = 2f
internal const val SPHERE_FOV = 30f * PI_F / 180f

/** How far a drag across the short side of the frame turns the sphere. */
private const val DRAG_TURN = 2.6f

/** How fast the measured spin follows the hand, and how fast it falls still. */
private const val TRACK = 30f
private const val HELD_DECAY = 14f

/** The snap gives way to the spin, so a throw is not fought while it is fast. */
private const val PULL_SPEED = 1.4f
private const val PULL_RATE = 8f
private const val AIMED_PULL_RATE = 6f

private const val CAMERA_RATE = 4.5f

/** Over this the sphere counts as moving and the caption steps out of the way. */
private const val MOVING_SPEED = 0.08f
private const val MOVING_ANGLE = 0.01f

/**
 * Orientation of the sphere, and everything that is still turning it.
 *
 * There is no scroll position here at all. The whole of the state is which way
 * the sphere is facing, as a quaternion, plus the spin still in it — which is
 * why a drag from any direction composes with the last one instead of being
 * resolved into axes that would lock at the poles.
 */
class InfiniteMenuScrollState internal constructor(
    internal val tileCount: Int,
    private val itemCount: Int,
    initialIndex: Int = 0,
) {
    /** Unit directions of every tile, before the sphere is turned. */
    internal val tiles: FloatArray = fibonacciSphere(tileCount)

    internal val orientation = Quat(0.08f, -0.3f, 0f, 1f).also { it.normalize() }

    /** Index of the tile facing the camera. */
    internal var front = 0
        private set

    /** Wrapped index of the item shown by the tile at the front. */
    var currentIndex by mutableIntStateOf(0)
        private set

    /** True while the sphere is being dragged, still spinning, or still turning onto a tile. */
    var isScrollInProgress by mutableStateOf(false)
        private set

    /** 1 when the sphere has come to rest, 0 while it moves. Drives the caption. */
    internal var settle by mutableFloatStateOf(0f)
        private set

    /** Bumped once per frame, so the sphere repaints while it turns. */
    internal var tick by mutableIntStateOf(0)
        private set

    internal var camera = 0f
        private set

    internal var speed = 0f
        private set

    internal var axisX = 0f
    internal var axisY = 1f
    internal var axisZ = 0f

    internal var dragging = false
        private set

    internal var viewportWidth = 0f
    internal var viewportHeight = 0f

    /** Where the tiles landed last frame, for hit testing. */
    internal val visible = ArrayList<VisibleTile>()

    internal class VisibleTile(
        val index: Int,
        val x: Float,
        val y: Float,
        val radius: Float,
        val depth: Float,
    )

    /**
     * Nearest tile under the point. The tiles overlap, so the one closest to
     * the camera wins rather than the first found.
     */
    internal fun pick(at: Offset): Int {
        var best = -1
        var bestDepth = -2f
        for (tile in visible) {
            if (tile.depth <= bestDepth) continue
            if (hypot(at.x - tile.x, at.y - tile.y) > tile.radius) continue
            bestDepth = tile.depth
            best = tile.index
        }
        return best
    }

    private var target = -1
    private var pendingX = 0f
    private var pendingY = 0f
    private var idle = 0f
    private val scratch = FloatArray(3)

    init {
        if (initialIndex != 0) scrollTo(initialIndex)
    }

    // --- input ---------------------------------------------------------------

    internal fun onDragStart() {
        dragging = true
        target = -1
        pendingX = 0f
        pendingY = 0f
    }

    internal fun onDrag(dx: Float, dy: Float) {
        pendingX += dx
        pendingY += dy
    }

    internal fun onDragEnd() {
        dragging = false
    }

    /** Brings tile [tile] round to the front along the shortest arc. */
    internal fun aimAt(tile: Int) {
        if (tile in 0 until tileCount) target = tile
    }

    /** The tile showing item [index] that already sits nearest the front. */
    private fun tileFor(index: Int): Int {
        val item = ((index % itemCount) + itemCount) % itemCount
        var best = item.coerceIn(0, tileCount - 1)
        var bestZ = -2f
        var tile = item
        while (tile < tileCount) {
            val z = orientation.rotatedZ(tiles[tile * 3], tiles[tile * 3 + 1], tiles[tile * 3 + 2])
            if (z > bestZ) {
                bestZ = z
                best = tile
            }
            tile += itemCount
        }
        return best
    }

    /**
     * Turns the sphere to face the tile showing item [index], with no
     * animation. A sphere has no order to step through, so there is no `step`;
     * this and [animateScrollTo] are the programmatic controls.
     */
    fun scrollTo(index: Int) {
        val tile = tileFor(index)
        orientation.rotate(tiles[tile * 3], tiles[tile * 3 + 1], tiles[tile * 3 + 2], scratch)
        val angle = acos(scratch[2].clamped(-1f, 1f))
        val ax = scratch[1]
        val ay = -scratch[0]
        val length = hypot(ax, ay)
        if (length > 1e-6f) orientation.turn(ax / length, ay / length, 0f, angle)
        speed = 0f
        target = -1
        front = tile
        currentIndex = tile % itemCount
    }

    /** Turns the sphere to face the tile showing item [index], along the shortest arc. */
    fun animateScrollTo(index: Int) {
        aimAt(tileFor(index))
    }

    /**
     * The tile one step away in a screen direction, for autoplay: there is no
     * "next" on a sphere, so the neighbour is whichever tile sits nearest a
     * point stepped off the front by roughly one tile's spacing.
     */
    internal fun neighbour(dx: Float, dy: Float): Int {
        val spacing = sqrt(4f * PI_F / tileCount.coerceAtLeast(1))
        val length = hypot(dx, dy)
        if (length <= 1e-5f) return front
        val tx = dx / length * spacing
        val ty = dy / length * spacing
        val tz = 1f
        val norm = sqrt(tx * tx + ty * ty + tz * tz)
        return nearestTo(tx / norm, ty / norm, tz / norm)
    }

    // --- frame ---------------------------------------------------------------

    internal fun advance(deltaMs: Float, settings: MenuSettings) {
        tick++
        val dt = (deltaMs / 1000f).clamped(0.001f, 0.05f)

        val minSide = min(viewportWidth, viewportHeight)
        if (dragging) {
            if ((pendingX != 0f || pendingY != 0f) && minSide > 0f) {
                val length = hypot(pendingX, pendingY)
                val angle = length / minSide * DRAG_TURN * settings.dragSpeed
                // The axis is the drag turned a quarter: pulling right spins the
                // sphere about its vertical, pulling down about its horizontal.
                val ax = pendingY / length
                val ay = pendingX / length
                orientation.turn(ax, ay, 0f, angle)
                val blend = 1f - exp(-dt * TRACK)
                axisX += (ax - axisX) * blend
                axisY += (ay - axisY) * blend
                axisZ += (0f - axisZ) * blend
                normalizeAxis()
                speed += (angle / dt - speed) * blend
                pendingX = 0f
                pendingY = 0f
            } else {
                speed *= exp(-dt * HELD_DECAY)
            }
        } else {
            if (speed > 0.0005f) {
                orientation.turn(axisX, axisY, axisZ, speed * dt)
                speed *= exp(-dt * (6f - 5f * settings.inertia))
            } else {
                speed = 0f
            }

            val goal = if (target >= 0) target else nearestFront()
            orientation.rotate(
                tiles[goal * 3], tiles[goal * 3 + 1], tiles[goal * 3 + 2], scratch,
            )
            val angle = acos(scratch[2].clamped(-1f, 1f))
            // The snap stands down while the sphere is still travelling, so a
            // throw is allowed to run instead of being hauled back mid-flight.
            val pull = (1f - speed / PULL_SPEED).clamped(0f, 1f)
            if (angle > 0.0002f && pull > 0f) {
                // Axis that carries the goal tile toward the camera: its own
                // direction crossed with straight ahead.
                val ax = scratch[1]
                val ay = -scratch[0]
                val length = hypot(ax, ay)
                if (length > 1e-6f) {
                    val rate = if (target >= 0) AIMED_PULL_RATE else PULL_RATE
                    val step = angle * (1f - exp(-dt * rate)) * pull
                    orientation.turn(ax / length, ay / length, 0f, step)
                }
            } else if (target >= 0 && speed == 0f) {
                target = -1
            }
            front = goal
        }

        val settled = acos(
            orientation.rotatedZ(
                tiles[front * 3], tiles[front * 3 + 1], tiles[front * 3 + 2],
            ).clamped(-1f, 1f)
        )
        val moving = dragging || speed > MOVING_SPEED || settled > MOVING_ANGLE
        val want = if (moving) 0f else 1f
        settle += (want - settle) * (1f - exp(-dt * if (moving) 10f else 3f))
        if (!moving && settle > 0.998f) settle = 1f

        val aspect = if (viewportHeight > 0f) viewportWidth / viewportHeight else 1f
        val rest = restDistance(settings, aspect)
        // The sphere is pushed away while it is being handled and drawn back in
        // once it settles, so a throw opens the whole of it out and reading one
        // tile brings that tile close.
        val pullAmount = (if (dragging) 1f else speed / 2.2f).clamped(0f, 1f)
        val wanted = rest + settings.pullBack * pullAmount * SPHERE_RADIUS * 1.25f
        if (camera == 0f) camera = wanted
        camera += (wanted - camera) * (1f - exp(-dt * CAMERA_RATE))

        if (settings.autoplaySeconds > 0f && !moving) {
            idle += dt
            if (idle >= settings.autoplaySeconds) {
                idle = 0f
                aimAt(neighbour(1f, 0f))
            }
        } else {
            idle = 0f
        }

        val item = if (itemCount > 0) front % itemCount else 0
        if (currentIndex != item) currentIndex = item
        val inProgress = moving || target >= 0
        if (isScrollInProgress != inProgress) isScrollInProgress = inProgress
    }

    private fun normalizeAxis() {
        val length = sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ)
        if (length <= 1e-6f) {
            axisX = 0f; axisY = 1f; axisZ = 0f
            return
        }
        axisX /= length; axisY /= length; axisZ /= length
    }

    private fun nearestFront(): Int = nearestTo(0f, 0f, 1f)

    /** Tile whose turned direction points closest to the given direction. */
    private fun nearestTo(dx: Float, dy: Float, dz: Float): Int {
        var best = 0
        var bestDot = -2f
        for (i in 0 until tileCount) {
            orientation.rotate(tiles[i * 3], tiles[i * 3 + 1], tiles[i * 3 + 2], scratch)
            val d = scratch[0] * dx + scratch[1] * dy + scratch[2] * dz
            if (d > bestDot) {
                bestDot = d
                best = i
            }
        }
        return best
    }
}

/** Settings the frame loop needs, gathered so they can change between frames. */
internal class MenuSettings(
    val tileCount: Int,
    val tileSize: Float,
    val zoom: Float,
    val pullBack: Float,
    val dragSpeed: Float,
    val inertia: Float,
    val autoplaySeconds: Float,
)

/** Angular size of one tile on the sphere, turned into world units. */
internal fun tileRadiusOf(tileCount: Int, tileSize: Float): Float =
    0.5f * SPHERE_RADIUS * sqrt(4f * PI_F / tileCount.coerceAtLeast(1)) * tileSize

/**
 * How far back the camera sits when nothing is happening: close enough that a
 * tile fills its share of the frame, which depends on the narrower side.
 */
internal fun restDistance(settings: MenuSettings, aspect: Float): Float {
    val span = tan(SPHERE_FOV / 2f) * min(1f, aspect)
    return SPHERE_RADIUS +
        tileRadiusOf(settings.tileCount, settings.tileSize) / (0.46f * settings.zoom * span)
}
