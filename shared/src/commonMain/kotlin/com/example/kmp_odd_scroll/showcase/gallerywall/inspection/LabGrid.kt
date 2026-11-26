package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A still field of dots. Only the band crossing the field on the way into the painting glows. */
@Composable
internal fun LabGrid(
    ink: Color,
    background: Color,
    frame: Size.() -> Rect,
    wave: () -> Float,
    modifier: Modifier = Modifier,
) {
    Spacer(modifier.drawWithCache {
        if (size.isEmpty()) return@drawWithCache onDrawBehind {}
        val painting = size.frame()
        val origin = painting.center
        val cell = 18.dp.toPx()
        val dot = 1.dp.toPx()
        val far = hypot(max(origin.x, size.width - origin.x), max(origin.y, size.height - origin.y))
        val band = 2.4f * cell
        // Start just under the nearest edge of the painting so the first glint is visible at once.
        val start = (min(painting.width, painting.height) * 0.5f - band).coerceAtLeast(0f)
        val rows = ceil(-origin.y / cell).toInt()..floor((size.height - origin.y) / cell).toInt()
        val columns = ceil(-origin.x / cell).toInt()..floor((size.width - origin.x) / cell).toInt()
        val dots = buildList {
            for (row in rows) {
                for (column in columns) {
                    add(Offset(origin.x + column * cell, origin.y + row * cell))
                }
            }
        }
        val distances = FloatArray(dots.size) { (dots[it] - origin).getDistance() }
        val field = Brush.radialGradient(
            0f to ink.copy(alpha = 0.29f), 0.68f to ink.copy(alpha = 0.19f), 1f to ink.copy(alpha = 0.07f),
            center = origin, radius = far,
        )
        val light = if (background.luminance() < 0.45f) Color(0xFFBBE5FF) else Color(0xFF306DAD)
        onDrawBehind {
            drawPoints(dots, PointMode.Points, field, strokeWidth = 2f * dot, cap = StrokeCap.Round)
            val t = wave()
            if (t <= 0f || t >= 1f) return@onDrawBehind
            val front = mix(start, far + band, t)
            for (i in dots.indices) {
                val distance = distances[i]
                val gap = abs(distance - front)
                if (gap >= band) continue
                val strength = ease(1f - gap / band)
                val point = dots[i] + (dots[i] - origin) * (strength * 1.4f * dot / max(dot, distance))
                drawCircle(light, dot * (1f + 4f * strength), point, alpha = 0.13f * strength)
                drawCircle(light, dot * (1f + 1.45f * strength), point, alpha = 0.95f * strength)
            }
        }
    })
}
