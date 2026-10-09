package com.example.kmp_odd_scroll.demos

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.mejdi14.oddscroll.helix.HelixItem

/**
 * Card art, generated rather than loaded. The demos need a handful of
 * photograph-shaped images with real tonal range for the grain to bite into;
 * drawing them keeps the samples free of both a network call and an
 * image-loading dependency.
 *
 * Every motif is built from the same parts — a graded sky, a run of ridges, a
 * light source — so the set reads as one body of work rather than six unrelated
 * test patterns.
 */
private const val ART_W = 560
private const val ART_H = 280

private enum class Motif { Peaks, Dunes, Forest, Arch, Blocks, Beam }

private class Plate(
    val title: String,
    val motif: Motif,
    val sky: List<Color>,
    val land: Color,
    val light: Color,
)

private val plates = listOf(
    Plate(
        "Mountain Lake", Motif.Peaks,
        listOf(Color(0xFF16253A), Color(0xFF3E5C72), Color(0xFFC6B49A)),
        Color(0xFF0C131D), Color(0xFFF3E6CE),
    ),
    Plate(
        "Desert Dunes", Motif.Dunes,
        listOf(Color(0xFF3A2416), Color(0xFF9A6134), Color(0xFFE8C68B)),
        Color(0xFF2A1A10), Color(0xFFFFF0C9),
    ),
    Plate(
        "Forest Path", Motif.Forest,
        listOf(Color(0xFF0E1A12), Color(0xFF2C4A2E), Color(0xFFA8BE7E)),
        Color(0xFF07100A), Color(0xFFE6F2C4),
    ),
    Plate(
        "Old Bridge", Motif.Arch,
        listOf(Color(0xFF221C2C), Color(0xFF4C4257), Color(0xFFBFAFB4)),
        Color(0xFF13101A), Color(0xFFF0E2E6),
    ),
    Plate(
        "City Alley", Motif.Blocks,
        listOf(Color(0xFF101217), Color(0xFF2A2F3A), Color(0xFF8E96A6)),
        Color(0xFF070809), Color(0xFFDDE4F0),
    ),
    Plate(
        "Harbour Light", Motif.Beam,
        listOf(Color(0xFF0A1420), Color(0xFF1E4152), Color(0xFF7FB3B8)),
        Color(0xFF050B10), Color(0xFFEAFBFF),
    ),
)

/** The demo set, drawn once. */
fun demoHelixItems(): List<HelixItem> = plates.map { plate ->
    HelixItem(image = render(plate), title = plate.title)
}

private fun render(plate: Plate): ImageBitmap {
    val bitmap = ImageBitmap(ART_W, ART_H)
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        Canvas(bitmap),
        Size(ART_W.toFloat(), ART_H.toFloat()),
    ) {
        val rand = Rand(plate.title.hashCode())
        drawRect(
            brush = Brush.verticalGradient(
                0f to plate.sky[0],
                0.62f to plate.sky[1],
                1f to plate.sky[2],
            )
        )
        when (plate.motif) {
            Motif.Peaks -> peaks(plate, rand)
            Motif.Dunes -> dunes(plate, rand)
            Motif.Forest -> forest(plate, rand)
            Motif.Arch -> arch(plate)
            Motif.Blocks -> blocks(plate, rand)
            Motif.Beam -> beam(plate)
        }
        // A touch of film grit, so the plates do not read as flat vector art.
        repeat(900) {
            val x = rand.next() * size.width
            val y = rand.next() * size.height
            drawRect(
                color = Color.White.copy(alpha = 0.05f + rand.next() * 0.05f),
                topLeft = Offset(x, y),
                size = Size(1f, 1f),
            )
        }
    }
    return bitmap
}

