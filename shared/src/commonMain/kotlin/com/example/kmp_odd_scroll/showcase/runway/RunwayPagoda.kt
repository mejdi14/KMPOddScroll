package com.example.kmp_odd_scroll.showcase.runway

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp as lerpColor
import com.example.kmp_odd_scroll.showcase.internal.PI_F
import com.example.kmp_odd_scroll.showcase.internal.clamped
import com.example.kmp_odd_scroll.showcase.internal.lerp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/*
 * A paper pagoda, in runway px. It is an octagonal tower in lifts, bottom to
 * top:
 *
 *   terrace       a wide low platform with a railing, stairs and a pier
 *   ground hall   podium, walls, a colonnade, and the big roof
 *   storeys       balcony, latticed walls, a skirt of roof; none to three
 *   top storey    the same under the tall concave roof
 *   spire         ringed mast, ball, rod, and the chains to the roof corners
 *
 * Folded, every height is nought and what lies on the plinth is the tower's
 * plan: its roofs as rings one inside the next. It stands by telescoping, each
 * lift pulling up out of the one below a moment after it, so the fold travels
 * up the tower rather than happening all at once.
 */
private const val TERRACE_HEIGHT = 22f
private const val HALL_HEIGHT = 116f
private const val STOREY_HEIGHT = 72f
private const val TOP_STOREY_HEIGHT = 145f
private const val SPIRE_HEIGHT = 110f

/** The most storeys a pagoda has above its hall, the top one included. */
internal const val PAGODA_MAX_STOREYS = 4
private const val MAX_LIFTS = PAGODA_MAX_STOREYS + 3

/** How much of the fold one lift takes to come up; the lifts start evenly through the rest. */
private const val LIFT_SPAN = 0.37f

/** Below this a lift has no height worth drawing walls for. */
private const val MIN_RISE = 0.02f

private const val TERRACE_R = 195f
private const val PODIUM_R = 138f
private const val HALL_WALL_R = 104f
private const val HALL_POST_R = 124f

/*
 * The octagon has a flat face to the viewer. Vertex k sits (k + 0.5) eighths of
 * a turn round from "toward the viewer", turning toward the right; face j runs
 * from vertex j to vertex j + 1, so face 7 is the front and face 3 the back.
 */
private val OCT_X = FloatArray(8) { sin((it + 0.5f) * PI_F / 4f) }
private val OCT_S = FloatArray(8) { cos((it + 0.5f) * PI_F / 4f) }

/** Faces far to near. The first three are the far side of a roof, the rest the near side. */
private val FAR_TO_NEAR = intArrayOf(3, 2, 4, 1, 5, 0, 6, 7)

/** How much of the light, which comes from the front left, each face catches. */
private val FACE_LIGHT = FloatArray(8) { 0.5f + 0.5f * cos((it + 1f) * PI_F / 4f + 0.7f) }

private val LINE = Color(0xFF4F5ABF)
private val ROOF_LIGHT = Color(0xFFCDD3F7)
private val ROOF_DARK = Color(0xFF9DA8E8)
private val ROOF_FLAT = Color(0xFFBDC5F2)
private val WALL_LIGHT = Color(0xFFF7F8FF)
private val WALL_DARK = Color(0xFFD3D9F6)
private val DECK = Color(0xFFECEEFB)
private val OPENING = Color(0xFF7F8BDC)
private val SPIRE = Color(0xFF7783DA)

/**
 * Draws the pagoda. One instance is kept for the life of the scroll: it owns
 * the scratch paths, so a frame builds no objects.
 */
internal class PaperPagoda {
    private val fill = Path()
    private val ink = Path()
    private val faint = Path()
    private val cut = Path()

    /** How far up each lift is, `0 .. 1` and a touch over, and the height it starts from. */
    private val liftHeight = FloatArray(MAX_LIFTS)
    private val rise = FloatArray(MAX_LIFTS)
    private val base = FloatArray(MAX_LIFTS + 1)

    /** Storeys above the hall, and the lifts they make: the top storey's, the spire's, and how many in all. */
    private var storeys = PAGODA_MAX_STOREYS
    private var top = 5
    private var mast = 6
    private var lifts = 7

