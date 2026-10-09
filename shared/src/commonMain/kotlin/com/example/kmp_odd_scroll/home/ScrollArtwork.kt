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
