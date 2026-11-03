package io.github.mejdi14.oddscroll.gallerywall

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import io.github.mejdi14.oddscroll.internal.clamped
import io.github.mejdi14.oddscroll.internal.smoothstep
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Turning the bare painting over like a sheet of paper, carried over from the
 * paper fold study in the AGSL shader playground. A crease sweeps in from the
 * lower left corner with three rings of ripples settling behind it, while the
 * sheet turns over about an axis that swings from the diagonal to upright,
 * its left side first, until it lies with its back showing.
 *
 * The sheet is a grid worked out on the CPU each frame and drawn as one
 * textured triangle mesh, back to front — one draw, not one per cell — out
 * of one image holding both of its sides. Where the platform has the shader
 * for it, paper grain shows over it as it turns.
 */

/** Cells across and down. */
private const val SEGMENTS = 36

/**
 * The eye's distance from the sheet, in the units the sheet's depth is worked
 * out in — the playground's camera, five units back from a sheet whose depth
 * is not scaled with it.
 */
private const val CAMERA = 5f

/** How wide the crease's band runs, in widths of the sheet. */
private const val CREASE = 0.85f

/** The back of the sheet, multiplied in — a little warmer and duller than the front — at the height of the turn. */
private val BACK = Color(0.99f, 0.96f, 0.91f)

private const val PI_F = PI.toFloat()

/**
 * A painting being turned over. [draw] takes the side facing up at
 * [frontLeft], [srcTop], [srcWidth] by [srcHeight] in [image] and the side
 * facing down the same size at [backLeft], lying flat on screen from [left],
 * [top] to [right], [bottom], [progress] of the way over.
 */
internal class PaperFold {
    private val side = SEGMENTS + 1
    private val sx = FloatArray(side * side)
    private val sy = FloatArray(side * side)
    private val sz = FloatArray(side * side)
    private val depth = FloatArray(SEGMENTS * SEGMENTS)
    private val cells = Array(SEGMENTS * SEGMENTS) { it }
    private val indices = List(SEGMENTS * SEGMENTS * 6) { it }
    private val paint = Paint().apply { filterQuality = FilterQuality.Medium }

    private var plainFor: ImageBitmap? = null
    private var plain: Shader? = null

    fun draw(
        scope: DrawScope,
        image: ImageBitmap,
        frontLeft: Float,
        backLeft: Float,
        srcTop: Float,
        srcWidth: Float,
        srcHeight: Float,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        progress: Float,
    ) {
        val raw = progress.clamped(0f, 1f)
        val turned = ((1f - cos(PI_F * raw)) * 0.5f)
        // The waves rise, hold through the middle of the turn and settle.
        val bell = when {
            raw < 0.4f -> 1f - (1f - raw / 0.4f).pow(4)
            raw <= 0.6f -> 1f
            else -> ((1f - raw) / 0.4f).coerceAtLeast(0f).pow(4)
        }
        val amplitude = 0.5f * bell
        val time = raw * FLIP_MS / 1000f
        // How much of it reads as paper rather than a painting: none at either end.
        val paper = sin(PI_F * raw)

        val w = right - left
        val h = bottom - top
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        for (j in 0..SEGMENTS) {
            val v = j.toFloat() / SEGMENTS
            for (i in 0..SEGMENTS) {
                val u = i.toFloat() / SEGMENTS
                bend(u, v, turned, amplitude, time, j * side + i, w, h, cx, cy)
            }
        }

        // Back to front, as the sheet folds over itself.
        for (c in depth.indices) {
            val a = (c / SEGMENTS) * side + c % SEGMENTS
            depth[c] = sz[a] + sz[a + 1] + sz[a + side] + sz[a + side + 1]
        }
        cells.sortBy { depth[it] }

        val count = cells.size * 6
        val positions = ArrayList<Offset>(count)
        val texture = ArrayList<Offset>(count)
        val colors = ArrayList<Color>(count)
        for (c in cells) {
            val i = c % SEGMENTS
            val j = c / SEGMENTS
            // Lower left, lower right, upper right, upper left: v runs up the sheet.
            val a = j * side + i
            val b = a + 1
            val d = a + side
            val e = d + 1
            val cross = (sx[b] - sx[a]) * (sy[e] - sy[a]) - (sy[b] - sy[a]) * (sx[e] - sx[a])
            // Turned towards the eye, it winds the other way on a y-down screen.
            val back = cross > 0f
            for (k in intArrayOf(a, b, e, a, e, d)) {
                positions += Offset(sx[k], sy[k])
                val u = (k % side).toFloat() / SEGMENTS
                val v = (k / side).toFloat() / SEGMENTS
                val tx = if (back) backLeft + (1f - u) * srcWidth else frontLeft + u * srcWidth
                texture += Offset(tx, srcTop + (1f - v) * srcHeight)
                val shade = (1f - 0.04f * paper + (sz[k] * 0.12f).clamped(-0.08f, 0.12f)).clamped(0f, 1f)
                colors += if (back) {
                    Color(
                        shade * (1f + (BACK.red - 1f) * paper),
                        shade * (1f + (BACK.green - 1f) * paper),
                        shade * (1f + (BACK.blue - 1f) * paper),
                    )
                } else {
                    Color(shade, shade, shade)
                }
            }
        }

        paint.shader = paperShaded?.shader(
            image = image,
            width = srcWidth,
            height = srcHeight,
            grain = paper,
        ) ?: plainShader(image)
        val vertices = Vertices(VertexMode.Triangles, positions, texture, colors, indices)
        scope.drawIntoCanvas { it.drawVertices(vertices, BlendMode.Modulate, paint) }
    }

