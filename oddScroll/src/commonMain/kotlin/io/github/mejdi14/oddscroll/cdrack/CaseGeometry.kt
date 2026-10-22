package io.github.mejdi14.oddscroll.cdrack

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas as BitmapCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The case, in millimetres from the middle of its face, after Ann Nguyen's
 * clear jewel case (github.com/annnguyentt/vibe-coding, cd-case): 146 square,
 * the hinge down the left, a 120 mm disc printed from 16.2 to 59.1.
 *
 * Every number below is the reference's own, read out of its geometry: a rod
 * there is a rounded-rectangle profile swept along a path, and here it is the
 * same path stroked to the profile's width. Its y is up and ours is down, so
 * what the reference puts at negative y sits at positive y here.
 */
internal const val CASE_MM = 146f
internal const val HALF_MM = CASE_MM / 2f
internal const val CORNER_MM = 8f
internal const val DISC_R = 60f
internal const val PRINT_IN = 16.2f
internal const val PRINT_OUT = 59.1f
internal const val CLEAR_R = 15f
internal const val DISC_HOLE = 7.5f

/** The hinge pin runs down the left edge, this far in and this far up the case. */
internal const val HINGE_X = -HALF_MM + 1f
internal const val HINGE_Z = 4f

/** How deep each part sits, from the back of the floor to the face of the lid. */
internal const val FLOOR_Z = -4.5f
internal const val TRAY_BACK = -5.2f
internal const val TRAY_FRONT = 2.4f
internal const val DISC_Z = -1.8f
internal const val LID_BACK = 3f
internal const val LID_FRONT = 5f

/** Where the backdrop stands behind the case, which its shadow lands on. */
internal const val BACKDROP_Z = -70f

/** One rod: a path, and the width of its profile across. */
internal class Rod(
    val path: Path,
    val width: Float,
    val cap: StrokeCap = StrokeCap.Round,
    /** It lies over the dark of the disc, which is what its middle shows. */
    val overPrint: Boolean = false,
)

/**
 * One flat piece of the case: the rods and the sheet of plastic that lie at
 * about the same depth, framed by the part of the face they cover.
 */
internal class CaseLayer(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    /** Depth in its frame, along the case's z. */
    val z: Float,
    /** Flat plastic, or null. */
    val sheet: Path?,
    /** How thick the sheet is, for what it absorbs. */
    val thickness: Float,
    val rods: List<Rod>,
    /** It lies over the printed disc, so what its rods bend into view is the disc. */
    val overDisc: Boolean = false,
)

/** A round knuckle of the hinge: a stretch of the pin's axis, and its radius. */
internal class Knuckle(val from: Float, val to: Float, val radius: Float)

internal fun trayLayers(): List<CaseLayer> = listOf(floorLayer(), rodLayer(), hubLayer(), tabLayer())

internal val trayKnuckles = listOf(Knuckle(-63f, -44f, 2.1f), Knuckle(44f, 63f, 2.1f))
internal val lidKnuckles = listOf(Knuckle(-42f, -20f, 2f), Knuckle(20f, 42f, 2f))

private fun floorLayer() = CaseLayer(
    left = -HALF_MM, top = -HALF_MM, width = CASE_MM, height = CASE_MM,
    z = FLOOR_Z,
    sheet = roundedSquare(HALF_MM, CORNER_MM),
    thickness = 1.4f,
    rods = listOf(Rod(roundedSquare(HALF_MM - 0.75f, CORNER_MM - 0.6f), 1.5f)),
)

