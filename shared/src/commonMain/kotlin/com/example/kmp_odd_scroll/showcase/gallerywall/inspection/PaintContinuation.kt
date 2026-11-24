package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The scenery visible behind the cutouts continues under them. Pull known paint up a weighted
 * pyramid, then fill missing samples from the finest level that sees neighbouring scenery.
 * A matching patch of the actual scenery supplies fine brush variation in the exposed area.
 */
internal fun continueScenery(a: PaintingAnalysis, known: BooleanArray): ImageBitmap {
    val base = PaintLevel(a.width, a.height)
    for (n in known.indices) {
        if (!known[n]) continue
        val rgb = a.pixels[n]
        for (c in 0..2) base.samples[4 * n + c] = ((rgb ushr (16 - 8 * c)) and 255).toFloat()
        base.samples[4 * n + 3] = 1f
    }
    val levels = mutableListOf(base)
    while (levels.last().width > 1 || levels.last().height > 1) {
        val fine = levels.last()
        val coarse = PaintLevel((fine.width + 1) / 2, (fine.height + 1) / 2)
        for (y in 0 until coarse.height) for (x in 0 until coarse.width) {
            val o = 4 * (y * coarse.width + x)
            for (dy in 0..1) for (dx in 0..1) {
                val f = 4 * (min(fine.height - 1, 2 * y + dy) * fine.width + min(fine.width - 1, 2 * x + dx))
                val w = fine.samples[f + 3]
                for (c in 0..2) coarse.samples[o + c] += fine.samples[f + c] * w
                coarse.samples[o + 3] += w
            }
            val weight = coarse.samples[o + 3]
            if (weight > 0f) for (c in 0..2) coarse.samples[o + c] /= weight
            coarse.samples[o + 3] = weight * 0.25f
        }
        levels += coarse
    }
    for (i in levels.lastIndex - 1 downTo 0) {
        val fine = levels[i]
        val coarse = levels[i + 1]
        for (y in 0 until fine.height) for (x in 0 until fine.width) {
            val n = (y * fine.width + x) * 4
            val w = fine.samples[n + 3]
            if (w >= 1f) continue
            val u = (x * 0.5f - 0.25f).coerceIn(0f, coarse.width - 1f)
            val v = (y * 0.5f - 0.25f).coerceIn(0f, coarse.height - 1f)
            val x0 = u.toInt()
            val y0 = v.toInt()
            val x1 = min(x0 + 1, coarse.width - 1)
            val y1 = min(y0 + 1, coarse.height - 1)
            for (c in 0..2) {
                val above = mix(coarse[x0, y0, c], coarse[x1, y0, c], u - x0)
                val below = mix(coarse[x0, y1, c], coarse[x1, y1, c], u - x0)
                fine.samples[n + c] = mix(mix(above, below, v - y0), fine.samples[n + c], w)
            }
        }
    }
    addPaintGrain(a, base, known)
    // Two colour-interpolated vertex rows cover the grid. Reuse their storage for every strip.
    val bitmap = ImageBitmap(a.width, a.height)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { color = Color.White }
    val points = List(2 * (a.width + 1)) { Offset((it / 2).toFloat(), (it % 2).toFloat()) }
    val strip = Vertices(VertexMode.TriangleStrip, points, points, List(points.size) { Color.White }, points.indices.toList())
    for (y in 0 until a.height) {
        for (i in points.indices) {
            val n = 4 * (min(a.height - 1, y + i % 2) * a.width + min(a.width - 1, i / 2))
            var argb = 0xff000000.toInt()
            for (c in 0..2) argb = argb or (base.samples[n + c].roundToInt().coerceIn(0, 255) shl (16 - c * 8))
            strip.colors[i] = argb
        }
        canvas.drawVertices(strip, BlendMode.Modulate, paint)
        canvas.translate(0f, 1f)
    }
    return bitmap
}

private class PaintLevel(val width: Int, val height: Int) {
    val samples = FloatArray(width * height * 4)
    operator fun get(x: Int, y: Int, channel: Int) = samples[4 * (y * width + x) + channel]
}

private class Donor(val x: Int, val y: Int, val rgb: FloatArray)

private fun addPaintGrain(a: PaintingAnalysis, base: PaintLevel, known: BooleanArray) {
    val tile = 8
    val donors = mutableListOf<Donor>()
    for (y in 0..a.height - tile step tile) for (x in 0..a.width - tile step tile) {
        val mean = FloatArray(3)
        var count = 0
        for (dy in 0 until tile) for (dx in 0 until tile) {
            val n = (y + dy) * a.width + x + dx
            if (!known[n]) continue
            count++
            for (c in 0..2) mean[c] += base.samples[4 * n + c]
        }
        if (count == tile * tile) {
            for (c in 0..2) mean[c] /= count
            donors += Donor(x, y, mean)
        }
    }
    if (donors.isEmpty()) return
    for (y in 0 until a.height step tile) for (x in 0 until a.width step tile) {
        val cx = min(a.width - 1, x + tile / 2)
        val cy = min(a.height - 1, y + tile / 2)
        val donor = donors.minBy { d ->
            val dx = (cx - d.x) * 0.18f
            val dy = (cy - d.y) * 0.18f
            var cost = dx * dx + dy * dy
            for (c in 0..2) {
                val v = base[cx, cy, c] - d.rgb[c]
                cost += v * v
            }
            cost
        }
        for (dy in 0 until min(tile, a.height - y)) for (dx in 0 until min(tile, a.width - x)) {
            val n = (y + dy) * a.width + x + dx
            if (known[n]) continue
            val edge = min(min(dx, tile - 1 - dx), min(dy, tile - 1 - dy))
            val feather = (edge + 0.35f).coerceAtMost(2f) * 0.32f
            for (c in 0..2) {
                val grain = base[donor.x + dx, donor.y + dy, c] - donor.rgb[c]
                base.samples[4 * n + c] += grain.coerceIn(-27f, 27f) * feather
            }
        }
    }
}