private fun DrawScope.peaks(plate: Plate, rand: Rand) {
    sun(plate, Offset(size.width * 0.74f, size.height * 0.3f), size.minDimension * 0.07f)
    // Three ridges, each lighter and lower than the last, so depth reads from
    // tone as well as from overlap.
    for (layer in 0 until 3) {
        val base = size.height * (0.52f + layer * 0.14f)
        val tint = lerpColor(plate.land, plate.sky[1], 0.45f - layer * 0.2f)
        drawPath(ridge(base, size.height * (0.22f - layer * 0.05f), 4 + layer * 2, rand), tint)
    }
    // The water: the same ridge, flipped, with the sky washed over it.
    drawRect(
        brush = Brush.verticalGradient(
            0f to plate.sky[1].copy(alpha = 0.55f),
            1f to plate.land,
        ),
        topLeft = Offset(0f, size.height * 0.78f),
        size = Size(size.width, size.height * 0.22f),
    )
}

private fun DrawScope.dunes(plate: Plate, rand: Rand) {
    sun(plate, Offset(size.width * 0.28f, size.height * 0.24f), size.minDimension * 0.09f)
    for (layer in 0 until 4) {
        val base = size.height * (0.46f + layer * 0.15f)
        val path = Path().apply {
            moveTo(0f, size.height)
            lineTo(0f, base + size.height * 0.08f)
            cubicTo(
                size.width * 0.3f, base - size.height * 0.1f,
                size.width * 0.62f, base + size.height * 0.12f,
                size.width, base - size.height * 0.04f,
            )
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, lerpColor(plate.land, plate.sky[2], 0.5f - layer * 0.13f))
    }
}

