package com.example.kmp_odd_scroll.showcase.gallerywall

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.example.kmp_odd_scroll.showcase.internal.clamped
import com.example.kmp_odd_scroll.showcase.internal.lerp
import com.example.kmp_odd_scroll.showcase.internal.smoothstep
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Stepping inside a painting. The painting is a grid of points, each pushed
 * back along its own line of sight to the depth the scene in it would have —
 * so from where the eye stands it still looks exactly like the flat painting,
 * and only once the eye moves does the depth show. The eye then goes in: a
 * little forward and a little narrower, the near ground swelling past it while
 * the distance barely moves, and the room behind fades to dark.
 *
 * Where the depth jumps — a figure against the sky — the sheet comes apart,
 * and what shows through the gap is a second sheet behind it: the same
 * painting with the near things painted out of it, a little way in from each
 * of their edges, in the colours round them, and laid at the depth of what is
 * behind them. So as a figure slides across the distance the distance goes on
 * behind it, a shade darker in its shadow, instead of opening onto the dark.
 *
 * Inside, a drag turns the head and leans it the same way, so the near things
 * slide across the far ones, and a scroll walks in or back; the head drifts a
 * little on its own so the depth never quite goes still.
 *
 * Each sheet is one textured triangle mesh drawn once a frame, its arrays
 * written in place — nothing is allocated after the painting is prepared.
 */

/** Cells along the painting's longer side. */
private const val CELLS = 128

/** The guess at the depth is worked out on cells this many pixels across, then each averaged. */
private const val SAMPLE = 4

/**
 * Nearest and farthest, against the painting's own distance: the ground at
 * the bottom of a landscape comes to half of it, the sky goes back four times.
 */
private const val NEAR = 0.5f
private const val FAR = 4f

/** Into the scene, in the painting's distance, and how much narrower the eye's view gets on the way. */
private const val FORWARD = 0.3f
private const val ZOOM = 1.2f

/** Walking in and back with the wheel, past [FORWARD]. */
private const val WANDER_IN = 0.08f
private const val WANDER_BACK = -0.25f

/** How far the head turns, and moves with it, in radians and the painting's distance per radian. */
internal const val YAW_MAX = 0.55f
internal const val PITCH_MAX = 0.3f
private const val LEAN = 0.25f

/** The head drifting on its own: how far it moves, and how far it turns. */
private const val DRIFT_MOVE = 0.025f
private const val DRIFT_TURN = 0.02f

/** Nothing nearer the eye than this is drawn, in the painting's distance. */
private const val CLIP = 0.04f

/** A cell of the front sheet whose far corner is this much further than its near one comes apart. */
private const val TEAR = 1.18f

/** How far in under a near thing's edge, in cells, the sheet behind reaches with what is behind it. */
private const val BEHIND_REACH = 10

/** Nearer than the sheet behind by this much, in nearness, and a point is painted out of it. */
private const val GAP = 0.06f

/** Cells along the longer side of the colour worked out where the near things are painted out; kept so every point indexes as a short. */
private const val FILL_CELLS = 192

/** The longer side of the painting behind, in pixels. */
private const val BEHIND_PX = 1024

/** How much darker the painting behind shows where it was painted in, as if in the near thing's shadow. */
private const val SHADOW = 0.2f

/** How much darker the far distance falls, into the dark round it. */
private const val FOG = 0.3f

/** The sheet's own edges fade over this much of it once it has depth. */
private const val EDGE = 0.035f

private val STEP_EASE = CubicBezierEasing(0.45f, 0f, 0.15f, 1f)

/**
 * The painting being stepped into: its depth, guessed once per painting, the
 * painting behind its near things worked out from it, and the two sheets
 * drawn through the eye each frame.
 */
internal class StepInside {
    private var forImage: ImageBitmap? = null
    private var forDepth: ImageBitmap? = null

    private val front = Sheet()
    private val behind = Sheet()
    private val eye = Eye()

