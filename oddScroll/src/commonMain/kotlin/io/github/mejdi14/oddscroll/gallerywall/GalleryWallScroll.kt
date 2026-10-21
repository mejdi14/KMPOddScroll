package io.github.mejdi14.oddscroll.gallerywall

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as BitmapCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import io.github.mejdi14.oddscroll.internal.clamped
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** One painting on the wall. */
data class GalleryWallItem(
    /** Hung whole, at its own aspect. */
    val image: ImageBitmap,
    val title: String = "",
    val artist: String = "",
    val date: String = "",
    val medium: String = "",
    /**
     * Height against the tallest painting, which is 1. The reference hangs its
     * paintings from 0.72 to 1: a size of their own, with a ceiling three of
     * them sit exactly on.
     */
    val scale: Float = 1f,
)

/*
 * The room, in heights of the tallest painting, as measured off the reference.
 *
 * How tall a painting stands at each column of the screen is one fixed curve,
 * the same for every painting in every frame bar its own height — pooled over
 * 475,000 columns in 640 frames, to 0.44 px. So the paintings slide along a
 * wall that stays put, seen from inside, level with their middles. The curve
 * is flat across the middle, then rises in proportion to the distance from the
 * centre of the frame, which is what a wall running straight at the eye does:
 * a back wall square to the view, side walls running towards it, and a rounded
 * corner between. That shape puts the curve to 0.37 px.
 */

/** From the eye to the back wall. */
private const val DEPTH = 6.954f

/** From the middle to either side wall. */
private const val HALF_WIDTH = 1.101f

/** Radius of the rounded corners. */
private const val CORNER = 0.644f

/**
 * Between one painting and the next, edge to edge along the wall: the same
 * from the back wall round the corners and down the sides, to 2.6%.
 */
private const val GAP = 0.564f

/**
 * The eye's focal length, in widths of the frame — fixed by the paintings
 * holding their shape as they round the corner, to 1%. Capped by the height so
 * a wide, short frame does not blow the paintings up past it.
 */
private const val FOCAL = 1.400f
private const val FOCAL_PER_HEIGHT = 2.2f

/** The eye level, down the frame. The middles of the paintings sit on it. */
private const val HORIZON = 0.45f

/** Nothing closer than this is drawn; the side walls run on past the eye. */
private const val NEAR = 0.6f

/*
 * The label under each painting, on the wall with it: set left, the title in
 * italic over the artist, date and medium in grey. Read off two frames of one
 * painting square on the back wall, so these are the loosest numbers here.
 */
private const val LABEL_GAP = 0.045f
private const val LABEL_SIZE = 0.045f
private const val LABEL_LINE = 0.055f
private const val LABEL_GREY = 0.45f

/** Corner arcs are cut into chords this many to the quarter turn — under half a pixel off. */
private const val ARC_CHORDS = 12

private const val FLAT = HALF_WIDTH - CORNER
private const val ARC = CORNER * (PI.toFloat() / 2f)

/** Painting pixels per card height, against the back wall's own on screen; the edges come in 2.3x nearer. */
private const val BITMAP_SCALE = 1.6f

/** A notch of the wheel, in card heights along the wall. */
private const val WHEEL_STEP = 0.6f

private const val TAP_SLOP = 8f

@Composable
fun rememberGalleryWallScrollState(): GalleryWallScrollState =
    remember { GalleryWallScrollState() }

/**
 * Paintings hung round the inside of a room — a back wall facing you, side
 * walls running towards you, rounded corners between — that slide along the
 * walls past you. A painting comes in down the right-hand wall, turns the
 * corner, crosses the back and goes out down the left.
 *
 * The whole hang is one number. The wall stays put and every painting hangs a
 * fixed way along it from the one before, so where the hang has slid to says
 * where everything is; nothing turns, grows, fades or dims on its way round —
 * the change of shape is all perspective. Each painting, label and all, is
 * drawn once into a bitmap and then through its own exact projection: one
 * `drawImage` for each flat stretch of wall it is on, and a dozen chords to a
 * corner.
 *
 * Drag along it, or scroll, and the hang springs after; tap a painting to
 * bring it to the middle.
 *
 * @param items paintings, in order; the hang repeats, so it has no end.
 * @param background the wall.
 * @param contentColor the labels.
 * @param state also lets a host's own controls move it.
 */
