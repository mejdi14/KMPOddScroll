package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
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

/** A center-out reveal leaves a still field of dots behind the painting. */
@Composable
internal fun LabGrid(
    ink: Color,
    background: Color,
    frame: Size.() -> Rect,
    reveal: () -> Float,
    wave: () -> Float,
    modifier: Modifier = Modifier,
) {
    Spacer(modifier.drawWithCache {
        if (size.isEmpty()) return@drawWithCache onDrawBehind {}
        val painting = size.frame()
        val origin = painting.center
        val gridCenter = size.center
        val cell = 12.dp.toPx()
        val dotDiameter = 1.dp.toPx()
        val waveDot = 1.dp.toPx()
        val field = ink.copy(alpha = 0.22f)
        val revealFar = gridCenter.getDistance()
        val feather = 3f * cell
        val far = hypot(max(origin.x, size.width - origin.x), max(origin.y, size.height - origin.y))
        val band = 2.4f * cell
        // Start just under the nearest edge of the painting so the first glint is visible at once.
        val start = (min(painting.width, painting.height) * 0.5f - band).coerceAtLeast(0f)
        val rows = ceil(-gridCenter.y / cell).toInt()..floor((size.height - gridCenter.y) / cell).toInt()
        val columns = ceil(-gridCenter.x / cell).toInt()..floor((size.width - gridCenter.x) / cell).toInt()
        val dots = buildList {
            for (row in rows) {
                for (column in columns) {
                    add(Offset(gridCenter.x + column * cell, gridCenter.y + row * cell))
                }
            }
        }
        val distances = FloatArray(dots.size) { (dots[it] - origin).getDistance() }
        val light = if (background.luminance() < 0.45f) Color(0xFFBBE5FF) else Color(0xFF306DAD)
        onDrawBehind {
            val progress = reveal()
            if (progress >= 1f) {
                drawPoints(dots, PointMode.Points, field, strokeWidth = dotDiameter, cap = StrokeCap.Round)
            } else if (progress > 0f) {
                val radius = (revealFar + feather) * progress
                val fadeStart = (1f - feather / radius).coerceAtLeast(0f)
                val opacity = (radius / feather).coerceAtMost(1f)
                val revealedField = Brush.radialGradient(
                    fadeStart to field.copy(alpha = field.alpha * opacity),
                    1f to field.copy(alpha = 0f),
                    center = gridCenter,
                    radius = radius,
                )
                drawPoints(dots, PointMode.Points, revealedField, strokeWidth = dotDiameter, cap = StrokeCap.Round)
            }
            val t = wave()
            if (t <= 0f || t >= 1f) return@onDrawBehind
            val front = mix(start, far + band, t)
            for (i in dots.indices) {
                val distance = distances[i]
                val gap = abs(distance - front)
                if (gap >= band) continue
                val strength = ease(1f - gap / band)
                val point = dots[i] + (dots[i] - origin) * (strength * 1.4f * waveDot / max(waveDot, distance))
                drawCircle(light, waveDot * (1f + 4f * strength), point, alpha = 0.13f * strength)
                drawCircle(light, waveDot * (1f + 1.45f * strength), point, alpha = 0.95f * strength)
            }
        }
    })
}
