package com.example.kmp_odd_scroll.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
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
            "infinite_spiral" -> infiniteSpiral()
            "edge_distortion" -> edgeDistortion()
            "flex_carousel" -> flexCarousel()
            "infinite_menu" -> infiniteMenu()
            "card_sphere" -> cardSphere()
            "lift_stack" -> liftStack()
            "tape_rack" -> tapeRack()
            "gallery_wall" -> galleryWall()
            "terrace" -> terrace()
            else -> infiniteSpiral()
        }
    }
}

/**
 * The spiral in miniature: cards stepped down the frame and swung across it by
 * the turn, the middle one largest and the ends fading out.
 */
private fun DrawScope.infiniteSpiral() {
    val cards = 9
    for (i in 0 until cards) {
        // Measured from the middle outward, as the spiral itself is.
        val offset = i - (cards - 1) / 2f
        val angle = offset * 0.9f
        val focus = 1f - kotlin.math.min(kotlin.math.abs(offset) / 3f, 1f)
        val edge = kotlin.math.min(kotlin.math.abs(offset) / 4.5f, 1f)
        val card = size.minDimension * 0.17f * (0.78f + 0.3f * focus) *
            (0.86f + 0.14f * cos(angle))
        val cx = size.width * 0.5f + sin(angle) * size.width * 0.22f
        val cy = size.height * 0.5f + offset * size.height * 0.115f
        drawRect(
            color = Color(0xFFE4DCC9).copy(alpha = (1f - edge) * 0.85f + 0.08f),
            topLeft = Offset(cx - card / 2f, cy - card / 2f),
            size = Size(card, card),
        )
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

/**
 * The deck in miniature: four flat layers seen from a quarter turn, and the
 * card it stopped past standing up off the top one.
 */
private fun DrawScope.liftStack() {
    val half = size.minDimension * 0.27f
    val squash = 0.47f
    val layer = half * 0.36f
    val cx = size.width / 2f
    val top = size.height * 0.5f

    fun plate(cy: Float, color: Color) {
        val path = Path().apply {
            moveTo(cx - half, cy)
            lineTo(cx, cy - half * squash)
            lineTo(cx + half, cy)
            lineTo(cx, cy + half * squash)
            close()
        }
        drawPath(path, color)
    }
    for (i in 3 downTo 0) {
        plate(top + i * layer, Color(0xFFBFBFB6).copy(alpha = 0.3f + (3 - i) * 0.17f))
    }

    // Upright on the line through the top plate's middle, raised clear of it.
    val rise = half * 0.62f
    val foot = top - rise + half * squash * 0.5f
    val path = Path().apply {
        moveTo(cx - half / 2f, foot + half * squash / 2f)
        lineTo(cx + half / 2f, foot - half * squash / 2f)
        lineTo(cx + half / 2f, foot - half * squash / 2f - half * 1.26f)
        lineTo(cx - half / 2f, foot + half * squash / 2f - half * 1.26f)
        close()
    }
    drawPath(path, Color(0xFFF4F1E8))
}

/**
 * The rack in miniature: a row of clear CD cases stepping up to the right, each
 * covering the one behind, and the one in the middle standing proud of it.
 */
private fun DrawScope.tapeRack() {
    val case = size.minDimension * 0.36f
    val slope = case * 0.177f
    val stepX = case * 0.322f * 0.889f
    val stepY = -case * 0.322f * 0.458f
    val originX = size.width / 2f - case / 2f
    val originY = size.height / 2f - case / 2f + case * 0.17f

    for (i in 4 downTo -4) {
        val proud = (1f - abs(i).coerceAtMost(1)) * case * 0.342f
        val x = originX + i * stepX
        val y = originY + i * stepY - proud
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + case * 0.984f, y + slope)
            lineTo(x + case * 0.984f, y + slope + case)
            lineTo(x, y + case)
            close()
        }
        val lit = if (i == 0) 0.95f else 0.3f + (4 - abs(i)) * 0.08f
        drawPath(path, Color(0xFFDBE6ED).copy(alpha = 0.06f))
        drawPath(path, Color(0xFFDBE6ED).copy(alpha = lit * 0.8f), style = Stroke(1.5f))
        // The disc behind the lid, sheared with the face it sits in.
        val cx = x + case * 0.492f
        val cy = y + slope * 0.5f + case * 0.5f
        val r = case * 0.41f
        val disc = Path().apply {
            val steps = 32
            for (k in 0..steps) {
                val a = k * 2f * PI.toFloat() / steps
                val px = cos(a) * r * 0.984f
                val py = sin(a) * r + cos(a) * r * 0.177f
                if (k == 0) moveTo(cx + px, cy + py) else lineTo(cx + px, cy + py)
            }
            close()
        }
        drawPath(disc, Color(0xFFBFBFB6).copy(alpha = lit * 0.4f))
        drawPath(disc, Color(0xFFBFBFB6).copy(alpha = lit * 0.7f), style = Stroke(1f))
    }
}