@Composable
fun GalleryWallScroll(
    items: List<GalleryWallItem>,
    modifier: Modifier = Modifier,
    background: Color = Color(0xFFFFFFFF),
    contentColor: Color = Color(0xFF1A1A1A),
    state: GalleryWallScrollState = rememberGalleryWallScrollState(),
) {
    if (items.isEmpty()) {
        Box(modifier)
        return
    }

    val measurer = rememberTextMeasurer()
    val hang = remember(items) { Hang(items) }
    val art = remember(items, contentColor, measurer) { WallArt(items, contentColor, measurer) }
    val room = remember { Room() }
    SideEffect { state.hang = hang }

    LaunchedEffect(state) {
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                val deltaMs = if (previous == 0L) 16.67f else (now - previous) / 1_000_000f
                previous = now
                state.advance(deltaMs.clamped(1f, 50f))
            }
        }
    }

    Canvas(
        modifier
            .background(background)
            .fillMaxSize()
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var last = down.position
                    var travel = 0f
                    var dragging = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Release) break
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = change.position.x - last.x
                        travel += abs(dx) + abs(change.position.y - last.y)
                        last = change.position
                        if (travel > TAP_SLOP) dragging = true
                        if (dragging) {
                            // The back wall follows the finger, pixel for pixel.
                            state.nudge(-dx / room.backPx)
                            change.consume()
                        }
                    }
                    if (!dragging) {
                        val hit = room.hit(down.position.x, down.position.y, state.position, hang)
                        if (hit != null) state.scrollTo(hit)
                    }
                }
            }
            .pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val scroll = event.changes.firstOrNull()?.scrollDelta ?: continue
                        val by = scroll.x + scroll.y
                        if (by == 0f) continue
                        state.nudge(by * WHEEL_STEP)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
    ) {
        state.tick // read, so the wall repaints as the hang moves
        room.fit(size.width, size.height)
        art.ensure(room.backPx * BITMAP_SCALE)
        drawWall(art, hang, room, state.position)
    }
}

// --- the hang ----------------------------------------------------------------

/** Where each painting hangs along the wall, in card heights, with the hang at rest. */
internal class Hang(items: List<GalleryWallItem>) {
    val count = items.size
    val heights = FloatArray(count) { i -> items[i].scale }
    val widths = FloatArray(count) { i ->
        val image = items[i].image
        items[i].scale * image.width / max(image.height, 1).toFloat()
    }

    /** Left edge of each painting; the first is centred. */
    val starts = FloatArray(count)

    /** One time round the whole hang. */
    val lap: Float

    init {
        var s = -widths[0] / 2f
        for (i in 0 until count) {
            starts[i] = s
            s += widths[i] + GAP
        }
        lap = s - starts[0]
    }

    /** Left edge of painting [index], unwrapped. */
    fun start(index: Int): Float = floorDiv(index, count) * lap + starts[wrap(index)]

    fun centre(index: Int): Float = start(index) + widths[wrap(index)] / 2f

    fun wrap(index: Int): Int = ((index % count) + count) % count

    /** The painting whose middle is nearest [position], unwrapped. */
    fun nearest(position: Float): Int {
        val laps = floor((position - starts[0]) / lap).toInt()
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (i in laps * count - 1..(laps + 1) * count) {
            val d = abs(centre(i) - position)
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return best
    }
}

private fun floorDiv(a: Int, b: Int): Int = floor(a.toFloat() / b).toInt()

// --- the room ----------------------------------------------------------------

/** The camera on the room, worked out once per size. */
private class Room {
    var width = -1f
    var height = -1f
    var focal = 1f
    var cx = 0f
    var cy = 0f

