package io.github.mejdi14.oddscroll.cardsphere

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * Dotted marks, as SVG path data. They are drawn as strokes with an all but
 * zero dash and a round cap, which is what turns a line into a run of dots —
 * one path instead of placing every dot by hand, and the spacing follows the
 * curve for free.
 */
private val ICONS = mapOf(
    "x" to "M30 30 L48 48 M70 30 L52 48 M30 70 L48 52 M70 70 L52 52 " +
        "M48 48 H52 M48 52 H52 M48 48 V52 M52 48 V52",
    "ring" to "M50 22 A28 28 0 1 1 49.9 22",
    "flower" to "M50 20 A10 10 0 1 1 49.9 20 M50 40 V82 M50 56 L38 46 " +
        "M50 56 L62 46 M50 70 L40 62 M50 70 L60 62",
    "layers" to "M50 24 L80 40 L50 56 L20 40 Z M20 52 L50 68 L80 52 M20 64 L50 80 L80 64",
    "basket" to "M22 42 H78 L70 78 H30 Z M36 42 L44 24 M64 42 L56 24 " +
        "M36 56 V66 M50 56 V66 M64 56 V66",
    "person" to "M50 22 A12 12 0 1 1 49.9 22 M26 80 C26 58 74 58 74 80",
    "search" to "M44 26 A18 18 0 1 1 43.9 26 M57 57 L76 76",
)

private val SHAPE_PATHS = mapOf(
    "arch" to "M22 80V48a28 28 0 0 1 56 0v32z",
    "quarter" to "M22 78V22a56 56 0 0 1 56 56z",
    "halves" to "M20 50a15 15 0 0 1 30 0zM50 50a15 15 0 0 0 30 0z",
    "plus" to "M42 20h16v22h22v16H58v22H42V58H20V42h22z",
)

private val parsed = HashMap<String, Path>()

private fun pathOf(data: String): Path =
    parsed.getOrPut(data) { PathParser().parsePathString(data).toPath() }

private val DottedIcon = Stroke(
    width = 3.2f,
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 7f), 0f),
)

private val IconInk = Color(0xFFF2F2F2)
private val Label = Color(0xFF6B6B6B)

/**
 * Laid-out text, kept between frames.
 *
 * Every string here is fixed and every size is a fraction of a card's base
 * width, so a layout is worth doing once and keeping — measuring forty cards'
 * worth of text on every frame is not. The glyphs themselves are still
 * rasterised under the live transform, so a card coming to the front stays
 * sharp rather than being a scaled-up picture of smaller text.
 */
internal class CardText(private val measurer: TextMeasurer) {
    private val cache = HashMap<Key, TextLayoutResult>()

    private data class Key(
        val text: String,
        val size: Float,
        val weight: Int,
        val color: Long,
        val tracking: Float,
        val lineHeight: Float,
        val width: Int,
    )

    fun of(
        text: String,
        size: Float,
        weight: FontWeight,
        color: Color,
        tracking: Float = -0.04f,
        lineHeight: Float = 0f,
        width: Int = Int.MAX_VALUE,
    ): TextLayoutResult = cache.getOrPut(
        Key(text, size, weight.weight, color.value.toLong(), tracking, lineHeight, width)
    ) {
        measurer.measure(
            text = AnnotatedString(text),
            style = style(size, weight, color, tracking, lineHeight),
            constraints = if (width == Int.MAX_VALUE) Constraints() else Constraints(maxWidth = width),
        )
    }

    /** The one string that changes colour mid-way, so it cannot share [of]. */
    fun shipped(size: Float, lineHeight: Float): TextLayoutResult = cache.getOrPut(
        Key("::ship", size, 700, 0L, -0.05f, lineHeight, Int.MAX_VALUE)
    ) {
        measurer.measure(
            text = AnnotatedString.Builder().apply {
                pushStyle(SpanStyle(color = Paper))
                append("Copy.\nPaste.\n")
                pop()
                pushStyle(SpanStyle(color = Lime))
                append("Ship.")
            }.toAnnotatedString(),
            style = style(size, FontWeight.Bold, Paper, -0.05f, lineHeight),
        )
    }