    /**
     * Works out the depth of [image], from [depth] if there is a map of it
     * — white near, black far — or guessed from the picture if not, and the
     * painting behind its near things. Cheap after the first call for the
     * same painting.
     */
    fun prepare(image: ImageBitmap, depth: ImageBitmap?) {
        if (image === forImage && depth === forDepth) return
        forImage = image
        forDepth = depth
        val (cols, rows) = grid(image, CELLS)
        val w = cols + 1
        val h = rows + 1
        val count = w * h

        val near = if (depth != null) mapped(depth, w, h) else estimatePaintingDepth(image, w, h)
        val back = behindOf(near, w, h)
        front.build(cols, rows, image, near, FloatArray(count) { 1f }, TEAR)
        val shade = FloatArray(count) { 1f - SHADOW * smoothstep(GAP * 0.5f, GAP * 2f, near[it] - back[it]) }
        behind.build(cols, rows, paintedOut(image, near, back, cols, rows), back, shade, Float.POSITIVE_INFINITY)
    }

    /**
     * The painting, lying flat on screen from [left], [top] to [right],
     * [bottom] as seen through a focal length of [focal] about [cx], [cy],
     * [progress] of the way stepped into, linear in time; the head turned
     * [yaw] and [pitch], walked [wander] on, [time] seconds inside.
     */
    fun draw(
        scope: DrawScope,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        cx: Float,
        cy: Float,
        focal: Float,
        progress: Float,
        yaw: Float,
        pitch: Float,
        wander: Float,
        time: Float,
    ) {
        val e = progress.clamped(0f, 1f)
        val eased = STEP_EASE.transform(e)
        // Depth first, then the eye goes in, then it can look round.
        val depth = smoothstep(0.05f, 0.7f, e)
        val free = smoothstep(0.55f, 1f, e)

        val turn = yaw * free + DRIFT_TURN * sin(time * 0.21f) * free
        val tilt = pitch * free + DRIFT_TURN * 0.6f * sin(time * 0.17f + 1f) * free
        eye.left = left
        eye.top = top
        eye.right = right
        eye.bottom = bottom
        eye.cx = cx
        eye.cy = cy
        eye.focal = focal
        eye.depth = depth
        eye.forward = FORWARD * eased + wander.clamped(WANDER_BACK, WANDER_IN) * free
        eye.f = focal * lerp(1f, ZOOM, eased)
        eye.x = (LEAN * yaw + DRIFT_MOVE * sin(time * 0.31f)) * free
        eye.y = (LEAN * 0.5f * pitch + DRIFT_MOVE * 0.6f * sin(time * 0.27f + 2f)) * free
        eye.cosYaw = cos(turn)
        eye.sinYaw = sin(turn)
        eye.cosPitch = cos(tilt)
        eye.sinPitch = sin(tilt)

        // Flat, the front sheet covers the one behind exactly.
        if (depth > 0f) behind.draw(scope, eye)
        front.draw(scope, eye)
    }
}

/** Where the eye is and how it is turned, for this frame. */
private class Eye {
    var left = 0f
    var top = 0f
    var right = 0f
    var bottom = 0f
    var cx = 0f
    var cy = 0f
    var focal = 1f
    var f = 1f
    var depth = 0f
    var forward = 0f
    var x = 0f
    var y = 0f
    var cosYaw = 1f
    var sinYaw = 0f
    var cosPitch = 1f
    var sinPitch = 0f
}

/** One sheet of the painting: a grid of points each at its own depth, textured with a picture of it. */
private class Sheet {
    private var cols = 0
    private var rows = 0

    /** Nearness at each point of the grid, 0 farthest to 1 nearest. */
    private var near = FloatArray(0)

    /** Each point's distance against the painting's at full depth. */
    private var reach = FloatArray(0)

    /** How far in from the sheet's nearest edge, for fading it. */
    private var rim = FloatArray(0)

    /** Darkened by this much at full depth, beside the fog. */
    private var shade = FloatArray(0)

    /** A cell this much further at one corner than another comes apart. */
    private var tear = Float.POSITIVE_INFINITY

    /** Each point's distance for this frame, before the eye. */
    private var z = FloatArray(0)

    /** Drawn behind the eye, this frame. */
    private var hidden = BooleanArray(0)

