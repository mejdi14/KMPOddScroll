package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal class BlueprintStroke(val path: Path, val score: Float, val ruling: Path?) {
    val measure = PathMeasure().apply { setPath(path, false) }
}

/** Link thin gradient ridges in paint and depth, following the local tangent through junctions. */
internal fun traceBlueprint(a: PaintingAnalysis): List<BlueprintStroke> {
    val width = a.width
    val height = a.height
    val count = a.pixels.size
    if (width < 5 || height < 5) return emptyList()

    fun smoothed(input: FloatArray): FloatArray {
        val horizontal = FloatArray(count)
        val output = FloatArray(count)
        for (y in 0 until height) for (x in 0 until width) {
            val i = y * width + x
            horizontal[i] = 0.5f * input[i] + 0.25f * (input[max(0, x - 1) + y * width] + input[min(width - 1, x + 1) + y * width])
        }
        for (y in 0 until height) for (x in 0 until width) {
            val i = y * width + x
            output[i] = 0.5f * horizontal[i] + 0.25f * (horizontal[max(0, y - 1) * width + x] + horizontal[min(height - 1, y + 1) * width + x])
        }
        return output
    }
    val luma = FloatArray(count)
    val chroma = FloatArray(count)
    for (i in a.pixels.indices) {
        val c = a.pixels[i]
        val r = (c ushr 16 and 255).toFloat()
        val g = (c ushr 8 and 255).toFloat()
        val b = (c and 255).toFloat()
        luma[i] = 0.23f * r + 0.65f * g + 0.12f * b
        chroma[i] = 0.40f * r - 0.25f * b - 0.15f * g
    }
    val gx = FloatArray(count)
    val gy = FloatArray(count)
    val magnitude = FloatArray(count)
    val channels = listOf(smoothed(luma), smoothed(chroma), smoothed(FloatArray(count) { a.depth[it] * 140f }))
    for (channel in channels) {
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val i = y * width + x
            val dx = (2f * (channel[i + 1] - channel[i - 1]) + channel[i + 1 - width] -
                channel[i - 1 - width] + channel[i + 1 + width] - channel[i - 1 + width]) * 0.25f
            val dy = (2f * (channel[i + width] - channel[i - width]) + channel[i + width - 1] -
                channel[i - width - 1] + channel[i + width + 1] - channel[i - width + 1]) * 0.25f
            val length = dx * dx + dy * dy
            if (length > magnitude[i]) {
                magnitude[i] = length
                gx[i] = dx
                gy[i] = dy
            }
        }
    }
    val ridge = BooleanArray(count)
    val histogram = IntArray(256)
    for (i in magnitude.indices) {
        magnitude[i] = sqrt(magnitude[i])
        histogram[magnitude[i].toInt().coerceIn(0, 255)]++
    }
    var below = 0
    val threshold = histogram.indices.first {
        below += histogram[it]
        below >= count * 0.74f
    }.toFloat().coerceAtLeast(2f)
    for (y in 2 until height - 2) for (x in 2 until width - 2) {
        val i = y * width + x
        val length = magnitude[i]
        if (length < threshold * 0.44f) continue
        val dx = gx[i]
        val dy = gy[i]
        val alongNormal = when {
            abs(dx) * 0.4142f > abs(dy) -> 1
            abs(dy) * 0.4142f > abs(dx) -> width
            dx * dy > 0f -> width + 1
            else -> width - 1
        }
        ridge[i] = length >= magnitude[i - alongNormal] && length > magnitude[i + alongNormal]
    }

    fun walk(start: Int, orientation: Float): MutableList<Int> {
        var at = start
        var tx = -orientation * gy[at] / magnitude[at]
        var ty = orientation * gx[at] / magnitude[at]
        val result = mutableListOf<Int>()
        while (true) {
            var best = -1
            var score = -1f
            var nextX = 0f
            var nextY = 0f
            val x = at % width
            val y = at / width
            // A one-sample gap commonly occurs where the gradient turns between octants.
            for (radius in 1..2) {
                for (dy in -radius..radius) for (dx in -radius..radius) {
                    if (max(abs(dx), abs(dy)) != radius) continue
                    val next = (y + dy) * width + (x + dx)
                    if (!ridge[next]) continue
                    val scale = 1f / sqrt((dx * dx + dy * dy).toFloat())
                    val ux = dx * scale
                    val uy = dy * scale
                    val forward = ux * tx + uy * ty
                    val tangent = abs(uy * gx[next] - ux * gy[next]) / magnitude[next]
                    if (forward < (if (radius == 1) 0.2f else 0.83f) || tangent < 0.42f) continue
                    val candidate = forward + tangent * 0.52f + 0.12f * min(1f, magnitude[next] / (threshold * 3f))
                    if (candidate > score) {
                        best = next
                        score = candidate
                        nextX = ux
                        nextY = uy
                    }
                }
                if (best >= 0) break
            }
            if (best < 0) break
            tx = nextX
            ty = nextY
            at = best
            ridge[at] = false
            result += at
        }
        return result
    }

    val lines = mutableListOf<BlueprintStroke>()
    val seeds = ridge.indices.filter { ridge[it] && magnitude[it] >= threshold }.sortedByDescending { magnitude[it] }
    for (seed in seeds) {
        if (!ridge[seed]) continue
        ridge[seed] = false
        val left = walk(seed, -1f)
        val right = walk(seed, 1f)
        if (left.size + right.size < 4) continue
        val indices = left.asReversed() + seed + right
        // Smooth subpixel samples to keep diagonals and brush edges clean at display size.
        val points = indices.mapIndexed { k, index ->
            val previous = indices[max(0, k - 1)]
            val next = indices[min(indices.lastIndex, k + 1)]
            Offset(0.5f + (previous % width + 2 * (index % width) + next % width) * 0.25f,
                0.5f + (previous / width + 2 * (index / width) + next / width) * 0.25f)
        }
        val path = Path()
        val first = points.first()
        val last = points.last()
        path.moveTo(first.x, first.y)
        for (i in 1 until points.lastIndex) {
            val p = points[i]
            val mid = (p + points[i + 1]) * 0.5f
            path.quadraticTo(p.x, p.y, mid.x, mid.y)
        }
        path.lineTo(last.x, last.y)
        val meanStrength = indices.sumOf { magnitude[it].toDouble() }.toFloat() / indices.size
        val score = sqrt(points.size.toFloat()) * (0.65f + min(3f, meanStrength / threshold))
        val span = last - first
        val length = span.getDistance()
        // A long, genuinely straight run can support a faint extended construction line.
        val ruling = if (length > min(width, height) * 0.12f && points.all {
            abs((it.x - first.x) * span.y - (it.y - first.y) * span.x) < length * 1.25f
        }) Path().apply {
            val extension = span / length * min(12f, length * 0.2f)
            moveTo(first.x - extension.x, first.y - extension.y)
            lineTo(last.x + extension.x, last.y + extension.y)
        } else null
        lines += BlueprintStroke(path, score, ruling)
    }
    return lines.sortedByDescending { it.score }.take(740)
}