    private fun style(
        size: Float,
        weight: FontWeight,
        color: Color,
        tracking: Float,
        lineHeight: Float,
    ) = TextStyle(
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        letterSpacing = tracking.em,
        lineHeight = if (lineHeight > 0f) (size * lineHeight).sp else TextUnit.Unspecified,
    )
}

/**
 * One card, drawn in its own coordinates — 0 to [SphereCard.w] across and 0 to
 * [SphereCard.h] down. Whatever transform put it on the sphere is already in
 * place, so everything here is authored at the card's base size and nothing has
 * to know how big it ended up.
 */
internal fun DrawScope.drawCard(card: SphereCard, text: CardText) {
    val w = card.w
    val h = card.h
    val radius = max(4f, w * 0.03f)
    val rounded = Path().apply {
        addRoundRect(RoundRect(Rect(0f, 0f, w, h), CornerRadius(radius, radius)))
    }

    clipPath(rounded) {
        drawRect(card.background ?: defaultBackground(card.kind), Offset.Zero, Size(w, h))
        when (card.kind) {
            CardKind.Lime -> centred(text.of("Cue.", w * 0.3f, FontWeight.Bold, Ink, -0.05f), w, h)
            CardKind.Icon -> icon(card.icon, w, h)
            CardKind.Chip -> chip(text, w, h)
            CardKind.Boxed -> boxed(text, w, h)
            CardKind.Dots -> dots(card, w, h)
            CardKind.Typeface -> typeface(text, w, h)
            CardKind.Shape -> shape(card, w, h)
            CardKind.Target -> target(w, h)
            CardKind.Swatch -> swatch(card, text, w, h)
            CardKind.Stripe -> stripe(w, h)
            CardKind.Poster -> poster(text, w, h)
            CardKind.Words -> words(text, w, h)
        }
    }
}

private fun defaultBackground(kind: CardKind): Color = when (kind) {
    CardKind.Lime -> Lime
    CardKind.Icon, CardKind.Dots, CardKind.Words -> CardDark
    CardKind.Chip -> Color(0xFF151515)
    CardKind.Boxed -> Ink
    CardKind.Poster -> Grey
    CardKind.Typeface -> Paper
    CardKind.Shape -> Paper
    CardKind.Target, CardKind.Swatch, CardKind.Stripe -> Lime
}

private fun DrawScope.centred(layout: TextLayoutResult, w: Float, h: Float) {
    drawText(
        layout,
        topLeft = Offset(
            (w - layout.size.width) / 2f,
            (h - layout.size.height) / 2f,
        ),
    )
}

/** An icon drawn in its own hundred-unit box, scaled whole so the stroke and
    the dash spacing come with it. */
private fun DrawScope.icon(name: String, w: Float, h: Float) {
    val path = pathOf(ICONS[name] ?: ICONS.getValue("search"))
    val size = w * 0.72f
    withTransform({
        translate((w - size) / 2f, (h - size) / 2f)
        scale(size / 100f, size / 100f, Offset.Zero)
    }) {
        drawPath(path, IconInk, style = DottedIcon)
    }
}

private fun DrawScope.chip(text: CardText, w: Float, h: Float) {
    val layout = text.of("Cue.", w * 0.17f, FontWeight.SemiBold, Color(0xFF111111), -0.03f)
    val padX = w * 0.1f
    val padY = w * 0.07f
    val boxW = layout.size.width + padX * 2f
    val boxH = layout.size.height + padY * 2f
    val left = (w - boxW) / 2f
    val top = (h - boxH) / 2f
    drawRoundRect(
        color = Color(0xFFF4F4F4),
        topLeft = Offset(left, top),
        size = Size(boxW, boxH),
        cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
    )
    drawText(layout, topLeft = Offset(left + padX, top + padY))
}