    private var vertices: Vertices? = null
    private val paint = Paint().apply { filterQuality = FilterQuality.Medium }

    fun build(cols: Int, rows: Int, texture: ImageBitmap, near: FloatArray, shade: FloatArray, tear: Float) {
        this.cols = cols
        this.rows = rows
        val w = cols + 1
        val h = rows + 1
        val count = w * h
        this.near = near
        this.shade = shade
        this.tear = tear
        reach = FloatArray(count) { 1f / lerp(1f / FAR, 1f / NEAR, near[it]) }
        rim = FloatArray(count) { n ->
            val u = (n % w) / cols.toFloat()
            val v = (n / w) / rows.toFloat()
            smoothstep(0f, EDGE, min(min(u, 1f - u), min(v, 1f - v)))
        }
        z = FloatArray(count)
        hidden = BooleanArray(count)

        // Fixed from here on but for the arrays written in place each frame.
        val positions = List(count) { Offset.Zero }
        val coordinates = List(count) { n ->
            Offset((n % w) * texture.width / cols.toFloat(), (n / w) * texture.height / rows.toFloat())
        }
        val colors = List(count) { Color.White }
        vertices = Vertices(VertexMode.Triangles, positions, coordinates, colors, cellIndices(cols, rows) { true })
        paint.shader = ImageShader(texture, TileMode.Clamp, TileMode.Clamp)
    }

    fun draw(scope: DrawScope, eye: Eye) {
        val vertices = vertices ?: return
        val depth = eye.depth
        val w = cols + 1
        val positions = vertices.positions
        val colors = vertices.colors
        for (n in z.indices) {
            val u = (n % w) / cols.toFloat()
            val v = (n / w) / rows.toFloat()
            // On the painting's plane, a distance of 1 away, then pushed back along the line of sight.
            val k = 1f / lerp(1f, 1f / reach[n], depth)
            z[n] = k
            val x = (lerp(eye.left, eye.right, u) - eye.cx) / eye.focal * k - eye.x
            val y = (lerp(eye.top, eye.bottom, v) - eye.cy) / eye.focal * k - eye.y
            val d = k - eye.forward
            // Turned, then tilted.
            val x1 = eye.cosYaw * x - eye.sinYaw * d
            val z1 = eye.sinYaw * x + eye.cosYaw * d
            val y2 = eye.cosPitch * y - eye.sinPitch * z1
            val z2 = eye.sinPitch * y + eye.cosPitch * z1
            val behind = z2 < CLIP
            hidden[n] = behind
            val zz = if (behind) CLIP else z2
            positions[2 * n] = eye.cx + eye.f * x1 / zz
            positions[2 * n + 1] = eye.cy + eye.f * y2 / zz

            val alpha = lerp(1f, rim[n], depth)
            val dark = (1f - FOG * (1f - near[n])) * shade[n]
            colors[n] = argb(alpha, lerp(1f, dark, depth))
        }

        // Cells behind the eye, or across a jump in depth, are left out.
        val indices = vertices.indices
        var t = 0
        for (j in 0 until rows) {
            for (i in 0 until cols) {
                val a = j * w + i
                val b = a + 1
                val c = a + w + 1
                val d = a + w
                for (tri in 0..1) {
                    val p = a
                    val q = if (tri == 0) b else c
                    val r = if (tri == 0) c else d
                    val lo = min(z[p], min(z[q], z[r]))
                    val hi = max(z[p], max(z[q], z[r]))
                    val drop = hidden[p] || hidden[q] || hidden[r] || hi > lo * tear
                    indices[t] = if (drop) 0 else p.toShort()
                    indices[t + 1] = if (drop) 0 else q.toShort()
                    indices[t + 2] = if (drop) 0 else r.toShort()
                    t += 3
                }
            }
        }

        scope.drawIntoCanvas { it.drawVertices(vertices, BlendMode.Modulate, paint) }
    }

