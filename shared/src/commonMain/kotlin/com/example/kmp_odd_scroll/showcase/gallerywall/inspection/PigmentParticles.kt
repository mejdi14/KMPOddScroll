package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.example.kmp_odd_scroll.showcase.gallerywall.supportsHardwareMeshes
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Stratified samples retain fine paint colours and avoid a rigid screen-door pattern on rotation. */
internal class Pigments(val analysis: PaintingAnalysis) {
    val ratio = analysis.width.toFloat() / analysis.height
    private val budget = if (supportsHardwareMeshes) 21800f else 6400f
    val rows = min(analysis.height, sqrt(budget / ratio).toInt().coerceAtLeast(1))
    val columns = min(analysis.width, (rows * ratio).toInt().coerceAtLeast(1))
    val count = rows * columns
    val x = FloatArray(count)
    val y = FloatArray(count)
    val sample = IntArray(count)
    val seed = FloatArray(count)

    val cloudOrder by lazy { sample.indices.sortedBy { analysis.depth[sample[it]] } }
    val families by lazy {
        sample.indices.groupBy { analysis.colorLabels[sample[it]] }.mapValues { (_, members) ->
            members.sortedBy {
                val color = analysis.pixels[sample[it]]
                3 * ((color ushr 16) and 255) + 6 * ((color ushr 8) and 255) + (color and 255) + (seed[it] * 45).toInt()
            }
        }
    }

    init {
        var sequence = 9173
        fun noise(): Float {
            sequence = sequence * 1664525 + 1013904223
            return (sequence ushr 8) / 16777216f
        }
        for (i in 0 until count) {
            x[i] = (i % columns + 0.12f + noise() * 0.76f) / columns
            y[i] = (i / columns + 0.12f + noise() * 0.76f) / rows
            sample[i] = (x[i] * analysis.width).toInt() + (y[i] * analysis.height).toInt() * analysis.width
            seed[i] = noise()
        }
    }
}

/** One textured mesh draw for thousands of individually placed, antialiased pigment grains. */
private class GrainMesh(p: Pigments, order: List<Int>) {
    val vertices: Vertices
    private val paint: Paint

    init {
        val size = 24
        val stamp = ImageBitmap(size, size)
        Canvas(stamp).drawCircle(Offset(size / 2f, size / 2f), size / 2f - 1f, Paint().apply { color = Color.White })
        paint = Paint().apply {
            shader = ImageShader(stamp)
            filterQuality = FilterQuality.Low
        }
        val indices = intArrayOf(0, 1, 2, 0, 2, 3)
        val uv = listOf(Offset.Zero, Offset(size.toFloat(), 0f), Offset(size.toFloat(), size.toFloat()), Offset(0f, size.toFloat()))
        vertices = Vertices(VertexMode.Triangles, List(order.size * 4) { Offset.Zero },
            List(order.size * 4) { uv[it % 4] }, List(order.size * 4) { Color(p.analysis.pixels[p.sample[order[it / 4]]]) },
            List(order.size * 6) { it / 6 * 4 + indices[it % 6] })
    }

    fun place(slot: Int, x: Float, y: Float, r: Float) {
        val i = slot * 8
        val a = vertices.positions
        a[i] = x - r
        a[i + 1] = y - r
        a[i + 2] = x + r
        a[i + 3] = y - r
        a[i + 4] = x + r
        a[i + 5] = y + r
        a[i + 6] = x - r
        a[i + 7] = y + r
    }

    fun draw(scope: DrawScope, alpha: Float) {
        paint.alpha = alpha.coerceIn(0f, 1f)
        scope.drawIntoCanvas { it.drawVertices(vertices, BlendMode.Modulate, paint) }
    }
}

// Keep each indexed mesh below 32k vertices, including on older Android graphics drivers.
private class ParticleBatches(p: Pigments, order: List<Int>) {
    private val batchSize = 7500
    private val batches = order.chunked(batchSize).map { GrainMesh(p, it) }

    fun place(i: Int, x: Float, y: Float, r: Float) = batches[i / batchSize].place(i % batchSize, x, y, r)

    fun draw(scope: DrawScope, alpha: Float) {
        for (batch in batches) batch.draw(scope, alpha)
    }
}

internal class PointCloudInspection(private val image: ImageBitmap, private val a: PaintingAnalysis) : InspectionEffect {
    private val p = a.pigments
    private val order = p.cloudOrder
    private val mesh = ParticleBatches(p, order)