/** The tray's rails and ribs, the well the disc sits in and the clips round it. */
private fun rodLayer(): CaseLayer {
    val rods = ArrayList<Rod>()
    val at = HALF_MM - 3.3f
    rods += Rod(roundedSquare(at, CORNER_MM - 1.5f), 6.6f)
    for (s in floatArrayOf(-1f, 1f)) {
        for (c in floatArrayOf(-35f, -12f, 12f, 35f)) {
            rods += Rod(segment(c - 1.2f, s * at, c + 1.2f, s * at), 7.5f)
            // The tab takes the middle of the hinge side.
            if (s < 0f && kotlin.math.abs(c) < 20f) continue
            rods += Rod(segment(s * at, c - 1.2f, s * at, c + 1.2f), 7.5f)
        }
    }
    val e = HALF_MM - 9.6f
    for (sx in floatArrayOf(-1f, 1f)) {
        for (sy in floatArrayOf(-1f, 1f)) {
            val along = if (sx < 0f) 26f else 35f
            rods += Rod(corner(sx * e, sy * along, sx * e, sy * e, sx * 35f, sy * e, 4.5f), 4.8f)
            rods += Rod(segment(sx * e, sy * along, sx * at, sy * along), 3.8f)
            rods += Rod(segment(sx * 35f, sy * e, sx * 35f, sy * at), 3.8f)
        }
    }
    rods += Rod(circle(61.6f), 2.3f)
    rods += Rod(circle(61.6f - 1.55f), 1f)
    for (deg in floatArrayOf(59f, 121f, 238.5f, 301.5f)) {
        val a = deg / 180f * PI.toFloat()
        val dx = cos(a)
        val dy = -sin(a)
        rods += Rod(segment(dx * 58.6f, dy * 58.6f, dx * 63.2f, dy * 63.2f), 3.2f)
    }
    // The two hooks along the foot that hold the booklet.
    for (sx in floatArrayOf(-1f, 1f)) {
        rods += Rod(corner(sx * 46f, e, sx * 46f, 49.5f, sx * 42.6f, 45.6f, 4.2f), 3.8f)
    }
    return CaseLayer(
        left = -HALF_MM, top = -HALF_MM, width = CASE_MM, height = CASE_MM,
        z = -0.7f,
        sheet = null,
        thickness = 0f,
        rods = rods,
    )
}

/** The hub the disc snaps onto, through its hole. */
private fun hubLayer(): CaseLayer {
    val cap = Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(Rect(Offset.Zero, 19.4f))
        addOval(Rect(Offset.Zero, 6.2f))
        for (hx in floatArrayOf(-1f, 1f)) for (hy in floatArrayOf(-1f, 1f)) {
            addOval(Rect(Offset(hx * 8.9f, hy * 8.9f), 1.7f))
        }
    }
    val rods = arrayListOf(
        Rod(circle(18.8f), 1.6f, overPrint = true),
        Rod(circle(16.5f), 0.9f, overPrint = true),
        Rod(circle(9.6f), 1.9f),
        Rod(circle(6.6f), 1.1f),
    )
    for (hx in floatArrayOf(-1f, 1f)) for (hy in floatArrayOf(-1f, 1f)) {
        rods += Rod(circle(2f, hx * 8.9f, hy * 8.9f), 0.9f)
    }
    return CaseLayer(
        left = -21f, top = -21f, width = 42f, height = 42f,
        z = -0.5f,
        sheet = cap,
        thickness = 0.9f,
        rods = rods,
        overDisc = true,
    )
}

/** The tab down the hinge side that the booklet tucks under. */
private fun tabLayer(): CaseLayer {
    val right = -50.5f
    val mid = (-HALF_MM + right) / 2f
    val hw = (right + HALF_MM) / 2f
    val bracket = Path().apply {
        moveTo(-69f, -13f)
        lineTo(-61.7f, -13f)
        quadraticTo(-58.5f, -13f, -58.5f, -9.8f)
        lineTo(-58.5f, 9.8f)
        quadraticTo(-58.5f, 13f, -61.7f, 13f)
        lineTo(-69f, 13f)
    }
    return CaseLayer(
        left = -HALF_MM, top = -23f, width = right + HALF_MM + 1f, height = 46f,
        z = 1.8f,
        sheet = roundedRect(mid, 0f, hw, 22f, 4.2f),
        thickness = 1.6f,
        rods = listOf(
            Rod(roundedRect(mid, 0f, hw - 0.8f, 21.2f, 3.4f), 1.6f),
            Rod(bracket, 2.7f),
            Rod(roundedRect(-55.4f, 0f, 4.4f, 3.2f, 1.2f), 1.3f),
        ),
    )
}