private fun DrawScope.boxed(text: CardText, w: Float, h: Float) {
    val layout = text.of("Cue.", w * 0.2f, FontWeight.SemiBold, Color(0xFF111111), -0.05f)
    val dotBox = w * 0.12f
    val padX = w * 0.09f
    val padY = w * 0.1f
    val boxW = layout.size.width + w * 0.05f + dotBox + padX * 2f
    val boxH = layout.size.height + padY * 2f
    val left = (w - boxW) / 2f
    val top = (h - boxH) / 2f
    val corner = CornerRadius(w * 0.02f, w * 0.02f)

    // The reference puts a white glow behind this one. There is no blur to
    // reach for here, so it is built from a few translucent spreads, which is
    // what a blurred shadow is anyway.
    for (step in 3 downTo 1) {
        val grow = w * 0.04f * step
        drawRoundRect(
            color = Color.White.copy(alpha = 0.1f / step),
            topLeft = Offset(left - grow, top - grow),
            size = Size(boxW + grow * 2f, boxH + grow * 2f),
            cornerRadius = CornerRadius(corner.x + grow, corner.y + grow),
        )
    }
    drawRoundRect(Color(0xFFFAFAFA), Offset(left, top), Size(boxW, boxH), corner)
    drawText(layout, topLeft = Offset(left + padX, top + padY))

    val dotsLeft = left + padX + layout.size.width + w * 0.05f
    val dotsTop = top + (boxH - dotBox) / 2f
    val unit = dotBox / 10f
    for ((dx, dy) in listOf(2 to 2, 5 to 2, 8 to 2, 2 to 5, 5 to 5, 2 to 8, 5 to 8, 8 to 8)) {
        drawCircle(
            color = Color(0xFFBDBDBD),
            radius = unit * 0.9f,
            center = Offset(dotsLeft + dx * unit, dotsTop + dy * unit),
        )
    }
}

/** A seeded scatter of discs on a four by five lattice, holes and all. */
private fun DrawScope.dots(card: SphereCard, w: Float, h: Float) {
    var s = card.seed * 9301L + 49297L
    fun rnd(): Float {
        s = (s * 9301L + 49297L) % 233280L
        return s.toFloat() / 233280f
    }
    val boxW = w * 0.82f
    val boxH = h * 0.82f
    withTransform({
        translate((w - boxW) / 2f, (h - boxH) / 2f)
        scale(boxW / 100f, boxH / 124f, Offset.Zero)
    }) {
        for (y in 0 until 5) {
            for (x in 0 until 4) {
                val v = rnd()
                if (v < 0.22f) continue
                drawCircle(
                    color = card.dotColor,
                    radius = 2.5f + v * 8.5f,
                    center = Offset(16f + x * 23f, 14f + y * 24f),
                )
            }
        }
    }
}

private fun DrawScope.typeface(text: CardText, w: Float, h: Float) {
    centred(text.of("Aa", w * 0.46f, FontWeight.SemiBold, Ink, -0.06f), w, h)
    val left = text.of("Inter 600", w * 0.06f, FontWeight.Medium, Label)
    val right = text.of("−6%", w * 0.06f, FontWeight.Medium, Label)
    val inset = w * 0.07f
    drawText(left, topLeft = Offset(inset, h - inset - left.size.height))
    drawText(right, topLeft = Offset(w - inset - right.size.width, h - inset - right.size.height))
}

private fun DrawScope.shape(card: SphereCard, w: Float, h: Float) {
    val size = w * 0.7f
    withTransform({
        translate((w - size) / 2f, (h - size) / 2f)
        scale(size / 100f, size / 100f, Offset.Zero)
    }) {
        when (card.shape) {
            "circle" -> drawCircle(card.foreground, 30f, Offset(50f, 50f))
            "stack" -> {
                drawRoundRect(card.foreground, Offset(22f, 22f), Size(56f, 14f), CornerRadius(7f, 7f))
                drawRoundRect(card.foreground, Offset(22f, 43f), Size(40f, 14f), CornerRadius(7f, 7f))
                drawRoundRect(card.foreground, Offset(22f, 64f), Size(50f, 14f), CornerRadius(7f, 7f))
            }
            else -> SHAPE_PATHS[card.shape]?.let { drawPath(pathOf(it), card.foreground) }
        }
    }
}

