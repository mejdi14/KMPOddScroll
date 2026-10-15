package com.example.kmp_odd_scroll.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural cover art for the gallery tiles. One motif branch per route, so a
 * tile previews the shape of the motion rather than showing a screenshot that
 * would have to be kept in step.
 */
@Composable
fun ScrollArtwork(route: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF14151A),
                1f to Color(0xFF07070A),
            )
        )
        when (route) {
            "helix" -> helix()
            "edge_distortion" -> edgeDistortion()
            else -> helix()
        }
    }
}

/**
 * The helix in miniature: a stack of bars swept around a cylinder, each taken
 * further down toward the page the further it sits from the waist.
 */
private fun DrawScope.helix() {
    val rows = 9
    for (row in 0 until rows) {
        val t = row / (rows - 1f)
        val angle = t * 5.4f
        val y = size.height * (0.1f + t * 0.8f)
        val width = size.width * 0.52f * (0.6f + 0.4f * cos(angle))
        val centre = size.width * (0.5f + 0.18f * sin(angle))
        val fade = 1f - (kotlin.math.abs(t - 0.5f) * 2f)
        val bar = Color(0xFFE4DCC9).copy(alpha = 0.1f + fade * 0.75f)

        drawRect(bar, Offset(centre - width / 2f, y), Size(width, size.height * 0.055f))
    }
}

/**
 * The lattice in miniature, pushed through the same barrel curve the gallery
 * uses, so the tile previews the bend rather than just the grid.
 */
private fun DrawScope.edgeDistortion() {
    val cells = 4
    val pad = size.width * 0.07f
    val cellW = (size.width - pad * 2f) / cells
    val cellH = (size.height - pad * 2f) / cells

    for (row in 0 until cells) {
        for (col in 0 until cells) {
            val cx = pad + (col + 0.5f) * cellW
            val cy = pad + (row + 0.5f) * cellH
            val nx = (cx / size.width) * 2f - 1f
            val ny = (cy / size.height) * 2f - 1f
            val r = kotlin.math.max(kotlin.math.abs(nx), kotlin.math.abs(ny))
            // Same squeeze as EdgeWarp, read forwards: cells near the border
            // are pulled in and shrink, which is the bend in one frame.
            val squeeze = 1f / (1f + 0.55f * r * r)
            val w = cellW * 0.68f * squeeze
            val h = cellH * 0.78f * squeeze
            val x = (nx * squeeze * 0.5f + 0.5f) * size.width
            val y = (ny * squeeze * 0.5f + 0.5f) * size.height
            drawRect(
                color = Color(0xFFE4DCC9).copy(alpha = 0.25f + (1f - r) * 0.6f),
                topLeft = Offset(x - w / 2f, y - h / 2f),
                size = Size(w, h),
            )
        }
    }
}