/** The lid: one sheet, its rim, the notches round its edge. */
internal fun lidLayer(): CaseLayer {
    val inset = HALF_MM - 3.4f
    val rods = arrayListOf(
        Rod(roundedSquare(HALF_MM - 1f, CORNER_MM - 1f), 2f),
        Rod(roundedSquare(inset, CORNER_MM - 3f), 0.84f),
    )
    for (x in floatArrayOf(-42f, 30f)) {
        rods += Rod(Path().apply { arcTo(Rect(Offset(x, -inset), 4.4f), 0f, 180f, true) }, 0.84f)
        rods += Rod(Path().apply { arcTo(Rect(Offset(x, inset), 4.4f), 180f, 180f, true) }, 0.84f)
    }
    for (sx in floatArrayOf(-1f, 1f)) for (y in floatArrayOf(-30f, 30f)) {
        rods += Rod(segment(sx * inset, y - 3f, sx * inset, y + 3f), 1f)
    }
    return CaseLayer(
        left = -HALF_MM, top = -HALF_MM, width = CASE_MM, height = CASE_MM,
        z = (LID_BACK + LID_FRONT) / 2f,
        sheet = roundedSquare(HALF_MM, CORNER_MM),
        thickness = LID_FRONT - LID_BACK,
        rods = rods,
    )
}

// --- the outer wall ------------------------------------------------------------

/** Segments a rounded corner of the wall is cut into. */
private const val WALL_CORNER_STEPS = 4

/**
 * The outline of the case round its edge, as points with their outward
 * normals — x, y, nx, ny each — the wall standing on it is a quad per pair.
 */
internal val wallOutline: FloatArray by lazy {
    val out = ArrayList<Float>()
    val inner = HALF_MM - CORNER_MM
    // Corners clockwise on screen from the top right, each swept from the end
    // of one side to the start of the next.
    val corners = listOf(
        Triple(inner, -inner, -90f),
        Triple(inner, inner, 0f),
        Triple(-inner, inner, 90f),
        Triple(-inner, -inner, 180f),
    )
    for ((cx, cy, start) in corners) {
        for (k in 0..WALL_CORNER_STEPS) {
            val a = (start + 90f * k / WALL_CORNER_STEPS) / 180f * PI.toFloat()
            val nx = cos(a)
            val ny = sin(a)
            out += cx + nx * CORNER_MM
            out += cy + ny * CORNER_MM
            out += nx
            out += ny
        }
    }
    out.toFloatArray()
}

// --- baking --------------------------------------------------------------------

/**
 * What a layer bakes to. The plain look is drawn here once and then only
 * moved; the height map is for a shader to light a pixel at a time.
 */
internal enum class Bake { Look, Height }

/**
 * A layer drawn once at [ppm] pixels a millimetre.
 *
 * As a look: clear rods on a white page read dark at their edges, where they
 * turn the view into the dark of the room, and pale down the middle, where
 * they look straight through to the page — so each is a dark stroke with a
 * light one inside it, and the sheet is all but invisible.
 *
 * As a height map, for the shader: red is how high a rod's profile stands
 * (0 at its edge, 1 down its middle), green is the sheet, blue is the rod's
 * half-width over 4 mm, so its slope comes back out in millimetres.
 */
internal fun bakeLayer(layer: CaseLayer, ppm: Float, mode: Bake): ImageBitmap {
    val w = ceil(layer.width * ppm).toInt().coerceAtLeast(1)
    val h = ceil(layer.height * ppm).toInt().coerceAtLeast(1)
    val bitmap = ImageBitmap(w, h)
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        BitmapCanvas(bitmap),
        Size(w.toFloat(), h.toFloat()),
    ) {
        withTransform({
            scale(ppm, ppm, Offset.Zero)
            translate(-layer.left, -layer.top)
        }) {
            when (mode) {
                Bake.Look -> {
                    layer.sheet?.let { drawPath(it, Color(0xFF8A9199).copy(alpha = 0.035f)) }
                    for (rod in layer.rods) lookRod(rod)
                }
                Bake.Height -> {
                    layer.sheet?.let { drawPath(it, Color(0f, 1f, 0f)) }
                    for (rod in layer.rods) heightRod(rod)
                }
            }
        }
    }
    return bitmap
}