    // The runway's projection, taken as linear about the middle of the tower.
    private var originX = 0f
    private var u0 = 0f
    private var uPerX = 0f
    private var yn0 = 0f
    private var ynPerS = 0f
    private var wf0 = 0f
    private var wfPerS = 0f
    private var span = 0f
    private var lift = 0f
    private var frameH = 0f
    private var hair = 1f

    // The roof being drawn, and the last point asked of it.
    private var roofIn = 0f
    private var roofOut = 0f
    private var roofTop = 0f
    private var roofEave = 0f
    private var roofFlare = 0f
    private var roofReach = 0f
    private var roofCurve = 2f
    private var px = 0f
    private var py = 0f

    /**
     * [fold] is `0` flat to `1` standing. The tower has [storeys] storeys above
     * its hall and is drawn [scale] times life size. Its axis meets the plinth at
     * lateral [u] and screen row [yn]; [ynPerS] is how far a runway px toward
     * the viewer moves down the frame, [widthFraction] the runway's width
     * there and [widthPerS] how that grows toward the viewer. [across] and
     * [lift] turn runway widths and runway px of height into screen px.
     */
    fun draw(
        scope: DrawScope,
        fold: Float,
        storeys: Int,
        scale: Float,
        originX: Float,
        u: Float,
        yn: Float,
        ynPerS: Float,
        widthFraction: Float,
        widthPerS: Float,
        across: Float,
        lift: Float,
    ) {
        // The scale goes into the projection, so everything below is written at life size.
        this.originX = originX
        u0 = u
        uPerX = scale / PAGE_WIDTH_PX
        yn0 = yn
        this.ynPerS = ynPerS * scale
        wf0 = widthFraction
        wfPerS = widthPerS * scale
        span = across
        this.lift = lift * scale
        frameH = scope.size.height
        hair = (frameH / 1080f).clamped(0.8f, 1.7f)

        this.storeys = storeys.coerceIn(1, PAGODA_MAX_STOREYS)
        top = 1 + this.storeys
        mast = top + 1
        lifts = mast + 1
        liftHeight[0] = TERRACE_HEIGHT
        liftHeight[1] = HALL_HEIGHT
        for (k in 2 until top) liftHeight[k] = STOREY_HEIGHT
        liftHeight[top] = TOP_STOREY_HEIGHT
        liftHeight[mast] = SPIRE_HEIGHT

        val stagger = (1f - LIFT_SPAN) / (lifts - 1)
        var floor = 0f
        for (k in 0 until lifts) {
            rise[k] = popOut(((fold - k * stagger) / LIFT_SPAN).clamped(0f, 1f))
            base[k] = floor
            floor += liftHeight[k] * rise[k]
        }
        base[lifts] = floor

        with(scope) {
            shadow()
            terrace()
            hall()
            for (storey in 0 until this@PaperPagoda.storeys) storey(storey)
            spire()
        }
    }

    /** Ease out with a little overshoot: paper that springs past upright and settles. */
    private fun popOut(t: Float): Float {
        val back = t - 1f
        return 1f + 2.1f * back * back * back + 1.1f * back * back
    }

    // --- Projection ----------------------------------------------------------

    /** Screen x of the point [x] runway px right of the axis and [s] toward the viewer. */
    private fun sx(x: Float, s: Float): Float =
        originX + (u0 + x * uPerX) * (wf0 + s * wfPerS) * span

    /** Screen y of a point [s] runway px toward the viewer and [z] above the plinth. */
    private fun sy(s: Float, z: Float): Float =
        (yn0 + s * ynPerS) * frameH - z * (wf0 + s * wfPerS) * lift

    /** Height of [local] runway px up lift [k], with the lift as far up as it is. */
    private fun z(k: Int, local: Float): Float = base[k] + local * rise[k]

    private fun vx(k: Int, r: Float): Float = sx(r * OCT_X[k and 7], r * OCT_S[k and 7])

    private fun vy(k: Int, r: Float, z: Float): Float = sy(r * OCT_S[k and 7], z)

    /** Whether the upright face [j] of an octagon of radius [r] is turned toward the viewer. */
    private fun faces(j: Int, r: Float): Boolean = vx(j + 1, r) > vx(j, r)

    private fun Path.to(x: Float, s: Float, z: Float, start: Boolean = false) {
        if (start) moveTo(sx(x, s), sy(s, z)) else lineTo(sx(x, s), sy(s, z))
    }