    /** Opaque white darkened to [shade], at [alpha]. */
    private fun argb(alpha: Float, shade: Float): Int {
        val a = (alpha.clamped(0f, 1f) * 255f).roundToInt()
        val s = (shade.clamped(0f, 1f) * 255f).roundToInt()
        return (a shl 24) or (s shl 16) or (s shl 8) or s
    }
}

/** Columns and rows of a grid over [image] with [cells] along its longer side. */
private fun grid(image: ImageBitmap, cells: Int): Pair<Int, Int> {
    val landscape = image.width >= image.height
    val cols = if (landscape) cells else max(8, (cells * image.width / image.height.toFloat()).roundToInt())
    val rows = if (landscape) max(8, (cells * image.height / image.width.toFloat()).roundToInt()) else cells
    return cols to rows
}

/** Two triangles for each cell of a [cols] by [rows] grid that [keep] says to. */
private inline fun cellIndices(cols: Int, rows: Int, keep: (Int) -> Boolean): List<Int> {
    val w = cols + 1
    val indices = ArrayList<Int>(cols * rows * 6)
    for (j in 0 until rows) {
        for (i in 0 until cols) {
            val a = j * w + i
            if (!keep(a)) continue
            indices += a; indices += a + 1; indices += a + w + 1
            indices += a; indices += a + w + 1; indices += a + w
        }
    }
    return indices
}

/** How far stepped in the room has faded to dark behind the painting. */
internal fun veil(progress: Float): Float = smoothstep(0f, 0.45f, progress)

/** Turns the head by a drag of [dx], [dy] pixels through a focal length of [focal]. */
internal fun lookBy(dx: Float, dy: Float, focal: Float, yaw: Float, pitch: Float, out: FloatArray) {
    out[0] = (yaw + dx / focal).clamped(-YAW_MAX, YAW_MAX)
    out[1] = (pitch + dy / focal).clamped(-PITCH_MAX, PITCH_MAX)
}

/** Walks in or back by a scroll of [by], within reach. */
internal fun wanderBy(by: Float, wander: Float): Float = (wander - by * 0.04f).clamped(WANDER_BACK, WANDER_IN)

// --- behind the near things ----------------------------------------------------

/**
 * The nearness of what is behind each point of a [w] by [h] grid: the
 * farthest thing within [BEHIND_REACH] cells, softened, and never nearer
 * than the point itself. A thing smaller than the reach is gone from it
 * altogether; a larger one keeps its middle and loses only a band in from
 * its edge — which is all that a turn of the head can uncover.
 */
private fun behindOf(near: FloatArray, w: Int, h: Int): FloatArray {
    val across = FloatArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            var lo = 1f
            for (o in max(0, x - BEHIND_REACH)..min(w - 1, x + BEHIND_REACH)) lo = min(lo, near[y * w + o])
            across[y * w + x] = lo
        }
    }
    val out = FloatArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            var lo = 1f
            for (o in max(0, y - BEHIND_REACH)..min(h - 1, y + BEHIND_REACH)) lo = min(lo, across[o * w + x])
            out[y * w + x] = lo
        }
    }
    repeat(2) { blurred(out, w, h, 2) }
    for (n in out.indices) out[n] = min(out[n], near[n])
    return out
}

/** Box-blurs [values] on a [w] by [h] grid in place, [radius] each way, across then down. */
private fun blurred(values: FloatArray, w: Int, h: Int, radius: Int) {
    val line = FloatArray(max(w, h))
    for (y in 0 until h) {
        for (x in 0 until w) {
            var sum = 0f
            var n = 0
            for (o in max(0, x - radius)..min(w - 1, x + radius)) { sum += values[y * w + o]; n++ }
            line[x] = sum / n
        }
        for (x in 0 until w) values[y * w + x] = line[x]
    }
    for (x in 0 until w) {
        for (y in 0 until h) {
            var sum = 0f
            var n = 0
            for (o in max(0, y - radius)..min(h - 1, y + radius)) { sum += values[o * w + x]; n++ }
            line[y] = sum / n
        }
        for (y in 0 until h) values[y * w + x] = line[y]
    }
}