/** Short strokes describe shaded volumes between the outlines. The local paint gradient turns the nib. */
private fun formHatching(a: PaintingAnalysis): Path {
    val path = Path()
    val width = a.width
    val height = a.height
    val luminance = FloatArray(a.pixels.size) {
        val c = a.pixels[it]
        ((c ushr 16 and 255) * 0.21f + (c ushr 8 and 255) * 0.72f + (c and 255) * 0.07f) / 255f
    }
    val mean = luminance.average().toFloat()
    for (row in 1 until height - 2 step 3) for (col in 1 until width - 2 step 3) {
        val x = col + ((row / 3) % 2)
        val i = row * width + x
        val dark = ((mean - luminance[i]) * 2.6f + 0.15f).coerceIn(0f, 1f)
        // Stagger the screen so a shaded patch reads as tone before its individual strokes resolve.
        val phase = ((col * 19 + row * 43 + col * row) % 101) / 101f
        if (dark < 0.13f + 0.63f * phase) continue
        val dx = luminance[i + 1] - luminance[i - 1]
        val dy = luminance[i + width] - luminance[i - width]
        val baseAngle = -0.67f
        val slope = (dx - dy).coerceIn(-0.38f, 0.38f)
        val angle = baseAngle + slope
        val length = 0.32f + 1.2f * dark
        val ux = cos(angle) * length
        val uy = sin(angle) * length
        path.moveTo(x + 0.5f - ux, row + 0.5f - uy)
        path.lineTo(x + 0.5f + ux, row + 0.5f + uy)
    }
    return path
}