private fun DrawScope.lookRod(rod: Rod) {
    val w = rod.width
    fun stroke(width: Float) = Stroke(width, cap = rod.cap, join = StrokeJoin.Round)
    drawPath(rod.path, Color(0xFF3B4047), style = stroke(w))
    drawPath(rod.path, if (rod.overPrint) Color(0xFF6A7078) else Color(0xFF9AA1A9), style = stroke(w * 0.72f))
    drawPath(rod.path, if (rod.overPrint) Color(0xFF2A2D32) else Color(0xFFF3F5F7), style = stroke(w * 0.4f))
    // The light bent through it lands a little down and right of its middle.
    translate(w * 0.12f, w * 0.12f) {
        drawPath(rod.path, Color(0xFF23272D).copy(alpha = 0.55f), style = stroke(w * 0.08f))
    }
}

/** Steps a rod's profile is drawn in; fine enough that each is under a pixel. */
private const val PROFILE_STEPS = 24

private fun DrawScope.heightRod(rod: Rod) {
    val half = rod.width / 2f
    // From the full width in, each narrower stroke standing higher, kept only
    // where it is the highest yet, so crossing rods keep the taller profile.
    for (i in 0 until PROFILE_STEPS) {
        val across = 1f - i.toFloat() / PROFILE_STEPS
        val height = sqrt(1f - across * across)
        drawPath(
            rod.path,
            Color(height, 0f, (half / 4f).coerceAtMost(1f)),
            style = Stroke(rod.width * across, cap = rod.cap, join = StrokeJoin.Round),
            blendMode = BlendMode.Lighten,
        )
    }
}

/** The disc's two faces and its print, baked for the plain look. */
internal class DiscArt(
    val front: ImageBitmap,
    val back: ImageBitmap,
    val print: ImageBitmap,
)

/**
 * The bare disc as the reference shades it head on. The printed face is near
 * black where it is unprinted — a thin layer over the data — with a pale edge
 * and a dip at the step round the hub; the data face is a grey mirror. The
 * rainbow along the tracks goes on top every frame, because it follows the
 * light rather than the disc.
 */
internal fun bakeDisc(art: ImageBitmap, ppm: Float): DiscArt {
    val px = ceil(2f * DISC_R * ppm).toInt().coerceAtLeast(2)
    fun face(block: DrawScope.() -> Unit): ImageBitmap {
        val bitmap = ImageBitmap(px, px)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            BitmapCanvas(bitmap),
            Size(px.toFloat(), px.toFloat()),
        ) {
            withTransform({
                scale(ppm, ppm, Offset.Zero)
                translate(DISC_R, DISC_R)
            }) { block() }
        }
        return bitmap
    }
    val front = face {
        drawPath(ring(DISC_R, CLEAR_R), Color(0xFF1C1C1F))
        drawPath(ring(DISC_R, 58.2f), Color(0xFF8D9095))
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = 16.6f, center = Offset.Zero, style = Stroke(0.5f))
        clearMiddle()
    }
    val back = face {
        drawPath(ring(DISC_R, CLEAR_R), Color(0xFFC4C7CB))
        drawPath(ring(DISC_R, 58.2f), Color(0xFF8D9095))
        drawCircle(Color.Black.copy(alpha = 0.35f), radius = 16.6f, center = Offset.Zero, style = Stroke(0.5f))
        clearMiddle()
    }
    val print = face {
        clipPath(ring(PRINT_OUT, PRINT_IN)) {
            val square = discSquare(art)
            val d = (2f * DISC_R).roundToInt()
            drawImage(
                image = art,
                srcOffset = IntOffset(square.left, square.top),
                srcSize = IntSize(square.width, square.height),
                dstOffset = IntOffset(-d / 2, -d / 2),
                dstSize = IntSize(d, d),
                filterQuality = FilterQuality.High,
            )
        }
    }
    return DiscArt(front, back, print)
}

/** The clear plastic between the hub and the data, and the edge of the hole. */
private fun DrawScope.clearMiddle() {
    drawPath(ring(CLEAR_R, DISC_HOLE), Color(0xFF9AA0A8).copy(alpha = 0.16f))
    drawCircle(Color(0xFF3B4047), radius = DISC_HOLE + 0.25f, center = Offset.Zero, style = Stroke(0.5f))
}

/** The middle square of [art], which the disc prints across its whole diameter. */
internal fun discSquare(art: ImageBitmap): androidx.compose.ui.unit.IntRect {
    val side = min(art.width, art.height).coerceAtLeast(1)
    val left = (art.width - side) / 2
    val top = (art.height - side) / 2
    return androidx.compose.ui.unit.IntRect(left, top, left + side, top + side)
}