private fun DrawScope.target(w: Float, h: Float) {
    val size = w * 0.78f
    withTransform({
        translate((w - size) / 2f, (h - size) / 2f)
        scale(size / 100f, size / 100f, Offset.Zero)
    }) {
        val ring = Stroke(width = 1.6f)
        for (r in listOf(44f, 30f, 16f)) drawCircle(Ink, r, Offset(50f, 50f), style = ring)
        drawCircle(Ink, 5f, Offset(50f, 50f))
        val dotted = PathEffect.dashPathEffect(floatArrayOf(0.01f, 4.5f), 0f)
        drawLine(Ink, Offset(50f, 2f), Offset(50f, 98f), 1.6f, StrokeCap.Round, dotted)
        drawLine(Ink, Offset(2f, 50f), Offset(98f, 50f), 1.6f, StrokeCap.Round, dotted)
    }
}

/** Three bands, the first twice the height of the others, each labelled. */
private fun DrawScope.swatch(card: SphereCard, text: CardText, w: Float, h: Float) {
    val unit = h / 4f
    var top = 0f
    card.swatch.forEachIndexed { index, color ->
        val band = if (index == 0) unit * 2f else unit
        drawRect(color, Offset(0f, top), Size(w, band))
        val label = text.of(
            hex(color), w * 0.075f, FontWeight.Medium,
            if (color == Ink) Paper else Ink,
        )
        drawText(
            label,
            topLeft = Offset(w * 0.08f, top + band - w * 0.06f - label.size.height),
        )
        top += band
    }
}

private fun hex(color: Color): String {
    val r = (color.red * 255f + 0.5f).toInt()
    val g = (color.green * 255f + 0.5f).toInt()
    val b = (color.blue * 255f + 0.5f).toInt()
    fun pair(v: Int): String {
        val digits = "0123456789ABCDEF"
        return "${digits[(v shr 4) and 15]}${digits[v and 15]}"
    }
    return "#${pair(r)}${pair(g)}${pair(b)}"
}

private fun DrawScope.stripe(w: Float, h: Float) {
    withTransform({ scale(w / 100f, h / 120f, Offset.Zero) }) {
        for (i in 0 until 6) {
            drawRect(Ink, Offset(0f, 12f + i * 17f), Size(100f, 3f + i * 1.6f))
        }
    }
}

private fun DrawScope.poster(text: CardText, w: Float, h: Float) {
    val panelW = w * 0.8f
    val panelH = h * 0.86f
    val left = (w - panelW) / 2f
    val top = (h - panelH) / 2f
    val panel = Path().apply {
        addRoundRect(
            RoundRect(
                Rect(left, top, left + panelW, top + panelH),
                CornerRadius(w * 0.012f, w * 0.012f),
            )
        )
    }
    clipPath(panel) {
        drawRect(Color(0xFFFBFBFA), Offset(left, top), Size(panelW, panelH))
        val heading = text.of(
            "Where design\nmeets code", w * 0.05f, FontWeight.Medium, Ink, -0.04f, 1.12f,
        )
        drawText(heading, topLeft = Offset(left + w * 0.05f, top + w * 0.05f))

        val markSize = panelW * 0.72f
        withTransform({
            translate(left + panelW * 0.14f, top + panelH * 0.26f)
            scale(markSize / 100f, markSize / 100f, Offset.Zero)
        }) {
            drawCircle(Ink, 38f, Offset(50f, 50f))
            drawPath(pathOf("M50 12a38 38 0 0 1 0 76z"), Lime)
        }

        val mark = text.of("Cue.", w * 0.046f, FontWeight.SemiBold, Ink)
        drawText(
            mark,
            topLeft = Offset(
                left + panelW - w * 0.05f - mark.size.width,
                top + panelH - w * 0.05f - mark.size.height,
            ),
        )
    }
}

private fun DrawScope.words(text: CardText, w: Float, h: Float) {
    val layout = text.shipped(w * 0.15f, 1.02f)
    val pad = w * 0.08f
    drawText(layout, topLeft = Offset(pad, h - pad - layout.size.height))
}