    /** A point [a] of the way along face [j] at radius [r] and height [z]. */
    private fun Path.along(j: Int, r: Float, a: Float, z: Float, start: Boolean = false) {
        val x = r * lerp(OCT_X[j and 7], OCT_X[(j + 1) and 7], a)
        val s = r * lerp(OCT_S[j and 7], OCT_S[(j + 1) and 7], a)
        to(x, s, z, start)
    }

    private fun Path.octagon(r: Float, z: Float) {
        moveTo(vx(0, r), vy(0, r, z))
        for (k in 1..7) lineTo(vx(k, r), vy(k, r, z))
        close()
    }

    /** The upright rectangle on face [j] from [a0] to [a1] along it and [z0] to [z1] up it. */
    private fun Path.pane(j: Int, r: Float, a0: Float, a1: Float, z0: Float, z1: Float) {
        along(j, r, a0, z0, start = true)
        along(j, r, a1, z0)
        along(j, r, a1, z1)
        along(j, r, a0, z1)
        close()
    }

    private fun Path.face(j: Int, r: Float, z0: Float, z1: Float) = pane(j, r, 0f, 1f, z0, z1)

    private fun Path.across(j: Int, r: Float, z: Float, a0: Float = 0f, a1: Float = 1f) {
        along(j, r, a0, z, start = true)
        along(j, r, a1, z)
    }

    private fun Path.upright(j: Int, r: Float, a: Float, z0: Float, z1: Float) {
        along(j, r, a, z0, start = true)
        along(j, r, a, z1)
    }

    private fun DrawScope.stroke(path: Path, weight: Float = 1.15f, color: Color = LINE) {
        drawPath(path, color, style = Stroke(hair * weight))
    }

    // --- Shadow --------------------------------------------------------------

    /** A dark footprint under the terrace, and the tower's shadow thrown back and left as it rises. */
    private fun DrawScope.shadow() {
        fill.reset()
        fill.octagon(TERRACE_R + 7f, 0f)
        drawPath(fill, Color.Black.copy(alpha = 0.30f))

        val height = base[lifts]
        if (height < 6f) return
        val reach = height * 0.58f
        val tipX = -0.47f * reach
        val tipS = -0.88f * reach
        fill.reset()
        fill.to(0.88f * 92f, -0.47f * 92f, 0f, start = true)
        fill.to(tipX + 0.88f * 14f, tipS - 0.47f * 14f, 0f)
        fill.to(tipX - 0.88f * 14f, tipS + 0.47f * 14f, 0f)
        fill.to(-0.88f * 92f, 0.47f * 92f, 0f)
        fill.close()
        drawPath(fill, Color.Black.copy(alpha = 0.20f))
    }

    // --- Lift 0: terrace -----------------------------------------------------

    private fun DrawScope.terrace() {
        val top = base[1]
        val standing = rise[0] > MIN_RISE

        ink.reset()
        if (standing) {
            pier()
            ink.reset()
            for (j in 0..7) {
                if (!faces(j, TERRACE_R)) continue
                fill.reset()
                fill.face(j, TERRACE_R, 0f, top)
                drawPath(fill, lerpColor(WALL_DARK, WALL_LIGHT, FACE_LIGHT[j]))
                ink.face(j, TERRACE_R, 0f, top)
                ink.across(j, TERRACE_R, top * 0.70f)
                ink.across(j, TERRACE_R, top * 0.22f)
            }
        }
        fill.reset()
        fill.octagon(TERRACE_R, top)
        drawPath(fill, DECK)
        ink.octagon(TERRACE_R, top)
        ink.octagon(TERRACE_R - 22f, top)
        stroke(ink)

        stairs(top)

        if (standing) {
            val r = TERRACE_R - 6f
            val railTop = top + 13f * rise[0]
            fill.reset()
            ink.reset()
            for (j in FAR_TO_NEAR) {
                val front = j == 7
                railing(j, r, top, railTop, posts = 6, gap = front)
                if (!faces(j, r)) continue
                if (front) {
                    fill.pane(j, r, 0f, 0.35f, top, railTop)
                    fill.pane(j, r, 0.65f, 1f, top, railTop)
                } else {
                    fill.face(j, r, top, railTop)
                }
            }
            drawPath(fill, WALL_LIGHT.copy(alpha = 0.55f))
            stroke(ink, 1f)
        }
    }

