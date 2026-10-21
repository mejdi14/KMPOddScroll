package io.github.mejdi14.oddscroll.gallerywall

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as BitmapCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.ui.unit.dp
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

/**
 * A gilt frame round every painting: a moulding lit from the upper left, a
 * linen liner inside it with a gold slip at the canvas, and a soft shadow
 * thrown down and to the right onto the wall. Sizes are in heights of the
 * tallest painting; the frame hangs outside the canvas, so a framed painting
 * stands that much bigger on the wall.
 */
data class GalleryWallFrame(
    val width: Float = 0.07f,
    val color: Color = Color(0xFFC29B47),
    val linerWidth: Float = 0.028f,
    /** [Color.Transparent] for none, which leaves the moulding against the canvas. */
    val liner: Color = Color(0xFFEDE5D3),
    /** How far the shadow falls; 0 for none. */
    val shadow: Float = 0.022f,
) {
    internal val border: Float get() = width + if (liner.alpha > 0f) linerWidth else 0f
}

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

/*
 * Walking up to a painting. The eye goes straight in along the middle of the
 * room until the painting fills most of the frame, at a visitor's unhurried
 * pace: a slow first step, a glide, and a long ease to a stop. The steps
 * themselves show as a bob and a sway of the head, and a little roll with
 * each, fading in as the walk gets going and out as it arrives.
 */

/** How much of the frame the painting fills, across and down, once there. */
private const val FILL_WIDTH = 0.86f
private const val FILL_HEIGHT = 0.7f

private val WALK_EASE = CubicBezierEasing(0.45f, 0f, 0.15f, 1f)

/** Footfalls on the way. */
private const val STEPS = 4

/**
 * The room is first seen from this far back, in card heights, and walked into
 * the same way, over a few more steps.
 */
private const val ENTER_FROM = 3.5f
private const val ENTRY_STEPS = 5

/** The head's rise each step, and its sway to either side each stride, in card heights. */
private const val BOB = 0.03f
private const val SWAY = 0.02f

/** The head's roll with the sway, in degrees. */
private const val ROLL = 0.35f

/** [walkIn] starts the eye back in the doorway, and walks it into the room. */
@Composable
fun rememberGalleryWallScrollState(walkIn: Boolean = true): GalleryWallScrollState =
    remember { GalleryWallScrollState(walkIn) }

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
 * Drag along it, or scroll, and the hang springs after, coming to rest with
 * a painting in the middle; tap a painting to bring it to the middle. Once
 * the hang has stood still for two seconds, [inspectLabel] offers to walk up to
 * the painting in the middle; a tap anywhere steps back. The room opens with
 * a walk in from the doorway, unless the state was made without one.
 *
 * @param items paintings, in order; the hang repeats, so it has no end.
 * @param background the wall.
 * @param contentColor the labels.
 * @param frame the frame round each painting, or null to hang them bare.
 * @param inspectLabel the button that walks up to a painting, or null for
 * none — a host can show its own off [GalleryWallScrollState.canInspect].
 * @param state also lets a host's own controls move it.
 */
@Composable
fun GalleryWallScroll(
    items: List<GalleryWallItem>,
    modifier: Modifier = Modifier,
    background: Color = Color(0xFFFFFFFF),
    contentColor: Color = Color(0xFF1A1A1A),
    frame: GalleryWallFrame? = null,
    inspectLabel: String? = "Inspect",
    state: GalleryWallScrollState = rememberGalleryWallScrollState(),
) {
    if (items.isEmpty()) {
        Box(modifier)
        return
    }

    val measurer = rememberTextMeasurer()
    val hang = remember(items, frame) { Hang(items, frame) }
    val art = remember(items, contentColor, frame, measurer) { WallArt(items, contentColor, frame, measurer) }
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

    Box(modifier.background(background).fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        state.press()
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
                        state.release(dragging)
                        if (state.inspecting) {
                            state.leave()
                        } else if (!dragging) {
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
            val subject = state.subject
            room.stroll(state.entry, state.walk, hang, subject)
            // The painting up close is drawn again at the size it will end at,
            // as soon as it can be walked up to, so the walk does not start
            // on a dropped frame.
            val close = if (state.canInspect || state.walk > 0f) art.close(hang.wrap(subject), room.closePx) else null
            rotate(room.roll, Offset(room.cx, room.cy)) {
                drawWall(art, hang, room, state.position, close.takeIf { room.zoom > BITMAP_SCALE }, subject)
            }
        }

        if (inspectLabel != null) {
            InspectButton(
                label = inspectLabel,
                state = state,
                background = background,
                contentColor = contentColor,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
            )
        }
    }
}