private val cyanotype = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    0.053f, 0.171f, 0.025f, 0f, 11f,
    0.069f, 0.222f, 0.033f, 0f, 30f,
    0.071f, 0.232f, 0.034f, 0f, 41f,
    0f, 0f, 0f, 1f, 0f,
)))

internal class BlueprintInspection(private val image: ImageBitmap, private val a: PaintingAnalysis) : InspectionEffect {
    private val lines = a.drawing
    private val hatching = formHatching(a)
    private val inks = listOf(Color(0xFFEDE7CF), Color(0xFFD2DFD7), Color(0xFF97B8BB))
    private val strokes = listOf(0.66f, 0.46f, 0.35f).map { Stroke(width = it, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    private val bundles = List(3) { Path() }
    private val rulings = Path()
    private var lastProgress = -1f
    private var lastCount = -1

    private fun prepare(p: Float, count: Int) {
        if (p == lastProgress && count == lastCount) return
        lastProgress = p
        lastCount = count
        bundles.forEach { it.reset() }
        rulings.reset()
        for (i in 0 until count) {
            val line = lines[i]
            val rank = i.toFloat() / lines.size
            val group = if (rank < 0.08f) 0 else if (rank < 0.36f) 1 else 2
            // The draughtsman's long marks start first. Successive shorter marks overlap in time.
            val start = 0.06f + 0.60f * sqrt(rank)
            val reach = ((p - start) / (0.34f - 0.11f * rank)).coerceIn(0f, 1f)
            if (reach >= 1f) {
                bundles[group].addPath(line.path)
                line.ruling?.let { rulings.addPath(it) }
            } else if (reach > 0f) {
                line.measure.getSegment(0f, line.measure.length * ease(reach), bundles[group], true)
            }
        }
    }

    override fun draw(scope: DrawScope, frame: Rect, progress: Float, controls: InspectionControls) = with(scope) {
        val plate = ease(progress / 0.78f)
        if (plate < 1f) paintImage(image, frame)
        if (progress <= 0f) return@with
        withTransform({
            translate(frame.left, frame.top)
            scale(frame.width / image.width, frame.height / image.height, Offset.Zero)
        }) {
            drawImage(image, alpha = plate, colorFilter = cyanotype, filterQuality = FilterQuality.Medium)
        }
        val count = ((0.14f + controls.amount * 0.86f) * lines.size).toInt().coerceIn(0, lines.size)
        prepare(progress, count)
        clipRect(frame.left, frame.top, frame.right, frame.bottom) {
            withTransform({
                translate(frame.left, frame.top)
                scale(frame.width / a.width, frame.height / a.height, Offset.Zero)
            }) {
                val hatchReveal = ease((progress - 0.56f) / 0.44f) * controls.amount
                drawPath(hatching, Color(0xFFB0CACC), alpha = 0.38f * hatchReveal, style = Stroke(width = 0.29f, cap = StrokeCap.Round))
                drawPath(rulings, Color(0xFFB9B593), alpha = 0.48f * plate, style = Stroke(width = 0.27f))
                for (i in bundles.indices) drawPath(bundles[i], inks[i], alpha = plate, style = strokes[i])
            }
        }
    }
}
