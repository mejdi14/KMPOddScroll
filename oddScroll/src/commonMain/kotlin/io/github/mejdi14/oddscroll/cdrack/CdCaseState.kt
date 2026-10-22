package io.github.mejdi14.oddscroll.cdrack

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.mejdi14.oddscroll.internal.clamped
import io.github.mejdi14.oddscroll.internal.inOutCubic
import io.github.mejdi14.oddscroll.internal.lerp
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The motion is the reference's, key for key: the open and the close are
 * hand-keyed curves for the turn, the lid and the spread, smoothed with
 * monotone cubic splines, and a grabbed case springs back when let go.
 */

private const val TWO_PI = (2.0 * PI).toFloat()

/** How far open the lid swings, round the hinge. */
private const val OPEN_ANGLE = 3.05f
private const val OPEN_T = 2.3f
private const val CLOSE_T = 1.95f

/** The disc turns this fast, in radians a second, and eases to and from it. */
private val SPIN = 18f / 180f * PI.toFloat()
private const val SPIN_EASE = 0.9f

/** Turn per dp of drag, and how far a drag can turn and tip it. */
private const val GRAB_RATE = 0.008f
private const val GRAB_YAW = 0.9f
private const val GRAB_TILT = 0.45f

/** The spring a let-go case comes back on. */
private const val GRAB_K = 16f
private const val GRAB_C = 5.5f

/** A let-go after the finger has been still this long throws nothing. */
private const val STILL_MS = 90L

private val openYaw = monotone(
    0f to 0f, 0.55f to 0.3f, 1.0f to 0.85f, 1.2f to 2.0f, 1.41f to 3.4f,
    1.63f to 4.9f, 1.85f to 6.05f, 2.05f to 6.36f, 2.3f to TWO_PI,
)
private val openLid = monotone(
    0f to 0f, 0.97f to 0f, 1.2f to 0.45f, 1.41f to 1.35f, 1.63f to 2.3f,
    1.85f to 2.92f, 2.05f to 3.12f, 2.3f to OPEN_ANGLE,
)
private val openSpread = monotone(0f to 0f, 1.12f to 0f, 1.9f to 0.9f, 2.3f to 1f)
private val openPitch = monotone(0f to 0f, 1.35f to 0f, 2.3f to -0.1f)

private val closeLid = monotone(
    0f to OPEN_ANGLE, 0.22f to 1.75f, 0.48f to 1.55f, 1.05f to 1.08f, 1.42f to 0.6f,
    1.6f to 0.26f, 1.7f to 0f, 1.78f to 0.07f, 1.88f to 0f,
)
private val closeSpread = monotone(0f to 1f, 0.3f to 0.92f, 1.2f to 0.55f, 1.72f to 0f)
private val closeYaw = monotone(0f to 0f, 0.5f to 0.2f, 1.3f to 0.12f, 1.88f to 0f)
private val closePitch = monotone(
    0f to -0.1f, 1.6f to -0.05f, 1.7f to 0.035f, 1.82f to -0.01f, 1.95f to 0f,
)
private val closeRoll = monotone(0f to 0f, 0.8f to -0.05f, 1.95f to 0f)

/** Where the case is this frame: its turn, the lid's swing, how far it has moved over for being open. */
internal class CasePose {
    var x = 0f
    var y = 0f
    var z = 0f
    var scale = 1f
    var yaw = 0f
    var pitch = 0f
    var roll = 0f
    var lid = 0f
    var spread = 0f
    var spin = 0f
}

private enum class Phase { Rest, Opening, Open, Closing }

@androidx.compose.runtime.Composable
fun rememberCdCaseState(): CdCaseState = androidx.compose.runtime.remember { CdCaseState() }

/**
 * A single CD case: shut and idling, opening, open, or shutting again. Tap it
 * to open or shut it; drag it to turn it.
 */
class CdCaseState internal constructor() {

    /** Whether the case is open or on its way open. */
    var isOpen by mutableStateOf(false)
        private set

    /** Bumped once per frame, so the case repaints while anything moves. */
    internal var tick by mutableIntStateOf(0)
        private set

    private var t = 0f
    private var phase = Phase.Rest
    private var phaseAt = 0f
    private var closeAfter = false

    private var spin = 0f
    private var spinRate = SPIN

    private var grabbing = false
    private var grabX = 0f
    private var grabY = 0f
    private var grabVx = 0f
    private var grabVy = 0f
    private var flickX = 0f
    private var flickY = 0f
    private var movedAt = 0L

    /** Opens the case, if it is shut. */
    fun open() {
        if (phase != Phase.Rest) return
        phase = Phase.Opening
        phaseAt = t
        isOpen = true
    }

    /** Shuts the case; one still opening shuts as soon as it is open. */
    fun close() {
        if (phase == Phase.Opening) {
            closeAfter = true
            return
        }
        if (phase != Phase.Open) return
        phase = Phase.Closing
        phaseAt = t
        isOpen = false
    }

    fun toggle() {
        if (phase == Phase.Opening || phase == Phase.Open) close() else open()
    }

    /** Shut and still: not opening, open or closing. */
    internal val shut: Boolean get() = phase == Phase.Rest

    // --- input -----------------------------------------------------------------

    internal fun onGrab() {
        grabbing = true
        flickX = 0f
        flickY = 0f
    }