/** [art]'s middle square on its own, for a shader to sample, no larger than [max]. */
internal fun squareArt(art: ImageBitmap, max: Int): ImageBitmap {
    val square = discSquare(art)
    val px = min(square.width, max).coerceAtLeast(1)
    val bitmap = ImageBitmap(px, px)
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        BitmapCanvas(bitmap),
        Size(px.toFloat(), px.toFloat()),
    ) {
        drawImage(
            image = art,
            srcOffset = IntOffset(square.left, square.top),
            srcSize = IntSize(square.width, square.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(px, px),
            filterQuality = FilterQuality.High,
        )
    }
    return bitmap
}

/** Shadow maps are this coarse on purpose: drawn up smooth, they come out soft. */
private const val SHADOW_PX = 40

/**
 * What the tray and the lid keep off the backdrop, after the reference's
 * backdrop: the tray a little all over and more under its rails, the disc a
 * quarter of the light, the lid a little and a little more at its rim.
 */
internal fun bakeShadow(lid: Boolean): ImageBitmap {
    val bitmap = ImageBitmap(SHADOW_PX, SHADOW_PX)
    val ppm = SHADOW_PX / (CASE_MM + 12f)
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        BitmapCanvas(bitmap),
        Size(SHADOW_PX.toFloat(), SHADOW_PX.toFloat()),
    ) {
        withTransform({
            scale(ppm, ppm, Offset.Zero)
            translate(HALF_MM + 6f, HALF_MM + 6f)
        }) {
            val outline = roundedSquare(HALF_MM, CORNER_MM)
            if (lid) {
                drawPath(outline, Color.Black.copy(alpha = 0.03f))
                drawPath(roundedSquare(HALF_MM - 1.25f, CORNER_MM - 1.25f), Color.Black.copy(alpha = 0.05f), style = Stroke(2.5f))
            } else {
                drawPath(outline, Color.Black.copy(alpha = 0.02f))
                drawPath(roundedSquare(HALF_MM - 5.75f, CORNER_MM - 5.75f), Color.Black.copy(alpha = 0.08f), style = Stroke(5.5f))
                drawPath(ring(DISC_R, DISC_HOLE), Color.Black.copy(alpha = 0.24f))
            }
        }
    }
    return bitmap
}

/** Where a shadow map's pixel (0, 0) sits on the case, and how many millimetres a pixel is. */
internal const val SHADOW_LEFT = -HALF_MM - 6f
internal val SHADOW_MM_PER_PX = (CASE_MM + 12f) / SHADOW_PX

// --- paths -----------------------------------------------------------------------

internal fun roundedSquare(half: Float, r: Float): Path = roundedRect(0f, 0f, half, half, r)

internal fun roundedRect(cx: Float, cy: Float, hw: Float, hh: Float, r: Float): Path = Path().apply {
    addRoundRect(RoundRect(cx - hw, cy - hh, cx + hw, cy + hh, CornerRadius(max(r, 0f))))
}

internal fun circle(r: Float, cx: Float = 0f, cy: Float = 0f): Path = Path().apply {
    addOval(Rect(Offset(cx, cy), r))
}

/** The band between two radii about the middle. */
internal fun ring(outer: Float, inner: Float): Path = Path().apply {
    fillType = PathFillType.EvenOdd
    addOval(Rect(Offset.Zero, outer))
    addOval(Rect(Offset.Zero, inner))
}

internal fun segment(x0: Float, y0: Float, x1: Float, y1: Float): Path = Path().apply {
    moveTo(x0, y0)
    lineTo(x1, y1)
}

/** From a to b to c, the turn at b rounded off by about [r]. */
internal fun corner(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, r: Float): Path {
    fun toward(fx: Float, fy: Float): Offset {
        val dx = fx - bx
        val dy = fy - by
        val l = max(sqrt(dx * dx + dy * dy), 1e-4f)
        val cut = min(r, l / 2f)
        return Offset(bx + dx / l * cut, by + dy / l * cut)
    }
    val enter = toward(ax, ay)
    val leave = toward(cx, cy)
    return Path().apply {
        moveTo(ax, ay)
        lineTo(enter.x, enter.y)
        quadraticTo(bx, by, leave.x, leave.y)
        lineTo(cx, cy)
    }
}
