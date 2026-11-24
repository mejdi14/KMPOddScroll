package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.kmp_odd_scroll.showcase.gallerywall.estimatePaintingDepth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class PaintingAnalysis(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val depth: FloatArray,
    val palette: IntArray,
    val proportions: FloatArray,
    val colorLabels: IntArray,
    val objects: List<InspectionRegion>,
) {
    val pigments by lazy { Pigments(this) }
    val slices by lazy { depthSlices(this) }
    val relief by lazy { PaintingRelief(this) }
    val drawing by lazy { traceBlueprint(this) }
}

/** Pixel centres, contours and masks share one aspect-correct coordinate system. */
internal suspend fun analyzePainting(
    image: ImageBitmap,
    depthMap: ImageBitmap?,
    instanceMap: ImageBitmap? = null,
): PaintingAnalysis = withContext(Dispatchers.Default) {
    val scale = min(1f, 264f / max(image.width, image.height))
    val w = (image.width * scale).roundToInt().coerceAtLeast(1)
    val h = (image.height * scale).roundToInt().coerceAtLeast(1)
    val pixels = sampleImage(image, w, h)
    val depth = if (depthMap != null) {
        val gray = sampleImage(depthMap, w, h)
        FloatArray(gray.size) { (gray[it] and 255) / 255f }
    } else if (pixels.all { it == pixels[0] }) {
        FloatArray(pixels.size) { 0.5f }
    } else {
        // The gallery's edge-aware monocular fallback runs on a coarse grid; its
        // smooth field is sampled at the same pixel centres as the colour data.
        val dw = max(2, w / 3)
        val dh = max(2, h / 3)
        val small = estimatePaintingDepth(image, dw, dh)
        FloatArray(w * h) { index ->
            val u = ((index % w + 0.5f) * dw / w - 0.5f).coerceIn(0f, dw - 1f)
            val v = ((index / w + 0.5f) * dh / h - 0.5f).coerceIn(0f, dh - 1f)
            val x = u.toInt()
            val y = v.toInt()
            val nextX = min(x + 1, dw - 1)
            val nextY = min(y + 1, dh - 1)
            val top = small[y * dw + x] * (1 - (u - x)) + small[y * dw + nextX] * (u - x)
            val bottom = small[nextY * dw + x] * (1 - (u - x)) + small[nextY * dw + nextX] * (u - x)
            top * (1 - (v - y)) + bottom * (v - y)
        }
    }
    yield()
    val pigments = clusterPigments(pixels)
    yield()
    val regions = if (instanceMap != null) {
        val labels = readLabels(instanceMap)
        authoredInspectionRegions(labels, instanceMap.width, instanceMap.height, w, h)
    } else inspectionRegions(w, h, pixels, depth)
    val result = PaintingAnalysis(w, h, pixels, depth, pigments.colors, pigments.weights, pigments.labels, regions)
    // Warm the derived products on the analysis dispatcher. Opening an effect
    // can then allocate its drawing resources without tracing the whole work.
    yield()
    result.relief
    yield()
    result.drawing
    yield()
    result.slices
    yield()
    result.pigments.cloudOrder
    yield()
    result.pigments.families
    result
}

/** Read straight from the resource bitmap; a short reusable strip avoids a second full-size ARGB plane. */
private fun readLabels(image: ImageBitmap): ByteArray {
    val labels = ByteArray(image.width * image.height)
    val rows = 64
    val strip = IntArray(image.width * min(rows, image.height))
    var y = 0
    while (y < image.height) {
        val height = min(rows, image.height - y)
        image.readPixels(strip, 0, y, image.width, height)
        val offset = y * image.width
        for (i in 0 until image.width * height) labels[offset + i] = (strip[i] and 255).toByte()
        y += height
    }
    return labels
}

private fun sampleImage(
    image: ImageBitmap,
    width: Int,
    height: Int,
): IntArray {
    val bitmap = ImageBitmap(width, height)
    Canvas(bitmap).drawImageRect(
        image, IntOffset.Zero, IntSize(image.width, image.height),
        IntOffset.Zero, IntSize(width, height), Paint().apply { filterQuality = FilterQuality.Medium },
    )
    return IntArray(width * height).also { bitmap.readPixels(it) }
}