@Composable
private fun InspectButton(
    label: String,
    state: GalleryWallScrollState,
    background: Color,
    contentColor: Color,
    modifier: Modifier,
) {
    val shown = state.canInspect
    val reveal by animateFloatAsState(if (shown) 1f else 0f, tween(if (shown) 450 else 200))
    val shape = RoundedCornerShape(50)
    BasicText(
        text = label,
        style = TextStyle(
            color = contentColor,
            fontSize = 15.sp,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            letterSpacing = 0.6.sp,
        ),
        modifier = modifier
            .graphicsLayer {
                alpha = reveal
                translationY = (1f - reveal) * 10.dp.toPx()
            }
            // Only while it shows, or it would take taps meant for the wall.
            .then(if (shown) Modifier.pointerInput(state) { detectTapGestures { state.inspect() } } else Modifier)
            .background(background.copy(alpha = 0.9f), shape)
            .border(1.dp, contentColor.copy(alpha = 0.5f), shape)
            .padding(horizontal = 26.dp, vertical = 11.dp),
    )
}

// --- the hang ----------------------------------------------------------------

/** Where each painting hangs along the wall, in card heights, with the hang at rest; frames included. */
internal class Hang(items: List<GalleryWallItem>, frame: GalleryWallFrame? = null) {
    val count = items.size
    private val border = 2f * (frame?.border ?: 0f)
    val heights = FloatArray(count) { i -> items[i].scale + border }
    val widths = FloatArray(count) { i ->
        val image = items[i].image
        items[i].scale * image.width / max(image.height, 1).toFloat() + border
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

    /** Where the side walls are first seen from, ahead of the eye. */
    private var sideFrom = 0f

    /** The eye, walked forward from where the room is seen from: across, up and in. */
    var eyeX = 0f
    var eyeY = 0f
    var eyeZ = 0f

    /** Its roll, in degrees. */
    var roll = 0f

    /** How much bigger the back wall looks than from where the walk began. */
    var zoom = 1f

    /** Bitmap pixels to a card height that the painting walked up to wants. */
    var closePx = 1f

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
        sideFrom = max(leaves, NEAR)
        eyeZ = Float.NaN
    }

    /** Puts the eye [entry] of the way into the room and [walk] of the way up to painting [index]. */
    fun stroll(entry: Float, walk: Float, hang: Hang, index: Int) {
        val i = hang.wrap(index)
        // Near enough that the painting fills the frame one way or the other.
        val stand = max(
            focal * hang.widths[i] / (FILL_WIDTH * width),
            focal * hang.heights[i] / (FILL_HEIGHT * height),
        ).coerceAtMost(DEPTH)
        closePx = focal / stand * 1.05f
        val z = (DEPTH - stand) * WALK_EASE.transform(walk) - ENTER_FROM * (1f - WALK_EASE.transform(entry))
        // The two walks never overlap, so their steps simply add.
        eyeX = 0f
        eyeY = 0f
        roll = 0f
        step(walk, STEPS)
        step(entry, ENTRY_STEPS)
        if (z == eyeZ) return
        eyeZ = z
        zoom = DEPTH / (DEPTH - z)
        // The side walls leave the frame as far ahead of the eye as ever, so
        // further down the room; walked far enough, they are out of it.
        reach = FLAT + ARC + max(0f, DEPTH - CORNER - (sideFrom + z))
    }