/**
 * The room in miniature, with the reference's own proportions: paintings along
 * a back wall, round the corners and down the side walls towards the eye, each
 * outline traced along the wall so the ones on a corner bend with it.
 */
private fun DrawScope.galleryWall() {
    val depth = 6.954f
    val half = 1.101f
    val corner = 0.644f
    val flat = half - corner
    val arc = corner * PI.toFloat() / 2f
    val focal = size.width * 1.4f
    val cx = size.width / 2f
    val cy = size.height * 0.48f

    fun project(s: Float, y: Float): Offset {
        val a = abs(s)
        val (x, z) = when {
            a <= flat -> a to depth
            a <= flat + arc -> {
                val turn = (a - flat) / corner
                (flat + corner * sin(turn)) to (depth - corner + corner * cos(turn))
            }
            else -> half to (depth - corner - (a - flat - arc))
        }
        return Offset(cx + focal * (if (s < 0f) -x else x) / z, cy + focal * y / z)
    }

    val shapes = listOf(1.3f to 0.9f, 0.8f to 1f, 1.6f to 0.8f, 0.85f to 0.95f, 1.1f to 0.85f)
    var s = -0.65f
    while (s > -6f) s -= 1.45f
    var i = 0
    while (s < 6f) {
        val (w, h) = shapes[i % shapes.size]
        val path = Path()
        val steps = 6
        for (k in 0..steps) {
            val p = project(s + w * k / steps, -h / 2f)
            if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        for (k in steps downTo 0) {
            val p = project(s + w * k / steps, h / 2f)
            path.lineTo(p.x, p.y)
        }
        path.close()
        val near = 1f - (abs(s + w / 2f) / 5f).coerceAtMost(1f)
        drawPath(path, Color(0xFFBFBFB6).copy(alpha = 0.25f + near * 0.5f))
        s += w + 0.564f
        i++
    }
}

/**
 * The stack in miniature, by the same rule as the real one: each level back a
 * fifth smaller and a fifth of a width further out, so only its edge shows
 * past the one in front.
 */
private fun DrawScope.terrace() {
    val card = size.minDimension * 0.34f
    val cx = size.width / 2f
    val cy = size.height / 2f
    for (level in 3 downTo 0) {
        val scale = 0.801f.pow(level)
        val w = card * scale
        val h = w * 1.384f
        val lit = 1f - level / 4f
        for (side in if (level == 0) listOf(0) else listOf(-1, 1)) {
            val y = cy + side * level * 0.207f * card
            drawRect(
                // Opaque, so a band does not show through the one in front.
                color = lerp(Color(0xFF14151A), Color(0xFFBFBFB6), 0.2f + lit * 0.6f),
                topLeft = Offset(cx - w / 2f, y - h / 2f),
                size = Size(w, h),
            )
        }
    }
}
