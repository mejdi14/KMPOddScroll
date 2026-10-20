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
            "flex_carousel" -> flexCarousel()
            "infinite_menu" -> infiniteMenu()
            "card_sphere" -> cardSphere()
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

/**
 * The row in miniature: cards of unequal width on one line, dragged upward as
 * they reach the sides, which is the band of glass standing in for itself at
 * this size.
 */
private fun DrawScope.flexCarousel() {
    val widths = floatArrayOf(0.1f, 0.17f, 0.13f, 0.2f, 0.12f, 0.16f, 0.11f)
    val gap = size.width * 0.025f
    val cardH = size.height * 0.4f

    var total = gap * (widths.size - 1)
    for (w in widths) total += size.width * w
    var x = (size.width - total) / 2f

    for (w in widths) {
        val cw = size.width * w
        val centre = (x + cw / 2f) / size.width * 2f - 1f
        // Lifted on a bell centred on the edge band, as the lens lifts the row.
        val band = kotlin.math.abs(centre)
        val lift = 16f * band * band * (1f - band) * (1f - band)
        val y = size.height / 2f - cardH / 2f - lift * size.height * 0.16f *
            (if (centre >= 0f) 1f else -1f)
        drawRect(
            color = Color(0xFFE4DCC9).copy(alpha = 0.3f + (1f - band) * 0.6f),
            topLeft = Offset(x, y),
            size = Size(cw, cardH),
        )
        x += cw + gap
    }
}

/**
 * The sphere in miniature: tiles on a golden-angle spiral, those facing away
 * drawn smaller and fainter, exactly as the menu shades them.
 */
private fun DrawScope.infiniteMenu() {
    val count = 54
    val golden = 3.1415927f * (3f - kotlin.math.sqrt(5f))
    val r = size.minDimension * 0.4f
    val cx = size.width / 2f
    val cy = size.height / 2f

    for (i in 0 until count) {
        val y = 1f - (2f * (i + 0.5f)) / count
        val ring = kotlin.math.sqrt((1f - y * y).coerceAtLeast(0f))
        val px = kotlin.math.cos(golden * i) * ring
        val pz = kotlin.math.sin(golden * i) * ring
        if (pz < 0.02f) continue
        val tile = size.minDimension * 0.1f * (0.5f + 0.5f * pz)
        drawRect(
            color = Color(0xFFE4DCC9).copy(alpha = 0.12f + pz * 0.75f),
            topLeft = Offset(cx + px * r - tile / 2f, cy - y * r - tile / 2f),
            size = Size(tile, tile),
        )
    }
}

/**
 * The field in miniature: one big card at the front with small ones scattered
 * round it, on the paper the flowers are painted on.
 */
private fun DrawScope.cardSphere() {
    val golden = 3.1415927f * (3f - kotlin.math.sqrt(5f))
    val r = size.minDimension * 0.46f
    val cx = size.width / 2f
    val cy = size.height / 2f

    for (i in 0 until 26) {
        val y = 1f - (2f * (i + 0.5f)) / 26
        val ring = kotlin.math.sqrt((1f - y * y).coerceAtLeast(0f))
        val px = kotlin.math.cos(golden * i + 0.37f) * ring
        val pz = kotlin.math.sin(golden * i + 0.37f) * ring
        if (pz < 0f) continue
        val w = size.minDimension * 0.1f * (0.6f + 0.4f * pz)
        drawRect(
            color = Color(0xFFBFBFB6).copy(alpha = 0.2f + pz * 0.5f),
            topLeft = Offset(cx + px * r - w / 2f, cy - y * r - w * 0.6f),
            size = Size(w, w * 1.25f),
        )
    }

    val hero = size.minDimension * 0.3f
    drawRect(
        color = Color(0xFFF4F1E8),
        topLeft = Offset(cx - hero / 2f, cy - hero * 0.62f),
        size = Size(hero, hero * 1.25f),
    )
}