/**
 * [image] with its near things painted out wherever [near] — on a grid of
 * [cols] by [rows] cells — stands in front of [back]: the colour round each
 * such patch grown into it from its edge, a ring at a time, each point the
 * mean of those already painted beside it, so what is behind runs on in
 * under the near thing the way it would go on behind it.
 *
 * Worked out on a grid of its own, finer than the depth's, and laid over a
 * copy of the painting as one mesh of coloured points — the only way common
 * code can paint a picture pixel by pixel.
 */
private fun paintedOut(image: ImageBitmap, near: FloatArray, back: FloatArray, cols: Int, rows: Int): ImageBitmap {
    val (fc, fr) = grid(image, FILL_CELLS)
    val fw = fc + 1
    val fh = fr + 1
    val count = fw * fh

    // In front of what is behind it, read off the depth's grid; and one point
    // more all round, so the outline's own blend of the two goes with it.
    val inFront = BooleanArray(count) { n ->
        val gx = (n % fw) * cols / fc.toFloat()
        val gy = (n / fw) * rows / fr.toFloat()
        bilinear(near, cols + 1, gx, gy) - bilinear(back, cols + 1, gx, gy) > GAP
    }
    val out = BooleanArray(count)
    for (n in 0 until count) {
        if (!inFront[n]) continue
        val x = n % fw
        val y = n / fw
        for (oy in -1..1) for (ox in -1..1) {
            val px = x + ox
            val py = y + oy
            if (px in 0 until fw && py in 0 until fh) out[py * fw + px] = true
        }
    }
    if (out.none { it }) return image

    val (r, g, b) = sampled(image, fw, fh)
    grownInto(out, r, g, b, fw, fh)

    val scale = min(1f, BEHIND_PX / max(image.width, image.height).toFloat())
    val bw = max(1, (image.width * scale).roundToInt())
    val bh = max(1, (image.height * scale).roundToInt())
    val positions = List(count) { n -> Offset((n % fw) * bw / fc.toFloat(), (n / fw) * bh / fr.toFloat()) }
    val colors = List(count) { Color(r[it].clamped(0f, 1f), g[it].clamped(0f, 1f), b[it].clamped(0f, 1f)) }
    val indices = cellIndices(fc, fr) { a -> out[a] || out[a + 1] || out[a + fw] || out[a + fw + 1] }
    val painted = ImageBitmap(bw, bh)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(painted), Size(bw.toFloat(), bh.toFloat())) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(bw, bh),
            filterQuality = FilterQuality.High,
        )
        // Plain coloured points: white paint, so it makes no difference however the platform mixes the two.
        val paint = Paint().apply { color = Color.White }
        drawIntoCanvas {
            it.drawVertices(Vertices(VertexMode.Triangles, positions, positions, colors, indices), BlendMode.Modulate, paint)
        }
    }
    return painted
}

/** [values] on a grid [w] points across, read between its points at [x], [y]. */
private fun bilinear(values: FloatArray, w: Int, x: Float, y: Float): Float {
    val h = values.size / w
    val x0 = x.toInt().coerceIn(0, w - 1)
    val y0 = y.toInt().coerceIn(0, h - 1)
    val x1 = min(x0 + 1, w - 1)
    val y1 = min(y0 + 1, h - 1)
    val fx = (x - x0).clamped(0f, 1f)
    val fy = (y - y0).clamped(0f, 1f)
    val top = lerp(values[y0 * w + x0], values[y0 * w + x1], fx)
    val bottom = lerp(values[y1 * w + x0], values[y1 * w + x1], fx)
    return lerp(top, bottom, fy)
}

/**
 * Paints in the points of a [w] by [h] grid marked in [hole], in place in
 * [r], [g], [b], from its edge inwards a ring at a time — each point the mean
 * of the points beside it painted already, the ones square on counting more
 * than the corners — then softens what was painted once, so the rings do not
 * show.
 */
