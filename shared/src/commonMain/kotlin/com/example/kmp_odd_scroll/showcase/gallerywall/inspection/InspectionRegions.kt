package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal suspend fun inspectionRegions(
    w: Int, h: Int, pixels: IntArray, depth: FloatArray,
): List<InspectionRegion> {
    val regions = mutableListOf<InspectionRegion>()
    val taken = BooleanArray(pixels.size)
    if (pixels.all { it == pixels[0] } && depth.all { it == depth[0] }) return emptyList()

    // For an unannotated image, build a minimum-spanning forest over its
    // colour and depth discontinuities. The finer pass retains small objects
    // that disappear when adjoining patches are joined in the coarse pass.
    val edges = gridEdges(w, h, pixels, depth)
    val proposals = mutableListOf<RegionProposal>()
    for (scale in intArrayOf(490, 1600)) {
        yield()
        val forest = RegionForest(pixels.size)
        for (edge in edges) {
            val origin = (edge and 0x7ffff) ushr 1
            val neighbour = origin + if (edge and 1 == 0) 1 else w
            forest.join(origin, neighbour, edge ushr 19, scale)
        }
        val roots = IntArray(pixels.size) { forest.root(it) }
        val sizes = IntArray(pixels.size)
        val border = IntArray(pixels.size)
        val depthSum = FloatArray(pixels.size)
        for (i in roots.indices) {
            val r = roots[i]
            sizes[r]++
            depthSum[r] += depth[i]
            if (i < w || i >= pixels.size - w || i % w == 0 || i % w == w - 1) border[r]++
        }
        for (r in roots.indices) {
            val area = sizes[r].toFloat() / pixels.size
            if (area !in 0.006f..0.58f) continue
            val edgeContact = border[r] / sqrt(sizes[r].toFloat())
            val score = sqrt(area.coerceAtMost(0.17f)) * (0.55f + depthSum[r] / sizes[r]) /
                (1f + edgeContact * edgeContact)
            proposals += RegionProposal(roots, r, score)
        }
    }
    for (proposal in proposals.sortedByDescending { it.score }) {
        val mask = BooleanArray(pixels.size) { proposal.roots[it] == proposal.id }
        appendRegion(w, h, mask, regions, taken)
        if (regions.size == 5) break
    }
    return regions
}

private class RegionProposal(val roots: IntArray, val id: Int, val score: Float)

private fun appendRegion(
    w: Int, h: Int, mask: BooleanArray,
    out: MutableList<InspectionRegion>, taken: BooleanArray,
) {
    var x0 = w
    var y0 = h
    var x1 = 0
    var y1 = 0
    var area = 0
    var overlapping = 0
    for (i in mask.indices) if (mask[i]) {
        if (taken[i]) {
            overlapping++
            mask[i] = false
            continue
        }
        val x = i % w
        val y = i / w
        x0 = min(x0, x)
        y0 = min(y0, y)
        x1 = max(x1, x + 1)
        y1 = max(y1, y + 1)
        area++
    }
    if (area == 0 || overlapping > area * 0.12f) return
    val width = x1 - x0
    val coverage = BooleanArray(width * (y1 - y0)) { mask[(y0 + it / width) * w + x0 + it % width] }
    out += InspectionRegion(Rect(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat()), width, coverage)
    for (i in mask.indices) if (mask[i]) taken[i] = true
}

/** A tightly cropped silhouette in its original pixels, positioned in the shared analysis grid. */
internal class InspectionRegion(val bounds: Rect, private val width: Int, private val coverage: BooleanArray) {
    private val height = coverage.size / width
    private val stepX = bounds.width / width
    private val stepY = bounds.height / height

    val outline = inspectionOutline(coverage, width, height).apply {
        transform(Matrix().apply {
            this[0, 0] = stepX
            this[1, 1] = stepY
            this[3, 0] = bounds.left
            this[3, 1] = bounds.top
        })
    }

    fun contains(point: Offset): Boolean {
        if (!bounds.contains(point)) return false
        val x = ((point.x - bounds.left) / stepX).toInt().coerceAtMost(width - 1)
        val y = ((point.y - bounds.top) / stepY).toInt().coerceAtMost(height - 1)
        return coverage[y * width + x]
    }

    // A leader ends on visible paint even when the centre falls inside a hole.
    val anchor: Offset = run {
        val center = bounds.center
        var nearest = Float.MAX_VALUE
        var anchor = center
        for (i in coverage.indices) if (coverage[i]) {
            val point = Offset(bounds.left + (i % width + 0.5f) * stepX, bounds.top + (i / width + 0.5f) * stepY)
            val distance = (point - center).getDistanceSquared()
            if (distance < nearest) {
                nearest = distance
                anchor = point
            }
        }
        anchor
    }
}

/** Trace each nonzero ID at the mask's native resolution, including holes and occluded parts. */
internal suspend fun authoredInspectionRegions(
    labels: ByteArray, width: Int, height: Int, analysisWidth: Int, analysisHeight: Int,
): List<InspectionRegion> {
    val left = IntArray(256) { width }
    val top = IntArray(256) { height }
    val right = IntArray(256)
    val bottom = IntArray(256)
    for (y in 0 until height) for (x in 0 until width) {
        val id = labels[y * width + x].toInt() and 255
        if (id == 0) continue
        left[id] = min(left[id], x)
        top[id] = min(top[id], y)
        right[id] = max(right[id], x + 1)
        bottom[id] = max(bottom[id], y + 1)
    }

    val sx = analysisWidth.toFloat() / width
    val sy = analysisHeight.toFloat() / height
    val regions = mutableListOf<InspectionRegion>()
    for (id in 1..255) {
        val cropWidth = right[id] - left[id]
        if (cropWidth <= 0) continue
        yield()
        val coverage = BooleanArray(cropWidth * (bottom[id] - top[id])) { index ->
            val x = left[id] + index % cropWidth
            val y = top[id] + index / cropWidth
            labels[y * width + x] == id.toByte()
        }
        val bounds = Rect(left[id] * sx, top[id] * sy, right[id] * sx, bottom[id] * sy)
        regions += InspectionRegion(bounds, cropWidth, coverage)
    }
    return regions
}