    /** Screen pixels to a card height, on the back wall. */
    var backPx = 1f

    /** How far along the wall either way anything can be seen. */
    var reach = 0f

    private val point = FloatArray(2)

    fun fit(w: Float, h: Float) {
        if (w == width && h == height) return
        width = w
        height = h
        focal = min(FOCAL * w, FOCAL_PER_HEIGHT * h)
        cx = w / 2f
        cy = h * HORIZON
        backPx = focal / DEPTH
        // Where the side walls leave the frame, with a margin past it.
        val leaves = focal * HALF_WIDTH / max(w / 2f, 1f) * 0.9f
        reach = FLAT + ARC + (DEPTH - CORNER - max(leaves, NEAR))
    }

    /** The painting under a tap, unwrapped, or null. */
    fun hit(x: Float, y: Float, position: Float, hang: Hang): Int? {
        val s = along(x - cx) ?: return null
        wallPoint(s, point)
        val z = point[1]
        val here = s + position
        val laps = floor((here - hang.starts[0]) / hang.lap).toInt()
        for (i in laps * hang.count - 1..(laps + 1) * hang.count) {
            val left = hang.start(i)
            val index = hang.wrap(i)
            if (here < left || here > left + hang.widths[index]) continue
            val half = focal * hang.heights[index] / 2f / z
            return if (abs(y - cy) <= half) i else null
        }
        return null
    }