private fun grownInto(hole: BooleanArray, r: FloatArray, g: FloatArray, b: FloatArray, w: Int, h: Int) {
    val count = w * h
    val known = BooleanArray(count) { !hole[it] }
    val queued = BooleanArray(count)
    var ring = IntArray(count)
    var next = IntArray(count)
    var size = 0
    for (n in 0 until count) {
        if (hole[n] && besideKnown(n, w, h, known)) { ring[size++] = n; queued[n] = true }
    }
    val tr = FloatArray(count)
    val tg = FloatArray(count)
    val tb = FloatArray(count)
    while (size > 0) {
        for (k in 0 until size) {
            val n = ring[k]
            val x = n % w
            val y = n / w
            var sr = 0f
            var sg = 0f
            var sb = 0f
            var weight = 0f
            for (oy in -1..1) for (ox in -1..1) {
                if (ox == 0 && oy == 0) continue
                val px = x + ox
                val py = y + oy
                if (px !in 0 until w || py !in 0 until h) continue
                val o = py * w + px
                if (!known[o]) continue
                val k2 = if (ox == 0 || oy == 0) 1f else 0.7f
                sr += k2 * r[o]; sg += k2 * g[o]; sb += k2 * b[o]; weight += k2
            }
            tr[n] = sr / weight
            tg[n] = sg / weight
            tb[n] = sb / weight
        }
        var nextSize = 0
        for (k in 0 until size) {
            val n = ring[k]
            r[n] = tr[n]; g[n] = tg[n]; b[n] = tb[n]
            known[n] = true
        }
        for (k in 0 until size) {
            val n = ring[k]
            val x = n % w
            val y = n / w
            for (oy in -1..1) for (ox in -1..1) {
                val px = x + ox
                val py = y + oy
                if (px !in 0 until w || py !in 0 until h) continue
                val o = py * w + px
                if (hole[o] && !queued[o]) { next[nextSize++] = o; queued[o] = true }
            }
        }
        val swap = ring
        ring = next
        next = swap
        size = nextSize
    }

    // Softened once, within what was painted.
    for (n in 0 until count) {
        if (!hole[n]) continue
        val x = n % w
        val y = n / w
        var sr = 0f
        var sg = 0f
        var sb = 0f
        var c = 0
        for (oy in -1..1) for (ox in -1..1) {
            val px = x + ox
            val py = y + oy
            if (px !in 0 until w || py !in 0 until h) continue
            val o = py * w + px
            sr += r[o]; sg += g[o]; sb += b[o]; c++
        }
        tr[n] = sr / c; tg[n] = sg / c; tb[n] = sb / c
    }
    for (n in 0 until count) if (hole[n]) { r[n] = tr[n]; g[n] = tg[n]; b[n] = tb[n] }
}

/** Whether a point of a [w] by [h] grid has a [known] one beside it. */
private fun besideKnown(n: Int, w: Int, h: Int, known: BooleanArray): Boolean {
    val x = n % w
    val y = n / w
    for (oy in -1..1) for (ox in -1..1) {
        val px = x + ox
        val py = y + oy
        if (px in 0 until w && py in 0 until h && known[py * w + px]) return true
    }
    return false
}

// --- the depth -----------------------------------------------------------------

/** [image] read back on a [w] by [h] grid, [SAMPLE] pixels to a cell: red, green, blue and the spread of light in each. */
private fun sampled(image: ImageBitmap, w: Int, h: Int): Array<FloatArray> {
    val sw = w * SAMPLE
    val sh = h * SAMPLE
    val small = ImageBitmap(sw, sh)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(small), Size(sw.toFloat(), sh.toFloat())) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(sw, sh),
            filterQuality = FilterQuality.High,
        )
    }
    val pixels = IntArray(sw * sh)
    small.readPixels(pixels)
    val r = FloatArray(w * h)
    val g = FloatArray(w * h)
    val b = FloatArray(w * h)
    val spread = FloatArray(w * h)
    val n = (SAMPLE * SAMPLE).toFloat()
    for (cy in 0 until h) {
        for (cx in 0 until w) {
            var sr = 0f
            var sg = 0f
            var sb = 0f
            var sl = 0f
            var sll = 0f
            for (y in 0 until SAMPLE) {
                for (x in 0 until SAMPLE) {
                    val p = pixels[(cy * SAMPLE + y) * sw + cx * SAMPLE + x]
                    val pr = ((p shr 16) and 0xFF) / 255f
                    val pg = ((p shr 8) and 0xFF) / 255f
                    val pb = (p and 0xFF) / 255f
                    val l = 0.299f * pr + 0.587f * pg + 0.114f * pb
                    sr += pr; sg += pg; sb += pb; sl += l; sll += l * l
                }
            }
            val c = cy * w + cx
            r[c] = sr / n
            g[c] = sg / n
            b[c] = sb / n
            spread[c] = sqrt(max(0f, sll / n - (sl / n) * (sl / n)))
        }
    }
    return arrayOf(r, g, b, spread)
}

