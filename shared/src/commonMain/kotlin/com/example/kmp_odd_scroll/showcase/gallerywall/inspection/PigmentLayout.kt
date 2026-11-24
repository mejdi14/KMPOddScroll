package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Tangent circles give each colour a specimen with an area proportional to its presence in the painting. */
internal fun packPigments(weights: FloatArray, aspect: Float): List<Rect> {
    val centers = mutableListOf(Offset.Zero)
    val radii = weights.map { sqrt(it) }
    val gap = 0.065f
    fun bounds(i: Int, p: Offset) = Rect(p.x - radii[i], p.y - radii[i], p.x + radii[i], p.y + radii[i])
    var extent = bounds(0, Offset.Zero)
    fun union(a: Rect, b: Rect) = Rect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom))
    for (i in 1 until weights.size) {
        val candidates = mutableListOf<Offset>()
        for ((j, center) in centers.withIndex()) {
            val d = radii[j] + radii[i] + gap
            candidates.add(center + Offset(d, 0f))
            candidates.add(center + Offset(0f, d))
            candidates.add(center - Offset(d, 0f))
            candidates.add(center - Offset(0f, d))
            for (k in 0 until j) {
                val delta = centers[k] - center
                val distance = delta.getDistance()
                val other = radii[i] + radii[k] + gap
                val along = (d * d - other * other + distance * distance) / (2f * distance)
                if (along * along > d * d) continue
                val direction = delta / distance
                val tangent = center + direction * along
                val normal = Offset(-direction.y, direction.x) * sqrt(max(0f, d * d - along * along))
                candidates.add(tangent + normal)
                candidates.add(tangent - normal)
            }
        }
        val location = candidates.filter { candidate ->
            centers.indices.all { j -> (centers[j] - candidate).getDistance() + 0.001f >= radii[j] + radii[i] + gap }
        }.minBy { candidate ->
            val box = union(extent, bounds(i, candidate))
            max(box.width / aspect, box.height) + 0.035f * (box.width / aspect + box.height) +
                0.008f * candidate.getDistance()
        }
        centers.add(location)
        extent = union(extent, bounds(i, location))
    }
    val scale = min(aspect / extent.width, 1f / extent.height) * 0.95f
    return centers.mapIndexed { i, center ->
        val x = 0.5f + (center.x - extent.center.x) * scale / aspect
        val y = 0.5f + (center.y - extent.center.y) * scale
        val r = radii[i] * scale
        Rect(x - r / aspect, y - r, x + r / aspect, y + r)
    }
}