    /** [dx], [dy] in dp; [millis] is the event's uptime. */
    internal fun onGrabMove(dx: Float, dy: Float, millis: Long) {
        if (!grabbing) return
        grabY = (grabY + dx * GRAB_RATE).clamped(-GRAB_YAW, GRAB_YAW)
        grabX = (grabX + dy * GRAB_RATE).clamped(-GRAB_TILT, GRAB_TILT)
        flickX = dy * GRAB_RATE * 60f
        flickY = dx * GRAB_RATE * 60f
        movedAt = millis
    }

    internal fun onLetGo(millis: Long) {
        if (!grabbing) return
        grabbing = false
        val still = millis - movedAt > STILL_MS
        grabVx = if (still) 0f else flickX
        grabVy = if (still) 0f else flickY
    }

    // --- frame -----------------------------------------------------------------

    internal fun advance(dt: Float, spinning: Boolean) {
        tick++
        t += dt

        val want = if (spinning) SPIN else 0f
        spinRate += (want - spinRate) * (1f - exp(-dt * SPIN_EASE * 3f))
        spin += spinRate * dt
        if (spin > TWO_PI) spin -= TWO_PI

        if (!grabbing) {
            grabVx += (-GRAB_K * grabX - GRAB_C * grabVx) * dt
            grabVy += (-GRAB_K * grabY - GRAB_C * grabVy) * dt
            grabX += grabVx * dt
            grabY += grabVy * dt
        }

        val since = t - phaseAt
        if (phase == Phase.Opening && since >= OPEN_T) {
            phase = Phase.Open
            phaseAt = t
            if (closeAfter) {
                closeAfter = false
                close()
            }
        } else if (phase == Phase.Closing && since >= CLOSE_T) {
            phase = Phase.Rest
            phaseAt = t
        }
    }

    internal fun pose(out: CasePose, openScale: Float) {
        // Idling, it sways a little, turned a touch off square.
        out.y = 1.8f * sin(t * 1.1f)
        out.yaw = -0.2f + 0.1f * sin(t * 0.9f) + 0.03f * sin(t * 2.1f)
        out.pitch = -0.12f + 0.045f * sin(t * 0.7f)
        out.roll = 0.03f + 0.018f * sin(t * 0.5f)
        out.lid = 0f
        out.spread = 0f

        val since = t - phaseAt
        when (phase) {
            Phase.Rest -> Unit
            Phase.Opening -> {
                val q = minOf(since, OPEN_T)
                out.yaw = lerp(out.yaw, 0f, (q / 1f).clamped(0f, 1f)) + openYaw(q)
                out.lid = openLid(q)
                out.spread = openSpread(q)
                out.pitch = lerp(out.pitch, openPitch(q), (q / 1.45f).clamped(0f, 1f))
                out.roll *= 1f - (q / 1.45f).clamped(0f, 1f)
            }
            Phase.Open -> {
                out.yaw = 0.06f * sin(t * 0.6f)
                out.pitch = -0.1f + 0.02f * sin(t * 0.8f)
                out.roll = 0f
                out.lid = OPEN_ANGLE + 0.025f * sin(t * 1.3f)
                out.spread = 1f
            }
            Phase.Closing -> {
                val q = minOf(since, CLOSE_T)
                val back = inOutCubic(((q - 0.9f) / (CLOSE_T - 0.9f)).clamped(0f, 1f))
                out.yaw = lerp(closeYaw(q), out.yaw, back)
                out.pitch = lerp(closePitch(q), out.pitch, back)
                out.roll = lerp(closeRoll(q), out.roll, back)
                out.lid = closeLid(q)
                out.spread = closeSpread(q)
            }
        }

        out.pitch += grabX
        out.yaw += grabY
        // Open, the pair is centred: the tray moves over by half a case, the
        // lid swung out to its left.
        val s = lerp(1f, openScale, out.spread)
        out.x = HALF_MM * s * out.spread * 0.92f
        out.z = 0f
        out.scale = s
        out.spin = spin
    }
}

/**
 * A monotone cubic through [keys] (Fritsch–Carlson), flat outside them: it
 * never overshoots a key, which is what lets a lid land on its stop.
 */
private fun monotone(vararg keys: Pair<Float, Float>): (Float) -> Float {
    val n = keys.size
    val ts = FloatArray(n) { keys[it].first }
    val vs = FloatArray(n) { keys[it].second }
    val d = FloatArray(n - 1) { (vs[it + 1] - vs[it]) / (ts[it + 1] - ts[it]) }
    val m = FloatArray(n)
    for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
    for (i in 0 until n - 1) {
        if (d[i] == 0f) {
            m[i] = 0f
            m[i + 1] = 0f
            continue
        }
        val a = m[i] / d[i]
        val b = m[i + 1] / d[i]
        val s = a * a + b * b
        if (s > 9f) {
            val k = 3f / sqrt(s)
            m[i] = k * a * d[i]
            m[i + 1] = k * b * d[i]
        }
    }
    return { t ->
        when {
            t <= ts[0] -> vs[0]
            t >= ts[n - 1] -> vs[n - 1]
            else -> {
                var i = 0
                while (t > ts[i + 1]) i++
                val h = ts[i + 1] - ts[i]
                val u = (t - ts[i]) / h
                val u2 = u * u
                val u3 = u2 * u
                (2f * u3 - 3f * u2 + 1f) * vs[i] + (u3 - 2f * u2 + u) * h * m[i] +
                    (-2f * u3 + 3f * u2) * vs[i + 1] + (u3 - u2) * h * m[i + 1]
            }
        }
    }
}