    /** The small landing stage that juts from the terrace's right side. */
    private fun DrawScope.pier() {
        val x0 = TERRACE_R * OCT_X[1] - 6f
        val x1 = x0 + 44f
        val d = 17f
        val top = 15f * rise[0]
        ink.reset()
        fill.reset()
        fill.to(x0, d, 0f, start = true)
        fill.to(x1, d, 0f)
        fill.to(x1, d, top)
        fill.to(x0, d, top)
        fill.close()
        drawPath(fill, WALL_DARK)
        ink.addPath(fill)
        fill.reset()
        fill.to(x0, -d, top, start = true)
        fill.to(x1, -d, top)
        fill.to(x1, d, top)
        fill.to(x0, d, top)
        fill.close()
        drawPath(fill, DECK)
        ink.addPath(fill)
        ink.to(x0 + 30f, -d, top, start = true)
        ink.to(x0 + 30f, d, top)
        ink.to(x0 + 30f, d, 0f)
        ink.to(x1, d, top * 0.5f, start = true)
        ink.to(x0, d, top * 0.5f)
        stroke(ink, 1f)
    }

    /** The flight down from the front of the terrace, between two low strings. */
    private fun DrawScope.stairs(top: Float) {
        val s0 = TERRACE_R * OCT_S[0]
        val s1 = s0 + 48f
        val w = 30f
        fill.reset()
        fill.to(-w, s0, top, start = true)
        fill.to(w, s0, top)
        fill.to(w, s1, 0f)
        fill.to(-w, s1, 0f)
        fill.close()
        drawPath(fill, WALL_LIGHT)
        ink.reset()
        ink.addPath(fill)
        for (k in 1..6) {
            val f = k / 7f
            ink.to(-w, lerp(s0, s1, f), top * (1f - f), start = true)
            ink.to(w, lerp(s0, s1, f), top * (1f - f))
        }
        val string = 9f * rise[0]
        fill.reset()
        for (side in -1..1 step 2) {
            val x = side * (w + 7f)
            fill.to(x, s0, top, start = true)
            fill.to(x, s0, top + string)
            fill.to(x, s1 + 4f, string)
            fill.to(x, s1 + 4f, 0f)
            fill.close()
        }
        if (rise[0] > MIN_RISE) drawPath(fill, WALL_DARK)
        ink.addPath(fill)
        stroke(ink, 1f)
    }

    /** Rails and balusters along face [j] into [ink]; a [gap] leaves the middle open for stairs. */
    private fun railing(j: Int, r: Float, z0: Float, z1: Float, posts: Int, gap: Boolean) {
        val mid = lerp(z0, z1, 0.45f)
        if (gap) {
            ink.across(j, r, z1, 0f, 0.35f)
            ink.across(j, r, z1, 0.65f, 1f)
            ink.across(j, r, mid, 0f, 0.35f)
            ink.across(j, r, mid, 0.65f, 1f)
            ink.upright(j, r, 0.35f, z0, z1 + (z1 - z0) * 0.3f)
            ink.upright(j, r, 0.65f, z0, z1 + (z1 - z0) * 0.3f)
        } else {
            ink.across(j, r, z1)
            ink.across(j, r, mid)
        }
        for (p in 0..posts) {
            val a = p.toFloat() / posts
            if (gap && a > 0.35f && a < 0.65f) continue
            val newel = p == 0 || p == posts
            ink.upright(j, r, a, z0, if (newel) z1 + (z1 - z0) * 0.3f else z1)
        }
    }

    // --- Lift 1: ground hall -------------------------------------------------

