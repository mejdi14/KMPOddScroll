package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/*
 * The bench the painting is studied on: squared paper, as under a
 * conservator's lens. Fine lines a cell apart, a heavier one every few cells
 * and heavier still the two through the painting's middle, with a ruler's
 * ticks along those two. It is laid out from the middle of the painting, so
 * the painting sits square on it, and it fades out before the edges of the
 * stage rather than stop at them.
 */

private val CELL = 12.dp
private const val MAJOR = 5

private const val MINOR_ALPHA = 0.07f
private const val MAJOR_ALPHA = 0.14f
private const val AXIS_ALPHA = 0.24f

/** Ruler ticks across the two axes, a minor and a major one. */
private val TICK = 2.5.dp
private val TICK_MAJOR = 5.dp

/** How far in from the stage's edges it has faded in. */
private val FADE = 36.dp

/**
 * How much of the reveal each line takes to draw out. The rest is the front
 * of it travelling from the painting's middle to the far corner of the stage.
 */
private const val DRAW_OUT = 0.4f

/**
 * Squared paper filling the stage behind the painting, laid out from
 * [centre]. As [reveal] goes from 0 to 1 it spreads out from behind the
 * painting: each line, once the front gets to it, draws out both ways from
 * the middle. Both are read as it is drawn, not in composition.
 */
@Composable
internal fun LabGrid(ink: Color, centre: DrawScope.() -> Offset, reveal: () -> Float, modifier: Modifier = Modifier) {
    // Offscreen, so the fade at the edges takes the lines out and nothing else.
    Canvas(modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val r = reveal()
        if (r <= 0f || size.width <= 0f || size.height <= 0f) return@Canvas
        val o = centre()
        val cell = CELL.toPx()
        val hair = max(1f, 0.6.dp.toPx())
        val major = max(1f, 1.dp.toPx())
        val reachX = max(o.x, size.width - o.x)
        val reachY = max(o.y, size.height - o.y)
        val far = hypot(reachX, reachY).coerceAtLeast(1f)
        val front = r * (1f + DRAW_OUT)

        fun drawn(offset: Float): Float {
            val t = ((front - abs(offset) / far) / DRAW_OUT).coerceIn(0f, 1f)
            return 1f - (1f - t) * (1f - t) * (1f - t)
        }

        fun alpha(i: Int) = when {
            i == 0 -> AXIS_ALPHA
            i % MAJOR == 0 -> MAJOR_ALPHA
            else -> MINOR_ALPHA
        }
        fun width(i: Int) = if (i % MAJOR == 0) major else hair

        val columns = ceil(reachX / cell).toInt()
        for (i in -columns..columns) {
            val x = o.x + i * cell
            if (x < -major || x > size.width + major) continue
            val t = drawn(i * cell)
            if (t <= 0f) continue
            val reach = reachY * t
            drawLine(ink.copy(alpha = alpha(i) * t), Offset(x, o.y - reach), Offset(x, o.y + reach), width(i))
        }
        val rows = ceil(reachY / cell).toInt()
        for (j in -rows..rows) {
            val y = o.y + j * cell
            if (y < -major || y > size.height + major) continue
            val t = drawn(j * cell)
            if (t <= 0f) continue
            val reach = reachX * t
            drawLine(ink.copy(alpha = alpha(j) * t), Offset(o.x - reach, y), Offset(o.x + reach, y), width(j))
        }

        // A ruler along each axis, graduated in half cells.
        val tick = TICK.toPx()
        fun tickLength(k: Int) = when {
            k % (2 * MAJOR) == 0 -> TICK_MAJOR.toPx()
            k % 2 == 0 -> tick
            else -> tick * 0.6f
        }
        val half = cell * 0.5f
        for (k in -2 * columns..2 * columns) {
            if (k == 0) continue
            val x = o.x + k * half
            if (x < 0f || x > size.width) continue
            val t = drawn(k * half)
            if (t <= 0f) continue
            val l = tickLength(k)
            drawLine(ink.copy(alpha = AXIS_ALPHA * t), Offset(x, o.y - l), Offset(x, o.y + l), hair)
        }
        for (k in -2 * rows..2 * rows) {
            if (k == 0) continue
            val y = o.y + k * half
            if (y < 0f || y > size.height) continue
            val t = drawn(k * half)
            if (t <= 0f) continue
            val l = tickLength(k)
            drawLine(ink.copy(alpha = AXIS_ALPHA * t), Offset(o.x - l, y), Offset(o.x + l, y), hair)
        }

        val fade = FADE.toPx()
        val fx = (fade / size.width).coerceAtMost(0.5f)
        val fy = (fade / size.height).coerceAtMost(0.5f)
        drawRect(
            Brush.horizontalGradient(0f to Color.Transparent, fx to Color.Black, 1f - fx to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, fy to Color.Black, 1f - fy to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }
}
