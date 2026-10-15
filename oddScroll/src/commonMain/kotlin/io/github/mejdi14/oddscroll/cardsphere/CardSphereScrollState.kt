package io.github.mejdi14.oddscroll.cardsphere

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import io.github.mejdi14.oddscroll.internal.Quat
import io.github.mejdi14.oddscroll.internal.cubicBezierEase
import io.github.mejdi14.oddscroll.internal.dot
import io.github.mejdi14.oddscroll.internal.premultiplyBy
import io.github.mejdi14.oddscroll.internal.quatBetween
import io.github.mejdi14.oddscroll.internal.setTo
import io.github.mejdi14.oddscroll.internal.slerp
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow

/** Fast away, long glide in: the settle should look decided, not sprung. */
private val SETTLE = cubicBezierEase(0.25f, 0.75f, 0.2f, 1f)

/** Spin left after a flick decays by this much every sixtieth of a second. */
private const val COAST_DECAY = 0.88f

/** Under this the spin is spent and the field may commit to a hero. */
private const val SNAP_SPEED = 1.6f

/** Quiet time after the last input before the settle is allowed to begin. */
private const val SNAP_IDLE_MS = 60f

/**
 * Which way the sphere faces, and what is still turning it.
 *
 * Orientation is a quaternion because the field is pushed from any direction:
 * a diagonal drag is one turn about one axis, and it composes with the next
 * whatever direction that arrives from. Angles would lock at the poles and
 * would split one gesture into two.
 */
class CardSphereScrollState internal constructor() {
    internal val cards: List<SphereCard> = buildField()

    internal val rotation = Quat()

    /** Index of the hero card last settled at the front. */
    var hero by mutableIntStateOf(0)
        internal set

    /** Page colour. Flipped by a double tap. */
    var dark by mutableStateOf(false)
        internal set

    /** Bumped once per frame, so the field repaints while it turns. */
    internal var tick by mutableIntStateOf(0)
        private set

    /** Angular velocity, as an axis scaled by radians a second. */
    internal var spinX = 0f
    internal var spinY = 0f
    internal var spinZ = 0f

    internal var dragging = false
        private set

    /** Written by the draw pass: the projection the input has to speak in. */
    internal var focal = 1f
    internal var stretchX = 1f
    internal var stretchY = 1f
    internal var unit = 1f

    /** The morphing hero's icon, which is sticky once it opens. */
    internal var morphIcon = "search"

    /** Where the cards landed last frame, for hit testing. */
    internal val visible = ArrayList<VisibleCard>()

    internal class VisibleCard(
        val index: Int,
        val x: Float,
        val y: Float,
        val halfW: Float,
        val halfH: Float,
        val depth: Float,
    )

    /**
     * Nearest card under the point, in density-independent units. The field
     * overlaps heavily, so the one closest to the camera wins rather than the
     * first one found.
     */
    internal fun pick(at: Offset, density: Float): Int {
        val px = at.x / density
        val py = at.y / density
        var best = -1
        var bestDepth = Float.MAX_VALUE
        for (card in visible) {
            if (card.depth >= bestDepth) continue
            if (abs(px - card.x) > card.halfW || abs(py - card.y) > card.halfH) continue
            bestDepth = card.depth
            best = card.index
        }
        return best
    }

    private val scratch = FloatArray(3)
    private val between = Quat()
    private val ahead = Quat()
    private val snapFrom = Quat()
    private val snapTo = Quat()
    private var snapping = false
    private var snapStartMs = 0f
    private var snapMs = 0f
    private var snapHero = 0
    private var settled = true
    private var lastInputMs = 0f
    private var nowMs = 0f

    init {
        // Hero zero starts facing the camera.
        quatBetween(cards[0].x, cards[0].y, cards[0].z, 0f, 0f, 1f, rotation)
    }

    // --- input ---------------------------------------------------------------

    internal fun onDragStart() {
        dragging = true
        snapping = false
        settled = false
        spinX = 0f; spinY = 0f; spinZ = 0f
    }