    private fun DrawScope.hall() {
        val standing = rise[1] > MIN_RISE
        val podiumTop = z(1, 7f)
        val wallTop = z(1, 80f)
        val postTop = z(1, 76f)

        ink.reset()
        if (standing) {
            for (j in 0..7) {
                if (!faces(j, PODIUM_R)) continue
                fill.reset()
                fill.face(j, PODIUM_R, base[1], podiumTop)
                drawPath(fill, lerpColor(WALL_DARK, WALL_LIGHT, FACE_LIGHT[j]))
                ink.face(j, PODIUM_R, base[1], podiumTop)
            }
        }
        fill.reset()
        fill.octagon(PODIUM_R, podiumTop)
        drawPath(fill, DECK)
        ink.octagon(PODIUM_R, podiumTop)
        stroke(ink)

        if (standing) {
            walls(1, HALL_WALL_R, 7f, 80f, bays = 3, doorFace = 7)

            // The colonnade: posts a third of a face apart, tied by a beam under the eave and a low rail.
            ink.reset()
            faint.reset()
            for (j in 0..7) {
                if (!faces(j, HALL_POST_R)) continue
                for (p in 0..3) ink.upright(j, HALL_POST_R, p / 3f, podiumTop, postTop)
                faint.across(j, HALL_POST_R, lerp(podiumTop, postTop, 0.90f))
                faint.across(j, HALL_POST_R, lerp(podiumTop, postTop, 0.82f))
                if (j != 7) faint.across(j, HALL_POST_R, lerp(podiumTop, postTop, 0.15f))
            }
            stroke(faint, 1f)
            stroke(ink, 2.1f)
        }

        roof(
            rIn = 78f,
            zIn = base[2],
            rOut = 150f,
            zEave = z(1, 72f),
            flare = 13f * rise[1],
            reach = 0.08f,
            curve = 2.0f,
            edge = 5f * rise[1],
            up = rise[1],
        )
    }

    // --- Storeys, the top one last --------------------------------------------------

    private fun DrawScope.storey(index: Int) {
        val k = 2 + index
        val wallR = 64f - 4f * index
        val deckR = wallR + 12f
        val floor = base[k]
        val standing = rise[k] > MIN_RISE
        val railTop = z(k, 12f)

        // The balcony deck, sitting on the ring the roof below leaves open.
        fill.reset()
        fill.octagon(deckR, floor)
        drawPath(fill, DECK)
        stroke(fill, 1f)

        if (standing) {
            // The far side of the railing goes down first; the walls then stand in front of it.
            ink.reset()
            for (j in 0..7) if (!faces(j, deckR)) railing(j, deckR, floor, railTop, posts = 4, gap = false)
            stroke(ink, 0.9f)

            walls(k, wallR, 0f, 56f, bays = 3, doorFace = -1)

            fill.reset()
            ink.reset()
            for (j in 0..7) {
                if (!faces(j, deckR)) continue
                fill.face(j, deckR, floor, railTop)
                railing(j, deckR, floor, railTop, posts = 4, gap = false)
            }
            drawPath(fill, WALL_LIGHT.copy(alpha = 0.6f))
            stroke(ink, 1f)
        }

        if (k < top) {
            roof(
                rIn = wallR + 9f,
                zIn = base[k + 1],
                rOut = wallR + 34f,
                zEave = z(k, 50f),
                flare = 8f * rise[k],
                reach = 0.08f,
                curve = 1.8f,
                edge = 4f * rise[k],
                up = rise[k],
            )
        } else {
            // The top roof runs all the way in to the foot of the spire, steep and hollow.
            roof(
                rIn = 9f,
                zIn = base[k + 1],
                rOut = wallR + 36f,
                zEave = z(k, 50f),
                flare = 9f * rise[k],
                reach = 0.10f,
                curve = 2.5f,
                edge = 4f * rise[k],
                up = rise[k],
            )
        }
    }

    /**
     * The walls of lift [k] between [lo] and [hi]: every face turned to the
     * viewer, split into [bays] by posts, a latticed window in each bay under
     * a lintel band, and a door in the middle bay of [doorFace].
     */
    private fun DrawScope.walls(k: Int, r: Float, lo: Float, hi: Float, bays: Int, doorFace: Int) {
        val z0 = z(k, lo)
        val z1 = z(k, hi)
        val lintel = lerp(z0, z1, 0.86f)
        val sill = lerp(z0, z1, 0.20f)
        val head = lerp(z0, z1, 0.78f)
        cut.reset()
        ink.reset()
        faint.reset()
        for (j in 0..7) {
            if (!faces(j, r)) continue
            fill.reset()
            fill.face(j, r, z0, z1)
            drawPath(fill, lerpColor(WALL_DARK, WALL_LIGHT, FACE_LIGHT[j]))
            ink.face(j, r, z0, z1)
            ink.across(j, r, lintel)
            for (bay in 0 until bays) {
                val a0 = (bay + 0.16f) / bays
                val a1 = (bay + 0.84f) / bays
                val foot = if (j == doorFace && bay == bays / 2) z0 else sill
                cut.pane(j, r, a0, a1, foot, head)
                ink.pane(j, r, a0, a1, foot, head)
                if (bay > 0) ink.upright(j, r, bay.toFloat() / bays, z0, lintel)
                faint.upright(j, r, lerp(a0, a1, 1f / 3f), foot, head)
                faint.upright(j, r, lerp(a0, a1, 2f / 3f), foot, head)
                faint.across(j, r, lerp(foot, head, 0.66f), a0, a1)
            }
        }
        drawPath(cut, OPENING)
        stroke(faint, 0.9f, WALL_LIGHT)
        stroke(ink, 1.1f)
    }

