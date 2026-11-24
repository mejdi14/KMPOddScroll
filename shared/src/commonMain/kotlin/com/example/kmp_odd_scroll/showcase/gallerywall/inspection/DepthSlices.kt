package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import kotlin.math.max
import kotlin.math.min

internal class DepthSlice(val depth: Float, val bounds: Rect, val outline: Path, val underpaint: ImageBitmap?)

/**
 * Quantiles split the remaining scenery at image-specific depth breaks. Connected subjects stay
 * intact even when their nose and shoulder sit on opposite sides of a break.
 */
internal fun depthSlices(a: PaintingAnalysis): List<DepthSlice> {
    val groundCount = 3
    val owner = IntArray(a.depth.size) { -1 }
    a.objects.forEachIndexed { i, region ->
        for (n in owner.indices) {
            val center = Offset(n % a.width + 0.5f, n / a.width + 0.5f)
            if (region.contains(center)) owner[n] = groundCount + i
        }
    }
    val scenery = FloatArray(owner.count { it == -1 })
    var nScenery = 0
    for (n in owner.indices) if (owner[n] < 0) scenery[nScenery++] = a.depth[n]
    scenery.sort()
    val low = scenery.getOrNull(nScenery / 3) ?: 0.333f
    val high = scenery.getOrNull(nScenery * 2 / 3) ?: 0.667f
    for (n in owner.indices) if (owner[n] < 0) {
        owner[n] = when {
            a.depth[n] < low -> 0
            a.depth[n] < high -> 1
            else -> 2
        }
    }
    val rear = owner.asSequence().filter { it in 0 until groundCount }.minOrNull()
    val layers = ArrayList<DepthSlice>()
    for (layer in 0 until groundCount + a.objects.size) {
        val mask = BooleanArray(owner.size) { owner[it] == layer }
        var count = 0
        var near = 0f
        var left = a.width
        var right = 0
        var top = a.height
        var bottom = 0
        for (n in mask.indices) if (mask[n]) {
            val x = n % a.width
            val y = n / a.width
            count++
            near += a.depth[n]
            left = min(left, x)
            right = max(right, x + 1)
            top = min(top, y)
            bottom = max(bottom, y + 1)
        }
        if (count == 0) continue
        val subject = a.objects.getOrNull(layer - groundCount)
        val bounds = subject?.bounds ?: Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        val path = subject?.outline ?: inspectionOutline(mask, a.width, a.height)
        // Lift a separately resolved subject clear of the terrain it sits on.
        val depth = (near / count + if (subject != null) 0.09f else 0f).coerceIn(0f, 1f)
        if (layer == rear) {
            val underpaint = continueScenery(a, mask)
            val whole = Rect(0f, 0f, a.width.toFloat(), a.height.toFloat())
            layers += DepthSlice(0f, whole, path, underpaint)
        } else {
            layers += DepthSlice(depth, bounds, path, null)
        }
    }
    return layers.sortedBy { it.depth }
}