    /**
     * Turns by the drag, measured against the ellipsoid rather than the screen,
     * so a push feels the same on the stretched axis as on the other one.
     */
    internal fun onDrag(dx: Float, dy: Float) {
        val sx = dx / stretchX
        val sy = dy / stretchY
        val angle = hypot(sx, sy) / (focal * 0.5f)
        if (angle <= 0f) return
        val length = hypot(sy, sx)
        if (length <= 1e-6f) return
        rotation.turn(sy / length, sx / length, 0f, angle)
    }

    internal fun onDragEnd() {
        dragging = false
        lastInputMs = nowMs
    }

    /** [vx] and [vy] are pointer velocity in pixels a second. */
    internal fun flick(vx: Float, vy: Float) {
        val k = 1f / (focal * 0.5f)
        spinX = (vy / stretchY) * k
        spinY = (vx / stretchX) * k
        spinZ = 0f
        settled = false
        snapping = false
        lastInputMs = nowMs
    }

    /** Brings [index] to the front, or the nearest hero when it is out of range. */
    fun goTo(index: Int) {
        startSnap(if (index in 0 until HERO_COUNT) index else -1)
    }

    internal fun toggleTheme() {
        dark = !dark
    }

    // --- frame ---------------------------------------------------------------

    internal fun advance(nowMs: Float, deltaMs: Float) {
        this.nowMs = nowMs
        tick++
        val dt = min(0.05f, deltaMs / 1000f)

        if (!dragging && !snapping) {
            val speed = hypot(hypot(spinX, spinY), spinZ)
            if (speed > 0.02f) {
                rotation.turn(spinX / speed, spinY / speed, spinZ / speed, speed * dt)
                val k = COAST_DECAY.pow(dt * 30f)
                spinX *= k; spinY *= k; spinZ *= k
            }
            // The settle waits for the spin to fall off and for the hand to
            // have actually let go — otherwise a slow drag would be fought by
            // the snap the whole way.
            if (!settled && speed < SNAP_SPEED && nowMs - lastInputMs > SNAP_IDLE_MS) {
                startSnap(-1)
            }
        }

        if (snapping) {
            val t = ((nowMs - snapStartMs) / snapMs).coerceIn(0f, 1f)
            slerp(snapFrom, snapTo, SETTLE(t), rotation)
            rotation.normalize()
            if (t >= 1f) {
                snapping = false
                spinX = 0f; spinY = 0f; spinZ = 0f
                settled = true
                if (hero != snapHero) hero = snapHero
            }
        }
    }

    /**
     * Chooses a hero and begins the glide to it.
     *
     * The choice is made from where the sphere is *heading*, not where it is:
     * the spin is run forward a little first, so a flick lands on the card it
     * was thrown toward rather than the one that happened to be passing when
     * the finger lifted.
     */
    private fun startSnap(wanted: Int) {
        val speed = hypot(hypot(spinX, spinY), spinZ)
        ahead.setTo(rotation)
        if (speed > 0f) {
            ahead.turn(spinX / speed, spinY / speed, spinZ / speed, min(2.4f, speed * 0.3f))
        }

        var best = wanted
        if (best < 0) {
            var bestZ = -2f
            for (i in 0 until HERO_COUNT) {
                val card = cards[i]
                val z = ahead.rotatedZ(card.x, card.y, card.z)
                if (z > bestZ) {
                    bestZ = z
                    best = i
                }
            }
        }
        if (best < 0) best = 0

        val card = cards[best]
        rotation.rotate(card.x, card.y, card.z, scratch)
        quatBetween(scratch[0], scratch[1], scratch[2], 0f, 0f, 1f, between)

        snapFrom.setTo(rotation)
        snapTo.setTo(rotation)
        snapTo.premultiplyBy(between.x, between.y, between.z, between.w)

        val angle = 2f * acos(abs(rotation.dot(snapTo)).coerceIn(0f, 1f))
        snapStartMs = nowMs
        snapMs = 420f + angle * 260f
        snapHero = best
        snapping = true
        settled = false
    }
}