private fun gridEdges(w: Int, h: Int, pixels: IntArray, depth: FloatArray): IntArray {
    val smoothed = IntArray(pixels.size)
    // A light Gaussian removes isolated canvas grain before measuring the
    // boundary strength. The sample colours used for display remain intact.
    for (y in 0 until h) for (x in 0 until w) {
        var r = 0
        var g = 0
        var b = 0
        for (dy in -1..1) for (dx in -1..1) {
            val index = (y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)
            val p = pixels[index]
            val weight = (2 - abs(dx)) * (2 - abs(dy))
            r += ((p ushr 16) and 255) * weight
            g += ((p ushr 8) and 255) * weight
            b += (p and 255) * weight
        }
        smoothed[y * w + x] = ((r / 16) shl 16) or ((g / 16) shl 8) or (b / 16)
    }
    val edges = IntArray((w - 1) * h + (h - 1) * w)
    var at = 0
    for (i in pixels.indices) {
        fun edge(j: Int, direction: Int) {
            val a = smoothed[i]
            val b = smoothed[j]
            val distance = abs((a ushr 16) - (b ushr 16)) +
                abs(((a ushr 8) and 255) - ((b ushr 8) and 255)) + abs((a and 255) - (b and 255)) +
                (abs(depth[i] - depth[j]) * 500f).toInt()
            edges[at++] = (distance shl 19) or (i shl 1) or direction
        }
        if (i % w < w - 1) edge(i + 1, 0)
        if (i / w < h - 1) edge(i + w, 1)
    }
    edges.sort()
    return edges
}

private class RegionForest(count: Int) {
    private val parent = IntArray(count) { it }
    private val size = IntArray(count) { 1 }
    private val contrast = IntArray(count)

    fun root(i: Int): Int {
        var r = i
        while (parent[r] != r) {
            parent[r] = parent[parent[r]]
            r = parent[r]
        }
        return r
    }

    fun join(i: Int, j: Int, weight: Int, scale: Int) {
        val a = root(i)
        val b = root(j)
        if (a == b || weight > contrast[a] + scale / size[a] || weight > contrast[b] + scale / size[b]) return
        val head = if (size[a] >= size[b]) a else b
        val tail = if (head == a) b else a
        parent[tail] = head
        size[head] += size[tail]
        contrast[head] = weight
    }
}

/** Follows directed edges of exposed cells into closed contours, including
 * internal holes. The quadratic joins round the pixel staircase at subcell scale. */
internal fun inspectionOutline(mask: BooleanArray, width: Int, height: Int): Path {
    val stride = width + 1
    val edges = IntArray(stride * (height + 1))
    for (i in mask.indices) {
        if (!mask[i]) continue
        val x = i % width
        val y = i / width
        val corner = y * stride + x
        if (y == 0 || !mask[i - width]) edges[corner] = edges[corner] or 1
        if (x == width - 1 || !mask[i + 1]) edges[corner + 1] = edges[corner + 1] or 2
        if (y == height - 1 || !mask[i + width]) edges[corner + stride + 1] = edges[corner + stride + 1] or 4
        if (x == 0 || !mask[i - 1]) edges[corner + stride] = edges[corner + stride] or 8
    }
    val path = Path().apply { fillType = PathFillType.EvenOdd }
    val steps = intArrayOf(1, stride, -1, -stride)
    for (start in edges.indices) while (edges[start] != 0) {
        var point = start
        var direction = edges[start].countTrailingZeroBits()
        val corners = mutableListOf<Int>()
        do {
            edges[point] = edges[point] and (1 shl direction).inv()
            point += steps[direction]
            if (point == start) break
            val exits = edges[point]
            // At diagonal contact keep the occupied cell on the right.
            val right = (direction + 1) and 3
            val next = when {
                exits and (1 shl right) != 0 -> right
                exits and (1 shl direction) != 0 -> direction
                else -> (direction + 3) and 3
            }
            if (next != direction) corners.add(point)
            direction = next
        } while (true)
        corners.add(start)
        var previous = corners.last()
        for ((index, vertex) in corners.withIndex()) {
            val next = corners[(index + 1) % corners.size]
            val x = (vertex % stride).toFloat()
            val y = (vertex / stride).toFloat()
            val enterX = x + (previous % stride - x).coerceIn(-0.45f, 0.45f)
            val enterY = y + (previous / stride - y).coerceIn(-0.45f, 0.45f)
            val leaveX = x + (next % stride - x).coerceIn(-0.45f, 0.45f)
            val leaveY = y + (next / stride - y).coerceIn(-0.45f, 0.45f)
            if (index == 0) path.moveTo(enterX, enterY) else path.lineTo(enterX, enterY)
            path.quadraticTo(x, y, leaveX, leaveY)
            previous = vertex
        }
        path.close()
    }
    return path
}
