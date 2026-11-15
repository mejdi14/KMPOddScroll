package io.github.mejdi14.oddscroll.gallerywall

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
import io.github.mejdi14.oddscroll.internal.clamped
import io.github.mejdi14.oddscroll.internal.lerp
import io.github.mejdi14.oddscroll.internal.smoothstep
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
 * the distance barely moves, and the room behind fades to dark. Where the
 * depth jumps — a figure against the sky — the sheet tears open and the dark
 * shows through, the way a point cloud of a single picture does.
 *
 * Inside, a drag turns the head and leans it the same way, so the near things
 * slide across the far ones, and a scroll walks in or back; the head drifts a
 * little on its own so the depth never quite goes still.
 *
 * The whole scene is one textured triangle mesh drawn once a frame, its
 * arrays written in place — nothing is allocated after the first frame.
 */

/** Cells along the painting's longer side. */
private const val CELLS = 96

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

/** A cell whose far corner is this much further than its near one tears open. */
private const val TEAR = 1.3f

/** How much darker the far distance falls, into the dark round it. */
private const val FOG = 0.3f

/** The sheet's own edges fade over this much of it once it has depth. */
private const val EDGE = 0.035f

private val STEP_EASE = CubicBezierEasing(0.45f, 0f, 0.15f, 1f)

/**
 * The painting being stepped into: its depth, guessed once per painting, and
 * the mesh drawn through the eye each frame.
 */
internal class StepInside {
    private var forImage: ImageBitmap? = null
    private var forDepth: ImageBitmap? = null

    private var cols = 0
    private var rows = 0

    /** Nearness at each point of the grid, 0 farthest to 1 nearest. */
    private var near = FloatArray(0)

    /** Each point's distance against the painting's at full depth. */
    private var reach = FloatArray(0)

    /** How far in from the sheet's nearest edge, for fading it. */
    private var rim = FloatArray(0)

    /** Each point's distance for this frame, before the eye. */
    private var z = FloatArray(0)

    /** Drawn behind the eye or torn, this frame. */
    private var hidden = BooleanArray(0)

    private var vertices: Vertices? = null
    private val paint = Paint().apply { filterQuality = FilterQuality.Medium }

    /**
     * Works out the depth of [image], from [depth] if there is a map of it
     * — white near, black far — or guessed from the picture if not. Cheap
     * after the first call for the same painting.
     */
    fun prepare(image: ImageBitmap, depth: ImageBitmap?) {
        if (image === forImage && depth === forDepth) return
        forImage = image
        forDepth = depth
        val landscape = image.width >= image.height
        cols = if (landscape) CELLS else max(8, (CELLS * image.width / image.height.toFloat()).roundToInt())
        rows = if (landscape) max(8, (CELLS * image.height / image.width.toFloat()).roundToInt()) else CELLS
        val w = cols + 1
        val h = rows + 1
        val count = w * h

        val guess = if (depth != null) mapped(depth, w, h) else guessed(image, w, h)
        near = guess
        reach = FloatArray(count) { 1f / lerp(1f / FAR, 1f / NEAR, guess[it]) }
        rim = FloatArray(count) { n ->
            val u = (n % w) / cols.toFloat()
            val v = (n / w) / rows.toFloat()
            smoothstep(0f, EDGE, min(min(u, 1f - u), min(v, 1f - v)))
        }
        z = FloatArray(count)
        hidden = BooleanArray(count)

        // Fixed from here on but for the arrays written in place each frame.
        val positions = List(count) { Offset.Zero }
        val texture = List(count) { n ->
            Offset((n % w) * image.width / cols.toFloat(), (n / w) * image.height / rows.toFloat())
        }
        val colors = List(count) { Color.White }
        val indices = ArrayList<Int>(cols * rows * 6)
        for (j in 0 until rows) {
            for (i in 0 until cols) {
                val a = j * w + i
                indices += a; indices += a + 1; indices += a + w + 1
                indices += a; indices += a + w + 1; indices += a + w
            }
        }
        vertices = Vertices(VertexMode.Triangles, positions, texture, colors, indices)
        paint.shader = ImageShader(image, TileMode.Clamp, TileMode.Clamp)
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
        val vertices = vertices ?: return
        val e = progress.clamped(0f, 1f)
        val eased = STEP_EASE.transform(e)
        // Depth first, then the eye goes in, then it can look round.
        val depth = smoothstep(0.05f, 0.7f, e)
        val free = smoothstep(0.55f, 1f, e)

        val forward = FORWARD * eased + wander.clamped(WANDER_BACK, WANDER_IN) * free
        val f = focal * lerp(1f, ZOOM, eased)
        val turn = yaw * free + DRIFT_TURN * sin(time * 0.21f) * free
        val tilt = pitch * free + DRIFT_TURN * 0.6f * sin(time * 0.17f + 1f) * free
        val eyeX = (LEAN * yaw + DRIFT_MOVE * sin(time * 0.31f)) * free
        val eyeY = (LEAN * 0.5f * pitch + DRIFT_MOVE * 0.6f * sin(time * 0.27f + 2f)) * free
        val cosYaw = cos(turn)
        val sinYaw = sin(turn)
        val cosPitch = cos(tilt)
        val sinPitch = sin(tilt)

        val w = cols + 1
        val positions = vertices.positions
        val colors = vertices.colors
        for (n in z.indices) {
            val u = (n % w) / cols.toFloat()
            val v = (n / w) / rows.toFloat()
            // On the painting's plane, a distance of 1 away, then pushed back along the line of sight.
            val k = 1f / lerp(1f, 1f / reach[n], depth)
            z[n] = k
            val x = (lerp(left, right, u) - cx) / focal * k - eyeX
            val y = (lerp(top, bottom, v) - cy) / focal * k - eyeY
            val d = k - forward
            // Turned, then tilted.
            val x1 = cosYaw * x - sinYaw * d
            val z1 = sinYaw * x + cosYaw * d
            val y2 = cosPitch * y - sinPitch * z1
            val z2 = sinPitch * y + cosPitch * z1
            val behind = z2 < CLIP
            hidden[n] = behind
            val zz = if (behind) CLIP else z2
            positions[2 * n] = cx + f * x1 / zz
            positions[2 * n + 1] = cy + f * y2 / zz

            val alpha = lerp(1f, rim[n], depth)
            val shade = 1f - FOG * (1f - near[n]) * depth
            colors[n] = argb(alpha, shade)
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
                    val drop = hidden[p] || hidden[q] || hidden[r] || hi > lo * TEAR
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

/** How far stepped in the room has faded to dark behind the painting. */
internal fun veil(progress: Float): Float = smoothstep(0f, 0.45f, progress)

/** Turns the head by a drag of [dx], [dy] pixels through a focal length of [focal]: the scene follows the finger. */
internal fun lookBy(dx: Float, dy: Float, focal: Float, yaw: Float, pitch: Float, out: FloatArray) {
    out[0] = (yaw - dx / focal).clamped(-YAW_MAX, YAW_MAX)
    out[1] = (pitch - dy / focal).clamped(-PITCH_MAX, PITCH_MAX)
}

/** Walks in or back by a scroll of [by], within reach. */
internal fun wanderBy(by: Float, wander: Float): Float = (wander - by * 0.04f).clamped(WANDER_BACK, WANDER_IN)

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
private fun guessed(image: ImageBitmap, w: Int, h: Int): FloatArray {
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