    /** How far along the wall the column [u] pixels off the middle looks, or null past the side walls. */
    private fun along(u: Float): Float? {
        val a = abs(u) / focal
        val z: Float = when {
            a * DEPTH <= FLAT -> DEPTH
            a > 0f && HALF_WIDTH / a <= DEPTH - CORNER -> HALF_WIDTH / a
            else -> {
                // Far crossing of the ray x = a z with the corner's circle.
                val ox = FLAT
                val oz = DEPTH - CORNER
                val qa = a * a + 1f
                val qb = -2f * (a * ox + oz)
                val qc = ox * ox + oz * oz - CORNER * CORNER
                val disc = qb * qb - 4f * qa * qc
                if (disc < 0f) return null
                (-qb + sqrt(disc)) / (2f * qa)
            }
        }
        val x = a * z
        val s = when {
            z >= DEPTH -> x
            x >= HALF_WIDTH -> FLAT + ARC + (DEPTH - CORNER - z)
            else -> FLAT + CORNER * atan2(x - FLAT, z - (DEPTH - CORNER))
        }
        return if (u < 0f) -s else s
    }
}

/** The wall at [s] along it, from the middle of the back wall: x and depth into [out]. */
private fun wallPoint(s: Float, out: FloatArray) {
    val a = abs(s)
    val x: Float
    val z: Float
    when {
        a <= FLAT -> {
            x = a
            z = DEPTH
        }
        a <= FLAT + ARC -> {
            val turn = (a - FLAT) / CORNER
            x = FLAT + CORNER * sin(turn)
            z = DEPTH - CORNER + CORNER * cos(turn)
        }
        else -> {
            x = HALF_WIDTH
            z = DEPTH - CORNER - (a - FLAT - ARC)
        }
    }
    out[0] = if (s < 0f) -x else x
    out[1] = z
}

// --- drawing -----------------------------------------------------------------

private fun DrawScope.drawWall(art: WallArt, hang: Hang, room: Room, position: Float) {
    if (size.width <= 0f || size.height <= 0f) return
    val reach = room.reach
    val laps = floor((position - reach - hang.starts[0]) / hang.lap).toInt()
    var i = laps * hang.count - 1
    // The wall is seen from inside, so nothing on it covers anything else and
    // the order does not matter.
    while (true) {
        val left = hang.start(i) - position
        if (left > reach) break
        val card = art.cards[hang.wrap(i)]
        if (left + card.wallWidth >= -reach) drawCard(card, left, room)
        i++
    }
}

private val matrix = Matrix()
private val quad = FloatArray(8)
private val p0 = FloatArray(2)
private val p1 = FloatArray(2)

/** Breaks along the wall where it changes from flat to curved. */
private val bends = floatArrayOf(-FLAT - ARC, -FLAT, FLAT, FLAT + ARC)

/**
 * One painting whose bitmap's left edge is [left] along the wall: cut where
 * the wall bends, each flat stretch through one exact projection and each
 * curved one in chords.
 */
private fun DrawScope.drawCard(card: CardArt, left: Float, room: Room) {
    val from = max(left, -room.reach)
    val to = min(left + card.wallWidth, room.reach)
    if (to <= from) return
    var a = from
    while (a < to) {
        var b = to
        for (k in bends) if (k > a + 1e-5f && k < b) b = k
        val curved = abs((a + b) / 2f) in FLAT..(FLAT + ARC)
        val pieces = if (curved) max(1, ceil((b - a) / (ARC / ARC_CHORDS)).toInt()) else 1
        for (n in 0 until pieces) {
            drawPiece(card, left, a + (b - a) * n / pieces, a + (b - a) * (n + 1) / pieces, room)
        }
        a = b
    }
}

/** Columns of overlap between neighbouring pieces, or the seams show. */
private const val OVERLAP = 1

private fun DrawScope.drawPiece(card: CardArt, left: Float, a: Float, b: Float, room: Room) {
    // On whole columns of the bitmap, so neighbours meet exactly.
    val c0 = ((a - left) * card.pxPerUnit).roundToInt().coerceIn(0, card.bitmap.width)
    val c1 = ((b - left) * card.pxPerUnit).roundToInt().coerceIn(0, card.bitmap.width)
    if (c1 <= c0) return
    wallPoint(left + c0 / card.pxPerUnit, p0)
    wallPoint(left + c1 / card.pxPerUnit, p1)
    // The overlap runs on along the same flat stretch, back towards the last piece.
    val start = max(c0 - OVERLAP, 0)
    val back = (c0 - start).toFloat() / (c1 - c0)
    val x0 = p0[0] - (p1[0] - p0[0]) * back
    val z0 = p0[1] - (p1[1] - p0[1]) * back
    val x1 = p1[0]
    val z1 = p1[1]
    if (z0 <= 0.05f || z1 <= 0.05f) return

    val f = room.focal
    val top = card.top
    val bottom = card.top + card.bitmap.height / card.pxPerUnit
    quad[0] = room.cx + f * x0 / z0; quad[1] = room.cy + f * top / z0
    quad[2] = room.cx + f * x1 / z1; quad[3] = room.cy + f * top / z1
    quad[4] = room.cx + f * x1 / z1; quad[5] = room.cy + f * bottom / z1
    quad[6] = room.cx + f * x0 / z0; quad[7] = room.cy + f * bottom / z0

    val w = c1 - start
    val h = card.bitmap.height
    if (!quadMatrix(quad, w.toFloat(), h.toFloat(), matrix)) return
    withTransform({ transform(matrix) }) {
        drawImage(
            image = card.bitmap,
            srcOffset = IntOffset(start, 0),
            srcSize = IntSize(w, h),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(w, h),
            filterQuality = FilterQuality.Medium,
        )
    }
}

/**
 * The projective map taking a [w] by [h] picture onto four screen corners,
 * written into [out] — the same construction as Lift Stack's. Rows are the
 * input axes.
 */
private fun quadMatrix(q: FloatArray, w: Float, h: Float, out: Matrix): Boolean {
    val x0 = q[0]; val y0 = q[1]
    val x1 = q[2]; val y1 = q[3]
    val x2 = q[4]; val y2 = q[5]
    val x3 = q[6]; val y3 = q[7]
    val sx = x0 - x1 + x2 - x3
    val sy = y0 - y1 + y2 - y3
    val dx1 = x1 - x2
    val dx2 = x3 - x2
    val dy1 = y1 - y2
    val dy2 = y3 - y2
    val den = dx1 * dy2 - dx2 * dy1
    if (abs(den) < 1e-6f) return false
    val g = (sx * dy2 - dx2 * sy) / den
    val k = (dx1 * sy - sx * dy1) / den
    out.reset()
    out[0, 0] = (x1 - x0 + g * x1) / w
    out[0, 1] = (y1 - y0 + g * y1) / w
    out[0, 3] = g / w
    out[1, 0] = (x3 - x0 + k * x3) / h
    out[1, 1] = (y3 - y0 + k * y3) / h
    out[1, 3] = k / h
    out[3, 0] = x0
    out[3, 1] = y0
    out[3, 3] = 1f
    return true
}

// --- art ---------------------------------------------------------------------

/** A painting with its label under it, square on, as hung. */
private class CardArt(
    val bitmap: ImageBitmap,
    /** Bitmap pixels to a card height, both ways. */
    val pxPerUnit: Float,
    /** Bitmap top, above the eye level, in card heights (negative is up). */
    val top: Float,
) {
    /** How far along the wall the bitmap runs, label included. */
    val wallWidth: Float get() = bitmap.width / pxPerUnit
}

/** Every painting drawn once, rebuilt only when they change size on screen. */
private class WallArt(
    private val items: List<GalleryWallItem>,
    private val ink: Color,
    private val measurer: TextMeasurer,
) {
    var cards: List<CardArt> = emptyList()
        private set

    private var builtFor = -1

    fun ensure(pxPerUnit: Float) {
        val px = pxPerUnit.roundToInt().coerceAtLeast(8)
        if (px == builtFor) return
        builtFor = px
        val unit = px.toFloat()
        val density = Density(1f)
        val title = TextStyle(
            color = ink,
            fontSize = (LABEL_SIZE * unit).sp,
            lineHeight = (LABEL_LINE * unit).sp,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
        )
        val rest = title.copy(color = ink.copy(alpha = ink.alpha * LABEL_GREY), fontStyle = FontStyle.Normal)

        cards = items.map { item ->
            val paintH = (item.scale * unit).roundToInt().coerceAtLeast(1)
            val paintW = (paintH * item.image.width / max(item.image.height, 1).toFloat()).roundToInt().coerceAtLeast(1)
            val lines = listOf(item.title to title, item.artist to rest, item.date to rest, item.medium to rest)
                .filter { it.first.isNotEmpty() }
                .map { (text, style) -> measurer.measure(text, style, density = density) }
            val labelW = lines.maxOfOrNull { it.size.width } ?: 0
            val labelTop = paintH + LABEL_GAP * unit
            val labelH = if (lines.isEmpty()) 0f else LABEL_GAP * unit + lines.size * LABEL_LINE * unit
            val bw = max(paintW, labelW + 1)
            val bh = ceil(paintH + labelH).toInt()

            val bitmap = ImageBitmap(bw, bh)
            CanvasDrawScope().draw(
                density,
                LayoutDirection.Ltr,
                BitmapCanvas(bitmap),
                Size(bw.toFloat(), bh.toFloat()),
            ) {
                drawImage(
                    image = item.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(item.image.width, item.image.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(paintW, paintH),
                    filterQuality = FilterQuality.High,
                )
                lines.forEachIndexed { n, layout ->
                    drawText(layout, topLeft = Offset(0f, labelTop + n * LABEL_LINE * unit))
                }
            }
            // Painting widths along the wall come from its own pixels, so the
            // hang and the bitmap agree to the column.
            CardArt(bitmap, paintH / item.scale, top = -item.scale / 2f)
        }
    }
}