    /** Adds the head's bob, sway and roll at [p] of a walk of [steps] footfalls. */
    private fun step(p: Float, steps: Int) {
        val envelope = sin(PI.toFloat() * p)
        if (envelope <= 0f) return
        val stride = PI.toFloat() * steps * p
        eyeX += SWAY * envelope * sin(stride)
        eyeY -= BOB * envelope * sin(stride) * sin(stride)
        roll += ROLL * envelope * sin(stride)
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

/** [close], when there is one, stands in for painting [closeIndex] (unwrapped). */
private fun DrawScope.drawWall(art: WallArt, hang: Hang, room: Room, position: Float, close: CardArt?, closeIndex: Int) {
    if (size.width <= 0f || size.height <= 0f) return
    val reach = room.reach
    val laps = floor((position - reach - hang.starts[0]) / hang.lap).toInt()
    var i = laps * hang.count - 1
    // The wall is seen from inside, so nothing on it covers anything else and
    // the order does not matter.
    while (true) {
        val left = hang.start(i) - position
        if (left > reach) break
        val card = if (close != null && i == closeIndex) close else art.cards[hang.wrap(i)]
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
    // From the eye, wherever the walk has taken it.
    val x0 = p0[0] - (p1[0] - p0[0]) * back - room.eyeX
    val z0 = p0[1] - (p1[1] - p0[1]) * back - room.eyeZ
    val x1 = p1[0] - room.eyeX
    val z1 = p1[1] - room.eyeZ
    if (z0 <= 0.05f || z1 <= 0.05f) return

    val f = room.focal
    val top = card.top - room.eyeY
    val bottom = top + card.bitmap.height / card.pxPerUnit
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
    private val frame: GalleryWallFrame?,
    private val measurer: TextMeasurer,
) {
    var cards: List<CardArt> = emptyList()
        private set

    private var builtFor = -1

    private var closeCard: CardArt? = null
    private var closeIndex = -1
    private var closeFor = -1

    fun ensure(pxPerUnit: Float) {
        val px = pxPerUnit.roundToInt().coerceAtLeast(8)
        if (px == builtFor) return
        builtFor = px
        cards = items.map { build(it, px.toFloat()) }
    }

    /**
     * Painting [index] again at [pxPerUnit], for walking up to — no finer than
     * its own image, which would only blur it twice.
     */
    fun close(index: Int, pxPerUnit: Float): CardArt {
        val item = items[index]
        val px = min(pxPerUnit, item.image.height / item.scale).roundToInt().coerceAtLeast(8)
        val cached = closeCard
        if (cached != null && index == closeIndex && px == closeFor) return cached
        closeIndex = index
        closeFor = px
        return build(item, px.toFloat()).also { closeCard = it }
    }

    private fun build(item: GalleryWallItem, unit: Float): CardArt {
        val density = Density(1f)
        val title = TextStyle(
            color = ink,
            fontSize = (LABEL_SIZE * unit).sp,
            lineHeight = (LABEL_LINE * unit).sp,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
        )
        val rest = title.copy(color = ink.copy(alpha = ink.alpha * LABEL_GREY), fontStyle = FontStyle.Normal)
        val paintH = (item.scale * unit).roundToInt().coerceAtLeast(1)
        val paintW = (paintH * item.image.width / max(item.image.height, 1).toFloat()).roundToInt().coerceAtLeast(1)
        val border = frame?.let { (it.border * unit).roundToInt() } ?: 0
        val shadow = frame?.let { (it.shadow * unit).roundToInt() } ?: 0
        val outerW = paintW + 2 * border
        val outerH = paintH + 2 * border
        val lines = listOf(item.title to title, item.artist to rest, item.date to rest, item.medium to rest)
            .filter { it.first.isNotEmpty() }
            .map { (text, style) -> measurer.measure(text, style, density = density) }
        val labelW = lines.maxOfOrNull { it.size.width } ?: 0
        val labelTop = outerH + LABEL_GAP * unit
        val labelH = if (lines.isEmpty()) 0f else LABEL_GAP * unit + lines.size * LABEL_LINE * unit
        val bw = max(outerW + 2 * shadow, labelW + 1)
        val bh = ceil(max(outerH + 2f * shadow, outerH + labelH)).toInt()

        val bitmap = ImageBitmap(bw, bh)
        CanvasDrawScope().draw(
            density,
            LayoutDirection.Ltr,
            BitmapCanvas(bitmap),
            Size(bw.toFloat(), bh.toFloat()),
        ) {
            if (frame != null) drawShadow(outerW.toFloat(), outerH.toFloat(), shadow.toFloat())
            drawImage(
                image = item.image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(item.image.width, item.image.height),
                dstOffset = IntOffset(border, border),
                dstSize = IntSize(paintW, paintH),
                filterQuality = FilterQuality.High,
            )
            if (frame != null) drawFrame(frame, outerW.toFloat(), outerH.toFloat(), border.toFloat(), unit)
            lines.forEachIndexed { n, layout ->
                drawText(layout, topLeft = Offset(0f, labelTop + n * LABEL_LINE * unit))
            }
        }
        // Painting widths along the wall come from its own pixels, so the
        // hang and the bitmap agree to the column.
        val pxPerUnit = paintH / item.scale
        return CardArt(bitmap, pxPerUnit, top = -outerH / 2f / pxPerUnit)
    }
}

// --- the frame ---------------------------------------------------------------

/*
 * The moulding's profile, outer edge to inner, as a share of the base gold:
 * a dark outer lip, a broad lit round, a shadowed cove and a bright bead by
 * the liner.
 */
private val PROFILE = listOf(
    0.00f to -0.55f,
    0.07f to -0.20f,
    0.24f to 0.45f,
    0.42f to 0.05f,
    0.62f to -0.40f,
    0.80f to -0.05f,
    0.89f to 0.40f,
    1.00f to -0.45f,
)

/** Black over each side of the moulding — top, right, bottom, left — for a light from the upper left. */
private val SIDE_SHADE = floatArrayOf(0f, 0.22f, 0.32f, 0.08f)

private fun shade(color: Color, by: Float): Color =
    if (by >= 0f) lerp(color, Color.White, by) else lerp(color, Color.Black, -by)

/** A soft shadow for a [w] by [h] frame, thrown [reach] down and to the right. */
private fun DrawScope.drawShadow(w: Float, h: Float, reach: Float) {
    if (reach < 1f) return
    val layers = 10
    // Stacked, growing, translucent: darkest under the frame, fading outwards.
    for (k in 1..layers) {
        val spread = reach * k / layers
        drawRect(
            color = Color.Black.copy(alpha = 0.32f / layers),
            topLeft = Offset(reach - spread, reach - spread),
            size = Size(w + 2f * spread, h + 2f * spread),
        )
    }
}

/** The frame on a [w] by [h] bitmap, [border] deep all round, at [unit] pixels to a card height. */
private fun DrawScope.drawFrame(frame: GalleryWallFrame, w: Float, h: Float, border: Float, unit: Float) {
    val mould = min(frame.width * unit, border)
    val stops = PROFILE.map { (at, by) -> at to shade(frame.color, by) }.toTypedArray()
    // Each side a mitred trapezoid, lit across its width from outer edge to inner.
    for (side in 0..3) {
        val path = Path()
        val (outerA, outerB, innerB, innerA) = when (side) {
            0 -> listOf(Offset(0f, 0f), Offset(w, 0f), Offset(w - mould, mould), Offset(mould, mould))
            1 -> listOf(Offset(w, 0f), Offset(w, h), Offset(w - mould, h - mould), Offset(w - mould, mould))
            2 -> listOf(Offset(w, h), Offset(0f, h), Offset(mould, h - mould), Offset(w - mould, h - mould))
            else -> listOf(Offset(0f, h), Offset(0f, 0f), Offset(mould, mould), Offset(mould, h - mould))
        }
        path.moveTo(outerA.x, outerA.y)
        path.lineTo(outerB.x, outerB.y)
        path.lineTo(innerB.x, innerB.y)
        path.lineTo(innerA.x, innerA.y)
        path.close()
        val start = when (side) {
            0 -> Offset(0f, 0f)
            1 -> Offset(w, 0f)
            2 -> Offset(0f, h)
            else -> Offset(0f, 0f)
        }
        val end = when (side) {
            0 -> Offset(0f, mould)
            1 -> Offset(w - mould, 0f)
            2 -> Offset(0f, h - mould)
            else -> Offset(mould, 0f)
        }
        drawPath(path, Brush.linearGradient(*stops, start = start, end = end))
        if (SIDE_SHADE[side] > 0f) drawPath(path, Color.Black.copy(alpha = SIDE_SHADE[side]))
    }

    val liner = border - mould
    if (liner >= 1f && frame.liner.alpha > 0f) {
        val inW = w - 2f * mould
        val inH = h - 2f * mould
        val canvasW = w - 2f * border
        val canvasH = h - 2f * border
        // The linen, as a ring, so the canvas inside it is left alone.
        val ring = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(mould, mould, mould + inW, mould + inH))
            addRect(Rect(border, border, border + canvasW, border + canvasH))
        }
        drawPath(ring, frame.liner)
        // Shadowed under the moulding's top and left.
        val fall = liner * 0.6f
        drawRect(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent), mould, mould + fall),
            topLeft = Offset(mould, mould),
            size = Size(inW, fall),
        )
        drawRect(
            Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.14f), Color.Transparent), mould, mould + fall),
            topLeft = Offset(mould, mould),
            size = Size(fall, inH),
        )
        // The gold slip at the canvas edge.
        val slip = max(1f, 0.004f * unit)
        drawRect(
            color = shade(frame.color, 0.1f),
            topLeft = Offset(border - slip / 2f, border - slip / 2f),
            size = Size(canvasW + slip, canvasH + slip),
            style = Stroke(slip),
        )
    }

    // The inner lip throws a little shadow onto the canvas itself.
    val lip = max(1f, 0.012f * unit)
    drawRect(
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent), border, border + lip),
        topLeft = Offset(border, border),
        size = Size(w - 2f * border, lip),
    )
    drawRect(
        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent), border, border + lip),
        topLeft = Offset(border, border),
        size = Size(lip, h - 2f * border),
    )
}