/** A depth map read as it is, white near, stretched to fill 0 to 1. */
private fun mapped(depth: ImageBitmap, w: Int, h: Int): FloatArray {
    val (r, g, b) = sampled(depth, w, h)
    val out = FloatArray(w * h) { 0.299f * r[it] + 0.587f * g[it] + 0.114f * b[it] }
    return stretched(out)
}

/**
 * A guess at how near each part of a painting is, from how a picture of a
 * place is usually made: the ground runs away up the canvas from its bottom
 * edge, the distance is hazier — paler, greyer, softer, bluer — than what is
 * near, and the sky is furthest of all. Then smoothed within each patch of
 * colour, so the depth jumps where the colour does, along the outlines of
 * things, and holds still across them.
 */
internal fun estimatePaintingDepth(image: ImageBitmap, w: Int, h: Int): FloatArray {
    val (r, g, b, spread) = sampled(image, w, h)
    val count = w * h
    val sat = FloatArray(count) {
        val hi = max(r[it], max(g[it], b[it]))
        val lo = min(r[it], min(g[it], b[it]))
        if (hi > 1e-3f) (hi - lo) / hi else 0f
    }
    val vivid = stretched(sat.copyOf())
    val busy = stretched(spread.copyOf())

    val out = FloatArray(count)
    for (n in 0 until count) {
        val y = (n / w) / (h - 1f)
        val ground = y.pow(1.4f)
        val clear = 0.5f * vivid[n] + 0.5f * busy[n]
        val light = 0.299f * r[n] + 0.587f * g[n] + 0.114f * b[n]
        val blue = b[n] - (r[n] + g[n]) / 2f
        val sky = smoothstep(0.02f, 0.15f, blue) * smoothstep(0.4f, 0.75f, light) * (1f - smoothstep(0.35f, 0.7f, y))
        out[n] = (0.62f * ground + 0.38f * clear) * (1f - 0.85f * sky)
    }

    // Smoothed across like colours and not across an outline.
    var from = out
    var to = FloatArray(count)
    repeat(8) {
        for (cy in 0 until h) {
            for (cx in 0 until w) {
                val c = cy * w + cx
                var sum = 2f * from[c]
                var weight = 2f
                for (oy in -1..1) {
                    for (ox in -1..1) {
                        if (ox == 0 && oy == 0) continue
                        val x = cx + ox
                        val y = cy + oy
                        if (x < 0 || y < 0 || x >= w || y >= h) continue
                        val o = y * w + x
                        val dr = r[o] - r[c]
                        val dg = g[o] - g[c]
                        val db = b[o] - b[c]
                        val k = exp(-(dr * dr + dg * dg + db * db) / 0.015f)
                        sum += k * from[o]
                        weight += k
                    }
                }
                to[c] = sum / weight
            }
        }
        val swap = from
        from = to
        to = swap
    }
    return stretched(from)
}

/** Stretches [values] in place so the lowest and highest fiftieth fill 0 to 1. */
private fun stretched(values: FloatArray): FloatArray {
    val sorted = values.copyOf().also { it.sort() }
    val lo = sorted[(sorted.size * 0.02f).toInt()]
    val hi = sorted[((sorted.size - 1) * 0.98f).toInt()]
    val span = max(hi - lo, 1e-4f)
    for (n in values.indices) values[n] = ((values[n] - lo) / span).clamped(0f, 1f)
    return values
}