    override fun draw(scope: DrawScope, frame: Rect, progress: Float, controls: InspectionControls) = with(scope) {
        val t = ease(progress)
        if (t < 0.72f) paintImage(image, frame, 1f - ease(t / 0.72f))
        if (t == 0f) return@with
        val yaw = controls.yaw * t
        val pitch = controls.pitch * t
        val cy = cos(yaw)
        val sy = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)
        // Fit the orbit's projected envelope, including the closest forward samples.
        val unit = min(frame.width, frame.height)
        val focal = max(frame.width, frame.height) * 1.9f
        val fraction = 0.22f + controls.density * 0.78f
        val radius = frame.width / p.columns * (0.30f + controls.grain * 0.60f) / sqrt(fraction)
        var fit = 1f
        val xRoom = min(frame.center.x, size.width - frame.center.x) * 0.94f
        val yRoom = min(frame.center.y, size.height - frame.center.y) * 0.94f
        // The eight corners bound the projected relief. This also keeps the framing steady while density changes.
        for (corner in 0 until 8) {
            val x = (if (corner and 1 == 0) -0.5f else 0.5f) * frame.width
            val y = (if (corner and 2 == 0) -0.5f else 0.5f) * frame.height
            val z = (if (corner and 4 == 0) -0.5f else 0.5f) * unit * controls.amount * 1.3f * t
            val rz = z * cy - x * sy
            val distance = focal / (focal - (y * sp + rz * cp))
            fit = min(fit, xRoom / (abs((x * cy + z * sy) * distance) + radius))
            fit = min(fit, yRoom / (abs((y * cp - rz * sp) * distance) + radius))
        }
        for (slot in order.indices) {
            val i = order[slot]
            if (p.seed[i] > fraction) {
                mesh.place(slot, 0f, 0f, 0f)
                continue
            }
            val x = (p.x[i] - 0.5f) * frame.width
            val y = (p.y[i] - 0.5f) * frame.height
            val z = (a.depth[p.sample[i]] - 0.5f) * unit * controls.amount * 1.3f * t
            val rx = x * cy + z * sy
            val rz = z * cy - x * sy
            val ry = y * cp - rz * sp
            val distance = focal / (focal - (y * sp + rz * cp))
            mesh.place(slot, frame.center.x + rx * distance * fit, frame.center.y + ry * distance * fit, radius * distance * fit)
        }
        mesh.draw(this, ease(t / 0.42f))
    }
}

internal class ColorInspection(private val image: ImageBitmap, private val a: PaintingAnalysis) : InspectionEffect {
    private val p = a.pigments
    private val order = List(p.count) { it }
    private val mesh = ParticleBatches(p, order)
    private val targetX = FloatArray(p.count)
    private val targetY = FloatArray(p.count)
    private val cells = Array(a.palette.size) { Rect.Zero }
    private val veils = mutableMapOf<Int, ImageBitmap>()

    init {
        val specimens = packPigments(a.proportions, p.ratio)
        for ((bin, members) in p.families) {
            val area = specimens[bin]
            cells[bin] = area
            for ((rank, i) in members.withIndex()) {
                val r = sqrt((rank + 0.5f) / members.size)
                val theta = rank * 2.3999632f + (p.seed[i] - 0.5f) * 0.012f
                targetX[i] = area.center.x + cos(theta) * r * area.width * 0.5f
                targetY[i] = area.center.y + sin(theta) * r * area.height * 0.5f
            }
        }
    }

    private fun veil(bin: Int): ImageBitmap = veils.getOrPut(bin) {
        val bitmap = ImageBitmap(a.width, a.height)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = Color(0xE0171615) }
        for (y in 0 until a.height) {
            var x = 0
            while (x < a.width) {
                val start = x
                while (x < a.width && a.colorLabels[y * a.width + x] != bin) x++
                if (start < x) canvas.drawRect(start.toFloat(), y.toFloat(), x.toFloat(), y + 1f, paint)
                x++
            }
        }
        bitmap
    }

    override fun draw(scope: DrawScope, frame: Rect, progress: Float, controls: InspectionControls) = with(scope) {
        val t = progress * (1f - controls.focus)
        val original = 1f - ease(t / 0.47f)
        if (original > 0f) {
            paintImage(image, frame, original)
            if (controls.selected in cells.indices && controls.focus > 0f) {
                paintImage(veil(controls.selected), frame, controls.focus * original * progress, FilterQuality.Low)
            }
        }
        if (t <= 0f) return@with
        val radius = frame.width / p.columns * 0.45f
        for (i in order.indices) {
            val f = ease((t * 1.25f - p.seed[i] * 0.25f))
            // Neighbouring grains travel together, then settle in order of lightness.
            val arc = 3.2f * f * (1f - f)
            val x = mix(p.x[i], targetX[i], f) + arc * (p.y[i] - 0.5f) * 0.19f
            val y = mix(p.y[i], targetY[i], f) - arc * (0.035f + 0.07f * p.seed[i])
            mesh.place(i, frame.left + x * frame.width, frame.top + y * frame.height, radius * (0.9f + 0.17f * p.seed[i]))
        }
        mesh.draw(this, ease(t / 0.35f))
        // The finest, darkest samples still have a visible edge on a dark mount.
        val rim = ease((t - 0.78f) / 0.22f)
        if (rim > 0f) for (cell in cells) {
            if (cell.isEmpty) continue
            drawCircle(Color(0xFF9E9384), frame.width * cell.width / 2f + radius * 1.6f,
                Offset(frame.left + cell.center.x * frame.width, frame.top + cell.center.y * frame.height),
                alpha = rim * 0.34f, style = Stroke(0.8f * density))
        }
    }

    override fun hit(frame: Rect, point: Offset, controls: InspectionControls): Int? {
        val x = (point.x - frame.left) / frame.width
        val y = (point.y - frame.top) / frame.height
        if (x !in 0f..1f || y !in 0f..1f) return null
        if (controls.selected >= 0) return a.colorLabels[(y * (a.height - 1)).toInt() * a.width + (x * (a.width - 1)).toInt()]
        return cells.indexOfFirst {
            if (it.isEmpty) false else {
                val dx = (x - it.center.x) / (it.width * 0.5f)
                val dy = (y - it.center.y) / (it.height * 0.5f)
                dx * dx + dy * dy <= 1f
            }
        }.takeIf { it >= 0 }
    }
}