private fun DrawScope.forest(plate: Plate, rand: Rand) {
    // A path receding to a vanishing point, flanked by trunks that thin with
    // distance.
    val vanish = Offset(size.width * 0.52f, size.height * 0.46f)
    drawPath(
        Path().apply {
            moveTo(size.width * 0.3f, size.height)
            lineTo(vanish.x - 4f, vanish.y)
            lineTo(vanish.x + 4f, vanish.y)
            lineTo(size.width * 0.78f, size.height)
            close()
        },
        lerpColor(plate.sky[2], plate.land, 0.35f),
    )
    for (side in intArrayOf(-1, 1)) {
        for (i in 0 until 5) {
            val depth = i / 5f
            val width = size.width * (0.055f - depth * 0.035f)
            val x = vanish.x + side * size.width * (0.06f + depth * 0.46f)
            val top = vanish.y - size.height * (0.1f + depth * 0.5f)
            drawRect(
                color = lerpColor(plate.land, plate.sky[1], 0.5f - depth * 0.4f),
                topLeft = Offset(x - width / 2f, top),
                size = Size(width, size.height - top),
            )
        }
    }
    repeat(60) {
        val x = rand.next() * size.width
        val y = rand.next() * size.height * 0.5f
        drawCircle(
            color = plate.light.copy(alpha = 0.12f + rand.next() * 0.2f),
            radius = 1f + rand.next() * 3f,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.arch(plate: Plate) {
    val deck = size.height * 0.56f
    drawRect(
        color = plate.land,
        topLeft = Offset(0f, deck),
        size = Size(size.width, size.height - deck),
    )
    // Arches cut back out of the deck, lightening with distance.
    for (i in 0 until 4) {
        val span = size.width / 4f
        val cx = span * (i + 0.5f)
        val r = span * 0.36f
        drawArc(
            color = lerpColor(plate.sky[1], plate.sky[2], 0.3f + i * 0.18f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(cx - r, deck - r),
            size = Size(r * 2f, r * 2f),
        )
        drawRect(
            color = lerpColor(plate.sky[1], plate.sky[2], 0.3f + i * 0.18f),
            topLeft = Offset(cx - r, deck),
            size = Size(r * 2f, size.height * 0.2f),
        )
    }
    drawRect(
        color = plate.land,
        topLeft = Offset(0f, deck - size.height * 0.1f),
        size = Size(size.width, size.height * 0.1f),
    )
    drawRect(
        color = plate.light.copy(alpha = 0.5f),
        topLeft = Offset(0f, deck - size.height * 0.1f),
        size = Size(size.width, 2f),
    )
}

private fun DrawScope.blocks(plate: Plate, rand: Rand) {
    // Two facing walls in near-silhouette, with a slot of bright sky between.
    for (side in intArrayOf(0, 1)) {
        var x = if (side == 0) 0f else size.width
        val direction = if (side == 0) 1f else -1f
        var depth = 0f
        while (depth < 0.45f) {
            val w = size.width * (0.07f + rand.next() * 0.06f)
            val h = size.height * (0.5f + rand.next() * 0.5f)
            val left = if (direction > 0) x else x - w
            drawRect(
                color = lerpColor(plate.land, plate.sky[1], depth * 1.6f),
                topLeft = Offset(left, size.height - h),
                size = Size(w, h),
            )
            // Windows.
            var wy = size.height - h + 8f
            while (wy < size.height - 12f) {
                var wx = left + 5f
                while (wx < left + w - 6f) {
                    if (rand.next() > 0.45f) {
                        drawRect(
                            color = plate.light.copy(alpha = 0.15f + rand.next() * 0.55f),
                            topLeft = Offset(wx, wy),
                            size = Size(3f, 5f),
                        )
                    }
                    wx += 7f
                }
                wy += 11f
            }
            x += direction * w
            depth += 0.09f
        }
    }
}

private fun DrawScope.beam(plate: Plate) {
    val centre = Offset(size.width * 0.5f, size.height * 0.38f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(plate.light, plate.light.copy(alpha = 0f)),
            center = centre,
            radius = size.minDimension * 0.6f,
        ),
        radius = size.minDimension * 0.6f,
        center = centre,
    )
    // Rays, drawn as thin wedges so the light has some structure to it.
    for (i in 0 until 14) {
        val spread = size.width * 0.5f
        val x = centre.x - spread + spread * 2f * (i / 13f)
        drawPath(
            Path().apply {
                moveTo(centre.x, centre.y)
                lineTo(x - 6f, size.height)
                lineTo(x + 6f, size.height)
                close()
            },
            plate.light.copy(alpha = 0.06f),
        )
    }
    drawRect(
        color = plate.land,
        topLeft = Offset(0f, size.height * 0.82f),
        size = Size(size.width, size.height * 0.18f),
    )
}

private fun DrawScope.sun(plate: Plate, centre: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(plate.light.copy(alpha = 0.55f), plate.light.copy(alpha = 0f)),
            center = centre,
            radius = radius * 5f,
        ),
        radius = radius * 5f,
        center = centre,
    )
    drawCircle(plate.light, radius, centre)
}

/** A jagged skyline, as a closed path down to the bottom of the plate. */
private fun DrawScope.ridge(base: Float, amplitude: Float, teeth: Int, rand: Rand): Path =
    Path().apply {
        moveTo(0f, size.height)
        lineTo(0f, base)
        for (i in 1..teeth) {
            val x = size.width * i / teeth
            val peak = base - amplitude * (0.35f + rand.next() * 0.65f)
            lineTo(x - size.width / teeth / 2f, peak)
            lineTo(x, base + amplitude * 0.12f * rand.next())
        }
        lineTo(size.width, size.height)
        close()
    }

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * k,
        green = a.green + (b.green - a.green) * k,
        blue = a.blue + (b.blue - a.blue) * k,
        alpha = a.alpha + (b.alpha - a.alpha) * k,
    )
}

/**
 * A fixed-seed xorshift, so a plate looks the same on every platform and every
 * run — the art is part of the sample, not a different picture each launch.
 */
private class Rand(seed: Int) {
    private var state: UInt = (seed.toUInt() or 1u)

    fun next(): Float {
        state = state xor (state shl 13)
        state = state xor (state shr 17)
        state = state xor (state shl 5)
        return (state % 10_000u).toFloat() / 10_000f
    }
}