    // --- Roofs ---------------------------------------------------------------

    /**
     * Puts in [px], [py] the point of the current roof on face [j], [q] of the
     * way along it and [t] of the way down from the ring to the eave, dropped
     * by [drop].
     *
     * The slope is hollow, steep at the ring and nearly level at the eave, and
     * toward the corners the eave turns up and runs out to a point.
     */
    private fun roofAt(j: Int, q: Float, t: Float, drop: Float = 0f) {
        val off = abs(2f * q - 1f)
        val corner = off * off * off * t * t * t
        val r = lerp(roofIn, roofOut, t) * (1f + roofReach * corner)
        val x = r * lerp(OCT_X[j and 7], OCT_X[(j + 1) and 7], q)
        val s = r * lerp(OCT_S[j and 7], OCT_S[(j + 1) and 7], q)
        val height = roofEave + (roofTop - roofEave) * (1f - t).pow(roofCurve) + roofFlare * corner - drop
        px = sx(x, s)
        py = sy(s, height)
    }

    /** Puts face [j] of the current roof in [fill]: along the ring, down one hip, back along the eave, up the other. */
    private fun roofFace(j: Int) {
        fill.reset()
        roofAt(j, 0f, 0f)
        fill.moveTo(px, py)
        roofAt(j, 1f, 0f)
        fill.lineTo(px, py)
        for (step in 1..HIP_STEPS) {
            roofAt(j, 1f, step.toFloat() / HIP_STEPS)
            fill.lineTo(px, py)
        }
        for (step in EAVE_STEPS - 1 downTo 0) {
            roofAt(j, step.toFloat() / EAVE_STEPS, 1f)
            fill.lineTo(px, py)
        }
        for (step in HIP_STEPS - 1 downTo 1) {
            roofAt(j, 0f, step.toFloat() / HIP_STEPS)
            fill.lineTo(px, py)
        }
        fill.close()
    }

    /** The lines on face [j]: a second one just up from the eave into [ink], three ribs down the slope into [faint]. */
    private fun roofLines(j: Int) {
        for (step in 0..EAVE_STEPS) {
            roofAt(j, step.toFloat() / EAVE_STEPS, 0.86f)
            if (step == 0) ink.moveTo(px, py) else ink.lineTo(px, py)
        }
        for (rib in 1..3) {
            for (step in 0..HIP_STEPS) {
                roofAt(j, rib / 4f, step.toFloat() / HIP_STEPS)
                if (step == 0) faint.moveTo(px, py) else faint.lineTo(px, py)
            }
        }
    }