    /**
     * One point of the sheet, [u] across and [v] up it, [turned] of the way
     * over: the playground's vertex shader, run here. Into [at] of the screen
     * points and their depths.
     */
    private fun bend(
        u: Float,
        v: Float,
        turned: Float,
        amplitude: Float,
        time: Float,
        at: Int,
        w: Float,
        h: Float,
        cx: Float,
        cy: Float,
    ) {
        val x = u - 0.5f
        val y = v - 0.5f

        // The crease, a ring out from the lower left corner, held still at the corner itself.
        val corner = sqrt(u * u + v * v)
        val anchor = smoothstep(0f, 0.35f, corner)
        val off = corner - turned * 2.2f
        val inFold = smoothstep(-CREASE, 0f, off) * (1f - smoothstep(0f, CREASE * 0.7f, off))
        val angle = if (off < 0f) smoothstep(-CREASE * 1.2f, 0f, off) * turned * 0.7f else 0f
        val crease = inFold * anchor * amplitude * 0.7f * (1f + sin(off * 4f + time * 2f) * 0.1f)

        // Behind the crease, the sheet twists a little in its own plane.
        val twist = angle * amplitude * 0.5f
        val mix = smoothstep(0f, -CREASE * 1.5f, off) * anchor * turned * 0.35f
        val tc = cos(twist)
        val ts = sin(twist)
        val px = x + (tc * x + ts * y - x) * mix
        val py = y + (-ts * x + tc * y - y) * mix

        // Rings of ripples settling behind the crease.
        val ring1 = smoothstep(-CREASE * 2.5f, -CREASE * 0.9f, off) * (1f - smoothstep(-CREASE * 0.9f, 0f, off))
        val ring2 = smoothstep(-CREASE * 4f, -CREASE * 2.2f, off) * (1f - smoothstep(-CREASE * 2.2f, -CREASE * 1.3f, off))
        val ring3 = smoothstep(-CREASE * 6f, -CREASE * 3.5f, off) * (1f - smoothstep(-CREASE * 3.5f, -CREASE * 2.5f, off))
        val settling = (
            ring1 * 0.18f * sin(off * 7.5f + time * 2f) +
                ring2 * 0.12f * sin(off * 6f + time * 1.5f + 1.2f) +
                ring3 * 0.08f * sin(off * 4.5f + time * 1.1f + 2.3f)
            ) * amplitude * anchor

        // A slow swell over the whole sheet, and its sag.
        val wave1 = sin(u * PI_F * 3.2f + time * 1.1f) * sin(v * PI_F * 2.8f + time * 0.9f) *
            amplitude * 0.06f * (1f - turned * 0.5f)
        val wave2 = sin((u + v) * PI_F * 2f + time * 0.7f) * sin((u - v) * PI_F * 1.8f + time * 0.5f) *
            amplitude * 0.04f * (1f - turned * 0.4f)
        val drape = sin(v * PI_F * 1.5f) * amplitude * 0.03f * (1f - turned * 0.7f)
        val pz = drape + settling + wave1 + wave2 + crease

        // Over, about an axis leaning from the diagonal to upright, the left side first.
        val flip = smoothstep(u * 0.3f, 0.8f + u * 0.2f, turned)
        val leanX = 0.5f * (1f - flip)
        val norm = 1f / sqrt(leanX * leanX + 1f)
        val ax = leanX * norm
        val ay = norm
        val s = sin(PI_F * flip)
        val c = cos(PI_F * flip)
        val oc = 1f - c
        val rx = (oc * ax * ax + c) * px + oc * ax * ay * py - ay * s * pz
        val ry = oc * ax * ay * px + (oc * ay * ay + c) * py + ax * s * pz
        val rz = ay * s * px - ax * s * py + c * pz

        val k = CAMERA / (CAMERA - rz)
        sx[at] = cx + rx * w * k
        sy[at] = cy - ry * h * k
        sz[at] = rz
    }

    private fun plainShader(image: ImageBitmap): Shader {
        val cached = plain
        if (cached != null && plainFor === image) return cached
        plainFor = image
        return ImageShader(image, TileMode.Clamp, TileMode.Clamp).also { plain = it }
    }
}

/** The platform's paper, or null to turn the painting over plainly. */
private val paperShaded: PaperShader? by lazy { paperShader() }
