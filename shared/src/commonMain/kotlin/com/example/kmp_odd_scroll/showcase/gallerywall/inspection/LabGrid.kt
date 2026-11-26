package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/** A wave of dots spreads across the wall while the glass lifts off the canvas. */
@Composable
internal fun LabGrid(
    frame: () -> Rect,
    reveal: () -> Float,
    entrySpread: () -> Float,
    lens: InspectionLens,
    modifier: Modifier = Modifier,
) {
    Spacer(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithCache {
        if (size.isEmpty()) return@drawWithCache onDrawBehind {}
        val painting = frame()
        val origin = if (painting.isEmpty) size.center else painting.center
        val cell = 14.dp.toPx()
        val dot = 0.72.dp.toPx()
        val field = InspectionInk.copy(alpha = 0.23f)
        val openingLight = Color(0xFFFFE4C6)
        val entryBlue = Color(0xFF79BFFF)
        val lensRadius = 120.dp.toPx()
        val band = 3f * cell
        val start = (min(painting.width, painting.height) * 0.5f - band).coerceAtLeast(0f)
        val far = hypot(max(origin.x, size.width - origin.x), max(origin.y, size.height - origin.y))
        val firstX = size.center.x % cell
        val firstY = size.center.y % cell
        val columns = floor((size.width - firstX) / cell).toInt() + 1
        val rows = floor((size.height - firstY) / cell).toInt() + 1
        // Start at the grid crossing behind the centre of the painting.
        val sourceX = firstX + round((origin.x - firstX) / cell) * cell
        val sourceY = firstY + round((origin.y - firstY) / cell) * cell
        val hiddenReach = minOf(sourceX - painting.left, painting.right - sourceX,
            sourceY - painting.top, painting.bottom - sourceY).coerceAtLeast(0f)
        val fullReach = max(sourceX, size.width - sourceX) + max(sourceY, size.height - sourceY)
        val dots = List(rows * columns) { i -> Offset(firstX + i % columns * cell, firstY + i / columns * cell) }
        val distances = FloatArray(dots.size) { i -> (dots[i] - origin).getDistance() }
        val stillWall = obtainGraphicsLayer().apply {
            record {
                drawRect(Color.Black)
                drawPoints(dots, PointMode.Points, field, strokeWidth = dot * 2f, cap = StrokeCap.Round)
            }
        }

        onDrawBehind {
            val progress = reveal()
            if (progress <= 0f) return@onDrawBehind
            drawLayer(stillWall)

            // Repaint just the cells inside the lens's bounding box, removing their old positions.
            val pointer = lens.position
            val intensity = lens.strength
            if (pointer != null && intensity > 0f) {
                val left = ceil((pointer.x - lensRadius - firstX) / cell).toInt().coerceAtLeast(0)
                val right = floor((pointer.x + lensRadius - firstX) / cell).toInt().coerceAtMost(columns - 1)
                val top = ceil((pointer.y - lensRadius - firstY) / cell).toInt().coerceAtLeast(0)
                val bottom = floor((pointer.y + lensRadius - firstY) / cell).toInt().coerceAtMost(rows - 1)
                if (left <= right && top <= bottom) {
                    drawRect(Color.Black, Offset(firstX + (left - 0.5f) * cell, firstY + (top - 0.5f) * cell),
                        Size((right - left + 1) * cell, (bottom - top + 1) * cell))
                    for (row in top..bottom) for (column in left..right) {
                        val point = dots[row * columns + column]
                        val delta = point - pointer
                        val distance = delta.getDistance()
                        val bend = ease(1f - distance / lensRadius) * intensity
                        val spread = delta * (1.2f * cell * bend / max(cell, distance))
                        drawCircle(InspectionInk, dot * (1f + 0.5f * bend), point + spread,
                            alpha = field.alpha + 0.5f * bend)
                    }
                }
            }

            if (progress < 1f) {
                val front = mix(start, far + band, progress)
                for (i in dots.indices) {
                    val glow = ease(1f - abs(distances[i] - front) / band)
                    if (glow <= 0f) continue
                    drawCircle(openingLight, dot * (1f + 2f * glow), dots[i], alpha = 0.55f * glow)
                }
                // Mask the black and the dots together. The unreached part still shows the original room.
                val radius = front + band
                drawRect(Brush.radialGradient(
                    (1f - 2f * band / radius).coerceAtLeast(0f) to Color.White.copy(alpha = ease(progress * 14f)),
                    1f to Color.Transparent,
                    center = origin, radius = radius,
                ), blendMode = BlendMode.DstIn)
            }

            val entry = entrySpread()
            if (entry > 0f && !painting.isEmpty) {
                // Distance is measured along the grid. Each line keeps its lit length as the tips advance.
                val reach = mix(hiddenReach, fullReach, ease(entry))
                for (row in 0 until rows) {
                    val y = firstY + row * cell
                    val length = reach - abs(y - sourceY)
                    if (length > 0f) drawLine(entryBlue, Offset(sourceX - length, y), Offset(sourceX + length, y), dot)
                }
                for (column in 0 until columns) {
                    val x = firstX + column * cell
                    val length = reach - abs(x - sourceX)
                    if (length > 0f) drawLine(entryBlue, Offset(x, sourceY - length), Offset(x, sourceY + length), dot)
                }
            }
        }
    })
}
