package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import kotlinx.coroutines.yield
import kotlin.math.cbrt
import kotlin.math.pow

internal class PigmentClusters(val colors: IntArray, val weights: FloatArray, val labels: IntArray)

private val linearSrgb = FloatArray(256) {
    val v = it / 255f
    if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
}

private fun colorBin(rgb: Int) =
    ((rgb ushr 9) and 0x7c00) or ((rgb ushr 6) and 0x03e0) or ((rgb ushr 3) and 0x001f)

/** Weighted Lloyd clustering in Oklab. A compact histogram makes the cost
 * depend on the number of distinct pigments, even for densely sampled works. */
internal suspend fun clusterPigments(pixels: IntArray): PigmentClusters {
    val count = IntArray(32768)
    val red = IntArray(count.size)
    val green = IntArray(count.size)
    val blue = IntArray(count.size)
    for (p in pixels) {
        val i = colorBin(p)
        count[i]++
        red[i] += (p ushr 16) and 255
        green[i] += (p ushr 8) and 255
        blue[i] += p and 255
    }
    val bins = count.indices.filter { count[it] > 0 }.toIntArray()
    val lab = FloatArray(bins.size * 3)
    for ((i, bin) in bins.withIndex()) {
        val r = linearSrgb[red[bin] / count[bin]]
        val g = linearSrgb[green[bin] / count[bin]]
        val b = linearSrgb[blue[bin] / count[bin]]
        val l = cbrt(0.41222147f * r + 0.53633254f * g + 0.05144599f * b)
        val m = cbrt(0.21190350f * r + 0.68069955f * g + 0.10739696f * b)
        val s = cbrt(0.08830246f * r + 0.28171884f * g + 0.62997870f * b)
        lab[3 * i] = 0.21045426f * l + 0.79361779f * m - 0.00407205f * s
        lab[3 * i + 1] = 1.9779985f * l - 2.4285922f * m + 0.4505937f * s
        lab[3 * i + 2] = 0.0259040f * l + 0.7827718f * m - 0.8086758f * s
    }

    val maxColors = minOf(6, bins.size)
    val centers = FloatArray(3 * maxColors)
    val nearest = FloatArray(bins.size) { Float.MAX_VALUE }
    val assignment = IntArray(bins.size)
    var seed = bins.indices.maxBy { count[bins[it]] }
    var k = 0
    while (k < maxColors) {
        lab.copyInto(centers, k * 3, seed * 3, seed * 3 + 3)
        var bestScore = 0f
        for (i in bins.indices) {
            nearest[i] = minOf(nearest[i], labDistance(lab, i, centers, k))
            val score = nearest[i] * count[bins[i]]
            if (score > bestScore) {
                seed = i
                bestScore = score
            }
        }
        k++
        if (nearest[seed] < 0.00045f) break
    }

    val weight = IntArray(k)
    val sums = FloatArray(3 * k)
    for (iteration in 0 until 16) {
        yield()
        sums.fill(0f)
        weight.fill(0)
        var changed = false
        for (i in bins.indices) {
            var nearestCenter = 0
            var distance = Float.MAX_VALUE
            for (j in 0 until k) {
                val d = labDistance(lab, i, centers, j)
                if (d < distance) {
                    nearestCenter = j
                    distance = d
                }
            }
            if (assignment[i] != nearestCenter) changed = true
            assignment[i] = nearestCenter
            val n = count[bins[i]]
            weight[nearestCenter] += n
            for (c in 0..2) sums[nearestCenter * 3 + c] += lab[i * 3 + c] * n
        }
        if (!changed) break
        for (j in 0 until k) if (weight[j] > 0) {
            for (c in 0..2) centers[j * 3 + c] = sums[j * 3 + c] / weight[j]
        }
    }
    val order = weight.indices.filter { weight[it] > 0 }.sortedByDescending { weight[it] }
    val ranks = IntArray(k)
    for ((rank, original) in order.withIndex()) ranks[original] = rank
    val lookup = IntArray(count.size)
    val r = IntArray(k)
    val g = IntArray(k)
    val b = IntArray(k)
    for ((i, bin) in bins.withIndex()) {
        val j = assignment[i]
        lookup[bin] = ranks[j]
        r[j] += red[bin]
        g[j] += green[bin]
        b[j] += blue[bin]
    }
    val colors = IntArray(order.size) { index ->
        val j = order[index]
        val n = weight[j]
        (0xff shl 24) or ((r[j] / n) shl 16) or ((g[j] / n) shl 8) or (b[j] / n)
    }
    return PigmentClusters(colors, FloatArray(order.size) { weight[order[it]].toFloat() / pixels.size },
        IntArray(pixels.size) { lookup[colorBin(pixels[it])] })
}

private fun labDistance(colors: FloatArray, ci: Int, centers: FloatArray, si: Int): Float {
    val dl = colors[ci * 3] - centers[si * 3]
    val da = colors[ci * 3 + 1] - centers[si * 3 + 1]
    val db = colors[ci * 3 + 2] - centers[si * 3 + 2]
    return dl * dl + da * da + db * db
}