    /**
     * A skirt of roof from a ring of radius [rIn] at [zIn] out and down to an
     * eave of radius [rOut] at [zEave]. Its corners lift by [flare] and run out
     * by [reach] of the radius; [curve] is how hollow the slope is, [edge] the
     * thickness of the eave seen from the front, and [up] how far its lift has
     * risen.
     *
     * Lying flat, the eight faces are one ring of paper under one light: one
     * fill and two strokes. Standing, every face is drawn far to near in its
     * own light, and none is culled: a hollow roof seen from above shows the
     * level foot of its far faces even where their steep tops are turned away,
     * and whatever should not show is painted over by the near faces and by
     * the storey that stands in the ring. The far three are inked before the
     * near five go down, so their lines are hidden too.
     */
    private fun DrawScope.roof(
        rIn: Float,
        zIn: Float,
        rOut: Float,
        zEave: Float,
        flare: Float,
        reach: Float,
        curve: Float,
        edge: Float,
        up: Float,
    ) {
        roofIn = rIn
        roofOut = rOut
        roofTop = zIn
        roofEave = zEave
        roofFlare = flare
        roofReach = reach
        roofCurve = curve

        ink.reset()
        faint.reset()
        if (up <= MIN_RISE) {
            cut.reset()
            for (j in 0..7) {
                roofFace(j)
                cut.addPath(fill)
                roofLines(j)
            }
            drawPath(cut, ROOF_FLAT)
            ink.addPath(cut)
            stroke(faint, 0.8f, LINE.copy(alpha = 0.45f))
            stroke(ink)
            return
        }

        val lit = up.clamped(0f, 1f)
        for (n in 0..7) {
            val j = FAR_TO_NEAR[n]
            roofFace(j)
            drawPath(fill, lerpColor(ROOF_FLAT, lerpColor(ROOF_DARK, ROOF_LIGHT, FACE_LIGHT[j]), lit))
            ink.addPath(fill)
            roofLines(j)

            // The thickness of the eave, on the faces that show their front edge.
            if (edge > 0.2f && faces(j, rOut)) {
                cut.reset()
                for (step in 0..EAVE_STEPS) {
                    roofAt(j, step.toFloat() / EAVE_STEPS, 1f)
                    if (step == 0) cut.moveTo(px, py) else cut.lineTo(px, py)
                }
                for (step in EAVE_STEPS downTo 0) {
                    roofAt(j, step.toFloat() / EAVE_STEPS, 1f, drop = edge)
                    cut.lineTo(px, py)
                }
                cut.close()
                drawPath(cut, ROOF_DARK)
                ink.addPath(cut)
            }
            if (n == 2 || n == 7) {
                stroke(faint, 0.8f, LINE.copy(alpha = 0.45f))
                stroke(ink)
                ink.reset()
                faint.reset()
            }
        }
    }

    // --- Last lift: spire -------------------------------------------------------

    private fun DrawScope.spire() {
        val foot = base[mast]
        val mastTop = z(mast, 40f)
        val axisX = sx(0f, 0f)

        // Chains from the mast to the eight corners of the top roof, which is still the current one.
        ink.reset()
        val chainY = sy(0f, z(mast, 64f))
        for (k in 0..7) {
            roofAt(k, 0f, 1f)
            val sag = abs(py - chainY) * 0.24f * rise[top].clamped(0f, 1f)
            ink.moveTo(axisX, chainY)
            ink.quadraticTo((axisX + px) * 0.5f, (chainY + py) * 0.5f + sag, px, py)
        }
        stroke(ink, 0.85f, LINE.copy(alpha = 0.8f))

        // Collar over the ring the roof leaves open.
        fill.reset()
        fill.octagon(13f, foot)
        drawPath(fill, ROOF_LIGHT)
        stroke(fill, 1f)

        // The ringed mast, tapering.
        fill.reset()
        fill.to(-9f, 0f, foot, start = true)
        fill.to(9f, 0f, foot)
        fill.to(5f, 0f, mastTop)
        fill.to(-5f, 0f, mastTop)
        fill.close()
        drawPath(fill, SPIRE)
        ink.reset()
        ink.addPath(fill)
        for (ring in 1..7) {
            val f = ring / 8f
            val r = lerp(9f, 5f, f)
            val height = lerp(foot, mastTop, f)
            ink.to(-r, 0f, height, start = true)
            ink.quadraticTo(axisX, sy(r * 1.5f, height), sx(r, 0f), sy(0f, height))
        }

        // Rod, with three crossbars shortening toward the tip.
        ink.to(0f, 0f, mastTop, start = true)
        ink.to(0f, 0f, z(mast, 110f))
        ink.to(-7f, 0f, z(mast, 74f), start = true)
        ink.to(7f, 0f, z(mast, 74f))
        ink.to(-5f, 0f, z(mast, 87f), start = true)
        ink.to(5f, 0f, z(mast, 87f))
        ink.to(-3f, 0f, z(mast, 99f), start = true)
        ink.to(3f, 0f, z(mast, 99f))
        stroke(ink, 1.1f)

        // The ball where the chains gather.
        val ballY = sy(0f, z(mast, 52f))
        val rx = sx(6.5f, 0f) - axisX
        val ry = max((sy(0f, z(mast, 43f)) - sy(0f, z(mast, 61f))) * 0.5f, rx * 0.5f)
        drawOval(SPIRE, Offset(axisX - rx, ballY - ry), Size(rx * 2f, ry * 2f))
        drawOval(LINE, Offset(axisX - rx, ballY - ry), Size(rx * 2f, ry * 2f), style = Stroke(hair))
    }
}

private const val HIP_STEPS = 5
private const val EAVE_STEPS = 8
