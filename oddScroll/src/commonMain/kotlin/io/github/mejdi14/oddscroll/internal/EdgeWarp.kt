package io.github.mejdi14.oddscroll.internal

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.max

/**
 * The fisheye, expressed as a forward map.
 *
 * A post-processing version of this samples the rendered frame: for each
 * destination pixel it reads the source at `pos / (1 + k * r * r)`, pulling
 * content inward near the borders so the middle of the frame bulges out to meet
 * them. There is no frame to post-process here — the cards are the geometry —
 * so the same curve has to run the other way: given where a point sits in the
 * undistorted scene, where does the warp put it?
 *
 * That inverse has no closed form, because the strength `k` is itself a
 * function of the radius it is being solved for. It is monotonic over the range
 * that lands on screen, though, so it is inverted once into a lookup table and
 * read back by binary search.
 *
 * The radius is the max-norm, `max(|x|, |y|)`, not the length — the band it
 * drives is a rectangle tracking the frame's own borders rather than a circle
 * inscribed in them. Both components are then scaled by the same factor, so the
 * map is uniform along any ray and the radius of the result scales with it.
 * That is what makes a one-dimensional table enough.
 */
internal class EdgeWarp(
    private val strength: Float = 0.3f,
    private val bandStart: Float = 0.75f,
    private val bandEnd: Float = 1.05f,
) {
    private val table = FloatArray(SAMPLES + 1)

    init {
        for (i in 0..SAMPLES) {
            val r = i.toFloat() / SAMPLES * MAX_R
            table[i] = r * squeeze(r)
        }
    }

    /** Zero across the middle of the frame, rising to one at its borders. */
    fun edgeAt(radius: Float): Float = smoothstep(bandStart, bandEnd, radius)

    private fun squeeze(r: Float): Float = 1f / (1f + strength * edgeAt(r) * r * r)

    /** Max-norm radius, the quantity the band is measured in. */
    fun radiusOf(p: Offset): Float = max(abs(p.x), abs(p.y))

    /**
     * Undistorted normalised position in, distorted normalised position out.
     * Both are in the frame's own coordinates, -1 to 1 on each axis, so the
     * warp is anisotropic in world terms — stronger across the narrow side of
     * the viewport, exactly as the screen-space original is.
     */
    fun forward(x: Float, y: Float): Offset {
        val source = max(abs(x), abs(y))
        if (source <= 1e-4f) return Offset(x, y)
        val scale = invert(source) / source
        return Offset(x * scale, y * scale)
    }

    /**
     * Smallest radius whose squeezed value is [source]. Beyond the end of the
     * table the point is well off screen and gets culled, so it is clamped
     * rather than extrapolated.
     */
    private fun invert(source: Float): Float {
        if (source >= table[SAMPLES]) return MAX_R
        var low = 0
        var high = SAMPLES
        while (high - low > 1) {
            val mid = (low + high) / 2
            if (table[mid] <= source) low = mid else high = mid
        }
        val span = table[high] - table[low]
        val t = if (span <= 0f) 0f else (source - table[low]) / span
        return (low + t) / SAMPLES * MAX_R
    }

    private companion object {
        const val SAMPLES = 128

        /** Past this the curve turns over and stops being invertible; nothing
            that far out reaches the frame anyway. */
        const val MAX_R = 1.35f
    }
}
