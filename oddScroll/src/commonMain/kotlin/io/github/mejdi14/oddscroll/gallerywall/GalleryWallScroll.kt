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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as BitmapCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
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
import kotlin.random.Random

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
 * A wooden frame round every painting, and the painting made a thing that
 * hangs off the wall rather than a print pasted to it: the framed face stands
 * [depth] out, its edges show as the wall turns away from the eye, and it
 * throws a soft shadow down and to the right onto the wall behind. The moulding
 * is four mitred sticks, each with its own grain running along it, under a
 * satin finish lit from the upper left. The painting sits [recess] back inside
 * it, behind [glass], so the inside of the moulding shows round it as the wall
 * turns away. Sizes are in heights of the tallest painting — call that a metre,
 * and the default stands four and a half centimetres deep, room for the glass,
 * a gap behind it, the canvas and its backing. The frame hangs outside the
 * canvas, so a framed painting stands that much bigger.
 */
data class GalleryWallFrame(
    val width: Float = 0.06f,
    /** The wood, before the grain and the light; walnut by default. */
    val color: Color = Color(0xFF7B4F2C),
    /** How far the face stands off the wall; 0 hangs it flat. */
    val depth: Float = 0.045f,
    /** How far the shadow falls; 0 for none. */
    val shadow: Float = 0.04f,
    /** How strongly the grain shows; 0 for a smooth painted or gilt finish. */
    val grain: Float = 1f,
    /** How far the painting sits back from the face, up to [depth]; 0 for flush. */
    val recess: Float = 0.015f,
    /**
     * A pane over the painting, flush with the face. It throws back the wall a
     * little square on and more and more as the wall turns away, and the lit
     * doorway behind you shows in it as a soft glow that stays put while the
     * paintings slide under it. Where the platform has the shader for it, the
     * pane is liquid glass whose rounded edges, hidden under the moulding, bend
     * the painting under them: walked up to, the painting comes out of its
     * frame to show them, and a drag slides the glass about over it.
     */
    val glass: Boolean = true,
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

/** Behind liquid glass, the frame and the label come off between these two points of the eased walk. */
private const val BARE_FROM = 0.5f
private const val BARE_TO = 0.85f

/**
 * Nearly there, the paintings either side slide away along the walls, this
 * many card heights, between these two points of the walk in time — round the
 * corners and down the side walls, out of the frame.
 */
private const val PART = 2.5f
private const val PART_FROM = 0.35f
private const val PART_TO = 0.9f

/**
 * The painting walked up to comes off the wall's curve and hangs flat, square
 * to the eye, over this first stretch of the walk in time.
 */
private const val FLATTEN_TO = 0.25f

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
 * the painting in the middle; a tap anywhere steps back. Behind liquid glass
 * the painting comes out of its frame on the way, and a drag slides the glass
 * about over it. The room opens with a walk in from the doorway, unless the
 * state was made without one.
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
    val liquid = frame?.glass == true && shadedGlass != null
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
                .pointerInput(state, liquid) {
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
                            val dy = change.position.y - last.y
                            travel += abs(dx) + abs(dy)
                            last = change.position
                            if (travel > TAP_SLOP) dragging = true
                            if (dragging) {
                                if (!state.inspecting) {
                                    // The back wall follows the finger, pixel for pixel.
                                    state.nudge(-dx / room.backPx)
                                } else if (liquid) {
                                    // And up close, the glass does.
                                    state.slideGlass(dx / room.nearPx, dy / room.nearPx)
                                }
                                change.consume()
                            }
                        }
                        state.release(dragging)
                        if (state.inspecting) {
                            if (!dragging || !liquid) state.leave()
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
            // and out of its frame, as soon as it can be walked up to, so the
            // walk does not start on a dropped frame.
            val close = if (state.canInspect || state.walk > 0f) {
                art.cards[hang.wrap(subject)].bare
                art.close(hang.wrap(subject), room.closePx).also { it.bare }
            } else {
                null
            }
            rotate(room.roll, Offset(room.cx, room.cy)) {
                drawWall(
                    art, hang, room, state.position, close.takeIf { room.zoom > BITMAP_SCALE }, subject, background,
                    state.glassX, state.glassY,
                )
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
    private val border = 2f * (frame?.width ?: 0f)
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

    /**
     * How much of the doorway shows in the glass: all of it from across the
     * room, none once walked up, where the visitor stands in its way.
     */
    var glare = 1f

    /** How far the painting walked up to has come out of its frame, 0 to 1. */
    var bare = 0f

    /** How far the paintings either side of it have slid away, in card heights. */
    var part = 0f

    /** How far it has come off the wall's curve, 0 to 1. */
    var flat = 0f

    /** Screen pixels to a card height on the back wall, from where the eye is now. */
    var nearPx = 1f

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
        val eased = WALK_EASE.transform(walk)
        glare = 1f - eased
        val off = ((eased - BARE_FROM) / (BARE_TO - BARE_FROM)).coerceIn(0f, 1f)
        bare = off * off * (3f - 2f * off)
        val away = ((walk - PART_FROM) / (PART_TO - PART_FROM)).coerceIn(0f, 1f)
        part = PART * away * away * (3f - 2f * away)
        val straight = (walk / FLATTEN_TO).coerceIn(0f, 1f)
        flat = straight * straight * (3f - 2f * straight)
        val z = (DEPTH - stand) * eased - ENTER_FROM * (1f - WALK_EASE.transform(entry))
        // The two walks never overlap, so their steps simply add.
        eyeX = 0f
        eyeY = 0f
        roll = 0f
        step(walk, STEPS)
        step(entry, ENTRY_STEPS)
        if (z == eyeZ) return
        eyeZ = z
        zoom = DEPTH / (DEPTH - z)
        nearPx = focal / (DEPTH - z)
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

/**
 * The wall at [s] along it, from the middle of the back wall, or [inset] in
 * from it towards the room: x and depth into [out].
 */
private fun wallPoint(s: Float, out: FloatArray, inset: Float = 0f) {
    val a = abs(s)
    val x: Float
    val z: Float
    when {
        a <= FLAT -> {
            x = a
            z = DEPTH - inset
        }
        a <= FLAT + ARC -> {
            val turn = (a - FLAT) / CORNER
            val r = CORNER - inset
            x = FLAT + r * sin(turn)
            z = DEPTH - CORNER + r * cos(turn)
        }
        else -> {
            x = HALF_WIDTH - inset
            z = DEPTH - CORNER - (a - FLAT - ARC)
        }
    }
    out[0] = if (s < 0f) -x else x
    out[1] = z
}

/** The wall's normal at [s] along it, pointing into the room: x and depth into [out]. */
private fun wallNormal(s: Float, out: FloatArray) {
    val a = abs(s)
    val x: Float
    val z: Float
    when {
        a <= FLAT -> {
            x = 0f
            z = -1f
        }
        a <= FLAT + ARC -> {
            val turn = (a - FLAT) / CORNER
            x = -sin(turn)
            z = -cos(turn)
        }
        else -> {
            x = -1f
            z = 0f
        }
    }
    out[0] = if (s < 0f) -x else x
    out[1] = z
}

/**
 * How far the sheets being drawn are taken off the wall's curve and laid flat
 * across the back wall's plane, square to the eye.
 */
private var flat = 0f

/** [wallPoint], [flat] of the way to the back wall's plane. */
private fun sheetPoint(s: Float, out: FloatArray, inset: Float = 0f) {
    wallPoint(s, out, inset)
    if (flat <= 0f) return
    out[0] += (s - out[0]) * flat
    out[1] += (DEPTH - inset - out[1]) * flat
}

/** [wallNormal], [flat] of the way to the back wall's. */
private fun sheetNormal(s: Float, out: FloatArray) {
    wallNormal(s, out)
    if (flat <= 0f) return
    out[0] -= out[0] * flat
    out[1] += (-1f - out[1]) * flat
}

// --- drawing -----------------------------------------------------------------

/**
 * [close], when there is one, stands in for painting [closeIndex] (unwrapped),
 * the one walked up to, whose glass is slid [slideX], [slideY] card heights
 * and from which the rest slide away; [wall] is what the glass throws back.
 */
private fun DrawScope.drawWall(
    art: WallArt,
    hang: Hang,
    room: Room,
    position: Float,
    close: CardArt?,
    closeIndex: Int,
    wall: Color,
    slideX: Float,
    slideY: Float,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val reach = room.reach
    val laps = floor((position - reach - hang.starts[0]) / hang.lap).toInt()
    var i = laps * hang.count - 1
    // The wall is seen from inside, so nothing on it covers anything else and
    // the order does not matter.
    while (true) {
        var left = hang.start(i) - position
        if (left > reach) break
        val subject = i == closeIndex
        if (i < closeIndex) left -= room.part else if (i > closeIndex) left += room.part
        val card = if (close != null && subject) close else art.cards[hang.wrap(i)]
        if (left <= reach && left + card.wallWidth >= -reach) {
            // Only the painting walked up to comes out of its frame.
            val bare = if (subject && room.bare > 0f) card.bare else null
            if (subject) {
                flat = room.flat
                drawCard(card, left, room, wall, bare, if (bare != null) room.bare else 0f, slideX, slideY)
                flat = 0f
            } else {
                drawCard(card, left, room, wall)
            }
        }
        i++
    }
}

private val matrix = Matrix()
private val quad = FloatArray(8)
private val p0 = FloatArray(2)
private val p1 = FloatArray(2)
private val n0 = FloatArray(2)
private val n1 = FloatArray(2)

/** Breaks along the wall where it changes from flat to curved. */
private val bends = floatArrayOf(-FLAT - ARC, -FLAT, FLAT, FLAT + ARC)

/**
 * One painting whose left edge is [left] along the wall: what is on the wall
 * behind it, the painting sunk in the frame and the glass over it, an edge,
 * then the face. The edge goes down after the painting, which it stands in
 * front of; the face over the glass hides the glass's edges.
 *
 * [off] of the way out of its frame, the frame and the label fade and [bare],
 * the painting without them, shows through; its glass is slid [slideX], [slideY].
 */
private fun DrawScope.drawCard(
    card: CardArt,
    left: Float,
    room: Room,
    wall: Color,
    bare: ImageBitmap? = null,
    off: Float = 0f,
    slideX: Float = 0f,
    slideY: Float = 0f,
) {
    val px = card.pxPerUnit
    val on = 1f - off
    val sunk = card.depth - card.recess
    if (on > 0f) card.wall?.let { drawSheet(it, px, card.top, left, 0f, room, on) }
    val glass = if (card.glass) pane.aim(card, left, room, wall, bare ?: card.back, slideX, slideY, off) else null
    if (bare != null) drawSheet(bare, px, card.top, left, sunk, room, glaze = glass.takeIf { on <= 0f })
    if (on <= 0f) return
    card.back?.let { drawSheet(it, px, card.top, left, sunk, room, on, glass) }
    if (card.edges != null) drawEdges(card, card.edges, left, room, on)
    drawSheet(card.face, px, card.top, left, card.depth, room, on)
}

/*
 * The glass. It throws back the room by Schlick's approximation of Fresnel —
 * four percent square on, more and more as the wall turns away — and the room
 * it throws back is the wall across from it. The doorway the visitor came in
 * by, behind them and to the left, shows in it as a glow.
 */
private const val GLASS_SQUARE_ON = 0.04f
private const val GLASS_MOST = 0.5f

/** The doorway's middle, in the room. */
private const val DOOR_X = -0.9f
private const val DOOR_Y = -0.6f
private const val DOOR_Z = -ENTER_FROM - 1f

/** The doorway's glow on the glass: half its width and height in card heights, and how bright its middle. */
private const val GLARE_WIDE = 0.22f
private const val GLARE_TALL = 0.42f
private const val GLARE_ALPHA = 0.16f
private val GLARE = Color(0xFFFFFBF2)

/**
 * The liquid glass's bevel, in card heights, round a square-cornered pane. The
 * pane runs this far under the moulding, so its edge only shows with the frame
 * off.
 */
private const val LIQUID_BEVEL = 0.05f

/** At most this much of the moulding's width, so the bevel stays under it. */
private const val LIQUID_UNDER = 0.85f

/**
 * The bevel once out of the frame, in card heights. Nothing hides it then, so
 * it widens as the frame fades, in over the painting's edge, and bends more.
 */
private const val LIQUID_WIDE = 0.14f

/** The lit rim along the liquid glass's edge, in card heights, and at least this many pixels. */
private const val LIQUID_RIM = 0.0025f
private const val LIQUID_RIM_MIN = 1.5f

/** The glass over the painting being drawn, worked out once a painting; sizes in face-bitmap pixels. */
private class Glaze {
    var wall = Color.White

    /** The opening, in from each side, and the face's size. */
    var opening = 0f
    var width = 0f
    var height = 0f

    /** Pixels to a card height. */
    var unit = 1f

    /** The painting the liquid glass bends, in the same pixels as the face. */
    var image: ImageBitmap? = null

    /** The liquid pane: its middle, half-sizes and bevel, how much its edges show, and its rim. */
    var cx = 0f
    var cy = 0f
    var hw = 0f
    var hh = 0f
    var bevel = 1f
    var edges = 0f
    var rim = 1f

    /** The doorway's glow: its middle, half-sizes and strength — 0 when it is not on this pane. */
    var gx = 0f
    var gy = 0f
    var rx = 1f
    var ry = 1f
    var glow = 0f

    /**
     * Finds where the doorway shows on [card]'s glass. The pane is taken flat,
     * square to the wall at the painting's middle, so the glow does not break
     * where a painting bends round a corner.
     */
    fun aim(
        card: CardArt,
        left: Float,
        room: Room,
        wall: Color,
        image: ImageBitmap?,
        slideX: Float,
        slideY: Float,
        edges: Float,
    ): Glaze {
        val px = card.pxPerUnit
        this.wall = wall
        this.image = image
        opening = card.opening.toFloat()
        width = card.face.width.toFloat()
        height = card.face.height.toFloat()
        unit = px
        val hidden = min(LIQUID_BEVEL * px, opening * LIQUID_UNDER)
        val bevel = hidden + max(LIQUID_WIDE * px - hidden, 0f) * edges
        cx = width / 2f + slideX * px
        cy = height / 2f + slideY * px
        hw = width / 2f - opening + hidden
        hh = height / 2f - opening + hidden
        this.bevel = max(min(bevel, min(hw, hh)), 1f)
        this.edges = edges
        rim = max(LIQUID_RIM * px, LIQUID_RIM_MIN)
        glow = 0f
        if (room.glare <= 0f) return this
        val mid = left + card.face.width / px / 2f
        sheetPoint(mid, p0, card.depth)
        sheetNormal(mid, n0)
        val nx = n0[0]
        val nz = n0[1]
        val eye = (room.eyeX - p0[0]) * nx + (room.eyeZ - p0[1]) * nz
        val door = (DOOR_X - p0[0]) * nx + (DOOR_Z - p0[1]) * nz
        if (eye <= 0f || door <= 0f) return this
        // Where the line from the eye to the doorway's mirror image crosses the pane.
        val t = eye / (eye + door)
        val hx = room.eyeX + t * (DOOR_X - 2f * door * nx - room.eyeX)
        val hz = room.eyeZ + t * (DOOR_Z - 2f * door * nz - room.eyeZ)
        val hy = room.eyeY + t * (DOOR_Y - room.eyeY)
        val along = (hx - p0[0]) * -nz + (hz - p0[1]) * nx
        gx = (mid + along - left) * px
        gy = (hy - card.top) * px
        rx = GLARE_WIDE * px
        ry = GLARE_TALL * px
        glow = GLARE_ALPHA * room.glare
        return this
    }
}

private val pane = Glaze()

/** The platform's own glass, where it has one. */
private val shadedGlass: GlassShader? by lazy { glassShader() }

/** How much of the room glass throws back, met at the wall normal [nx], [nz] by a ray along [x], [z]. */
private fun reflectance(nx: Float, nz: Float, x: Float, z: Float): Float {
    val c = abs(nx * x + nz * z) / max(sqrt((nx * nx + nz * nz) * (x * x + z * z)), 1e-6f)
    val k = 1f - c.coerceAtMost(1f)
    return min(GLASS_SQUARE_ON + (1f - GLASS_SQUARE_ON) * k * k * k * k * k, GLASS_MOST)
}

/**
 * The glass over one piece of a painting, in the face's pixels, columns [from]
 * to [to] and rows [top] to [bottom] of them and [s0] to [s1] along the wall;
 * ([x0], [z0]) and ([x1], [z1]) are its ends from the eye. The wall is thrown
 * back at either end and the middle, so neighbouring pieces meet without a step.
 */
private fun DrawScope.drawGlass(
    glaze: Glaze,
    from: Int,
    to: Int,
    top: Int,
    bottom: Int,
    s0: Float,
    s1: Float,
    x0: Float,
    z0: Float,
    x1: Float,
    z1: Float,
) {
    sheetNormal(s0, n0)
    sheetNormal(s1, n1)
    val near = reflectance(n0[0], n0[1], x0, z0)
    val far = reflectance(n1[0], n1[1], x1, z1)
    val middle = reflectance(n0[0] + n1[0], n0[1] + n1[1], (x0 + x1) / 2f, (z0 + z1) / 2f)

    val shader = shadedGlass
    val image = glaze.image
    if (shader != null && image != null) {
        val l = max(glaze.cx - glaze.hw, from.toFloat())
        val r = min(glaze.cx + glaze.hw, to.toFloat())
        val t = max(glaze.cy - glaze.hh, top.toFloat())
        val b = min(glaze.cy + glaze.hh, bottom.toFloat())
        if (r <= l || b <= t) return
        val brush = shader.brush(
            image = image,
            from = from.toFloat(),
            to = to.toFloat(),
            near = near,
            middle = middle,
            far = far,
            wall = glaze.wall,
            centreX = glaze.cx,
            centreY = glaze.cy,
            halfWidth = glaze.hw,
            halfHeight = glaze.hh,
            bevel = glaze.bevel,
            edges = glaze.edges,
            rim = glaze.rim,
        )
        drawRect(brush, topLeft = Offset(l, t), size = Size(r - l, b - t))
        return
    }

    val l = max(glaze.opening, from.toFloat())
    val r = min(glaze.width - glaze.opening, to.toFloat())
    val t = glaze.opening
    val b = glaze.height - glaze.opening
    if (r <= l || b <= t) return
    drawRect(
        Brush.horizontalGradient(
            0f to glaze.wall.copy(alpha = near),
            0.5f to glaze.wall.copy(alpha = middle),
            1f to glaze.wall.copy(alpha = far),
            startX = from.toFloat(),
            endX = to.toFloat(),
        ),
        topLeft = Offset(l, t),
        size = Size(r - l, b - t),
    )

    val cx = glaze.gx
    if (glaze.glow <= 0f || cx + glaze.rx < l || cx - glaze.rx > r) return
    // Round, then stood up tall; the opening goes the other way so it still bounds it.
    val k = glaze.ry / glaze.rx
    withTransform({ translate(cx, glaze.gy); scale(1f, k, Offset.Zero) }) {
        drawRect(
            Brush.radialGradient(
                0f to GLARE.copy(alpha = glaze.glow),
                0.45f to GLARE.copy(alpha = glaze.glow * 0.45f),
                1f to Color.Transparent,
                center = Offset.Zero,
                radius = glaze.rx,
            ),
            topLeft = Offset(l - cx, (t - glaze.gy) / k),
            size = Size(r - l, (b - t) / k),
        )
    }
}

/**
 * A bitmap whose left edge is [left] along the wall, [inset] off it, at
 * [alpha]: cut where the wall bends, each flat stretch through one exact
 * projection and each curved one in chords; [glaze] glasses it.
 */
private fun DrawScope.drawSheet(
    bitmap: ImageBitmap,
    pxPerUnit: Float,
    top: Float,
    left: Float,
    inset: Float,
    room: Room,
    alpha: Float = 1f,
    glaze: Glaze? = null,
) {
    // The glass can be slid off the bitmap, so the sheet runs on under it.
    var x0 = 0
    var x1 = bitmap.width
    var y0 = 0
    var y1 = bitmap.height
    if (glaze != null) {
        x0 = min(x0, floor(glaze.cx - glaze.hw).toInt())
        x1 = max(x1, ceil(glaze.cx + glaze.hw).toInt())
        y0 = min(y0, floor(glaze.cy - glaze.hh).toInt())
        y1 = max(y1, ceil(glaze.cy + glaze.hh).toInt())
    }
    val from = max(left + x0 / pxPerUnit, -room.reach)
    val to = min(left + x1 / pxPerUnit, room.reach)
    if (to <= from) return
    var a = from
    while (a < to) {
        var b = to
        for (k in bends) if (k > a + 1e-5f && k < b) b = k
        val curved = flat < 1f && abs((a + b) / 2f) in FLAT..(FLAT + ARC)
        val pieces = if (curved) max(1, ceil((b - a) / (ARC / ARC_CHORDS)).toInt()) else 1
        for (n in 0 until pieces) {
            drawPiece(
                bitmap, pxPerUnit, top, left, inset, a + (b - a) * n / pieces, a + (b - a) * (n + 1) / pieces,
                room, alpha, glaze, x0, x1, y0, y1,
            )
        }
        a = b
    }
}

/** Columns of overlap between neighbouring pieces, or the seams show. */
private const val OVERLAP = 1

/** The sheet's columns [sx0] to [sx1] and rows [sy0] to [sy1], which run past the bitmap's under slid glass. */
private fun DrawScope.drawPiece(
    bitmap: ImageBitmap,
    pxPerUnit: Float,
    top: Float,
    left: Float,
    inset: Float,
    a: Float,
    b: Float,
    room: Room,
    alpha: Float,
    glaze: Glaze?,
    sx0: Int,
    sx1: Int,
    sy0: Int,
    sy1: Int,
) {
    // On whole columns of the bitmap, so neighbours meet exactly.
    val c0 = ((a - left) * pxPerUnit).roundToInt().coerceIn(sx0, sx1)
    val c1 = ((b - left) * pxPerUnit).roundToInt().coerceIn(sx0, sx1)
    if (c1 <= c0) return
    sheetPoint(left + c0 / pxPerUnit, p0, inset)
    sheetPoint(left + c1 / pxPerUnit, p1, inset)
    // The overlap runs on along the same flat stretch, back towards the last piece.
    val start = max(c0 - OVERLAP, sx0)
    val back = (c0 - start).toFloat() / (c1 - c0)
    // From the eye, wherever the walk has taken it.
    val x0 = p0[0] - (p1[0] - p0[0]) * back - room.eyeX
    val z0 = p0[1] - (p1[1] - p0[1]) * back - room.eyeZ
    val x1 = p1[0] - room.eyeX
    val z1 = p1[1] - room.eyeZ
    if (z0 <= 0.05f || z1 <= 0.05f) return

    val f = room.focal
    val y0 = top + sy0 / pxPerUnit - room.eyeY
    val y1 = top + sy1 / pxPerUnit - room.eyeY
    quad[0] = room.cx + f * x0 / z0; quad[1] = room.cy + f * y0 / z0
    quad[2] = room.cx + f * x1 / z1; quad[3] = room.cy + f * y0 / z1
    quad[4] = room.cx + f * x1 / z1; quad[5] = room.cy + f * y1 / z1
    quad[6] = room.cx + f * x0 / z0; quad[7] = room.cy + f * y1 / z0

    if (!quadMatrix(quad, (c1 - start).toFloat(), (sy1 - sy0).toFloat(), matrix)) return
    withTransform({
        transform(matrix)
        // Into the bitmap's own pixels.
        translate(-start.toFloat(), -sy0.toFloat())
    }) {
        val i0 = max(start, 0)
        val i1 = min(c1, bitmap.width)
        if (i1 > i0) {
            drawImage(
                image = bitmap,
                srcOffset = IntOffset(i0, 0),
                srcSize = IntSize(i1 - i0, bitmap.height),
                dstOffset = IntOffset(i0, 0),
                dstSize = IntSize(i1 - i0, bitmap.height),
                alpha = alpha,
                filterQuality = FilterQuality.Medium,
            )
        }
        if (glaze != null) {
            drawGlass(glaze, start, c1, sy0, sy1, left + start / pxPerUnit, left + c1 / pxPerUnit, x0, z0, x1, z1)
        }
    }
}

/** A step along the wall, to tell which way it runs at an edge. */
private const val PROBE = 1e-3f

/**
 * Whichever of the painting's two side edges faces the eye, as a strip from
 * the wall out to the face. The top and bottom never show: the eye is level
 * with the middles.
 */
private fun DrawScope.drawEdges(card: CardArt, edges: ImageBitmap, left: Float, room: Room, alpha: Float) {
    val d = edges.width / 2
    val h = edges.height
    for (side in 0..1) {
        val s = if (side == 0) left else left + card.face.width / card.pxPerUnit
        if (abs(s) > room.reach) continue
        sheetPoint(s, p0)
        // Outwards from the painting, along the wall.
        sheetPoint(if (side == 0) s - PROBE else s + PROBE, p1)
        val ox = p1[0] - p0[0]
        val oz = p1[1] - p0[1]
        if ((room.eyeX - p0[0]) * ox + (room.eyeZ - p0[1]) * oz <= 0f) continue
        sheetPoint(s, p1, card.depth)
        val xw = p0[0] - room.eyeX
        val zw = p0[1] - room.eyeZ
        val xf = p1[0] - room.eyeX
        val zf = p1[1] - room.eyeZ
        if (zw <= 0.05f || zf <= 0.05f) continue

        val f = room.focal
        val y0 = card.top - room.eyeY
        val y1 = y0 + h / card.pxPerUnit
        // Wall side first, as the strip is drawn.
        quad[0] = room.cx + f * xw / zw; quad[1] = room.cy + f * y0 / zw
        quad[2] = room.cx + f * xf / zf; quad[3] = room.cy + f * y0 / zf
        quad[4] = room.cx + f * xf / zf; quad[5] = room.cy + f * y1 / zf
        quad[6] = room.cx + f * xw / zw; quad[7] = room.cy + f * y1 / zw
        if (!quadMatrix(quad, d.toFloat(), h.toFloat(), matrix)) continue
        withTransform({ transform(matrix) }) {
            drawImage(
                image = edges,
                srcOffset = IntOffset(side * d, 0),
                srcSize = IntSize(d, h),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(d, h),
                alpha = alpha,
                filterQuality = FilterQuality.Medium,
            )
        }
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
    /** The painting and label when it hangs flat; the frame alone, open in the middle, when framed. */
    val face: ImageBitmap,
    /** Bitmap pixels to a card height, both ways. */
    val pxPerUnit: Float,
    /** Bitmap top, above the eye level, in card heights (negative is up); every bitmap here shares it. */
    val top: Float,
    /** What it throws on the wall behind it — shadow, label — when it stands off it. */
    val wall: ImageBitmap? = null,
    /** Its left and right edges side by side, each from the wall out to the face. */
    val edges: ImageBitmap? = null,
    /** How far the face stands off the wall, in card heights. */
    val depth: Float = 0f,
    /** The painting in the frame, the inside of the moulding round it; the face's size. */
    val back: ImageBitmap? = null,
    /** How far [back] sits behind the face, in card heights. */
    val recess: Float = 0f,
    val glass: Boolean = false,
    /** The face's opening, in from each side, in its pixels. */
    val opening: Int = 0,
    unframe: (() -> ImageBitmap)? = null,
) {
    /** How far along the wall it runs, label and shadow included. */
    val wallWidth: Float get() = max(face.width, wall?.width ?: 0) / pxPerUnit

    /**
     * The canvas out of its frame, behind liquid glass, the size of the face;
     * drawn the first time it is asked for.
     */
    val bare: ImageBitmap? by lazy { unframe?.invoke() }
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
        cards = items.mapIndexed { n, item -> build(item, px.toFloat(), n) }
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
        return build(item, px.toFloat(), index).also { closeCard = it }
    }

    /** [seed] picks the painting's wood, the same at every size. */
    private fun build(item: GalleryWallItem, unit: Float, seed: Int): CardArt {
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
        val border = frame?.let { (it.width * unit).roundToInt() } ?: 0
        val shadow = frame?.let { (it.shadow * unit).roundToInt() } ?: 0
        val depth = frame?.let { (it.depth * unit).roundToInt() } ?: 0
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
        // Painting widths along the wall come from its own pixels, so the
        // hang and the bitmap agree to the column.
        val pxPerUnit = paintH / item.scale
        val top = -outerH / 2f / pxPerUnit

        fun paint(w: Int, h: Int, block: DrawScope.() -> Unit): ImageBitmap {
            val bitmap = ImageBitmap(w, h)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, BitmapCanvas(bitmap), Size(w.toFloat(), h.toFloat()), block)
            return bitmap
        }
        fun DrawScope.canvas() = drawImage(
            image = item.image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(item.image.width, item.image.height),
            dstOffset = IntOffset(border, border),
            dstSize = IntSize(paintW, paintH),
            filterQuality = FilterQuality.High,
        )
        fun DrawScope.label() = lines.forEachIndexed { n, layout ->
            drawText(layout, topLeft = Offset(0f, labelTop + n * LABEL_LINE * unit))
        }

        if (frame == null) return CardArt(paint(bw, bh) { canvas(); label() }, pxPerUnit, top)
        val recess = (frame.recess.coerceIn(0f, frame.depth) * unit).roundToInt().coerceAtMost(depth)
        val face = paint(outerW, outerH) {
            drawFrame(frame, outerW.toFloat(), outerH.toFloat(), border.toFloat(), unit, seed)
        }
        val back = paint(outerW, outerH) {
            drawReturns(frame, outerW.toFloat(), outerH.toFloat(), border.toFloat())
            canvas()
            drawSightShadow(outerW.toFloat(), outerH.toFloat(), border.toFloat(), max(0.012f * unit, 1.2f * recess))
        }
        val wall = paint(bw, bh) {
            drawShadow(outerW.toFloat(), outerH.toFloat(), shadow.toFloat())
            label()
        }
        val edges = if (depth > 0) paint(2 * depth, outerH) { drawFrameSides(frame, depth.toFloat(), outerH.toFloat(), unit, seed) } else null
        return CardArt(
            face = face,
            pxPerUnit = pxPerUnit,
            top = top,
            wall = wall,
            edges = edges,
            depth = depth / pxPerUnit,
            back = back,
            recess = recess / pxPerUnit,
            glass = frame.glass,
            opening = border,
            unframe = if (frame.glass && shadedGlass != null) {
                { paint(outerW, outerH) { canvas() } }
            } else {
                null
            },
        )
    }
}

// --- the frame ---------------------------------------------------------------

/*
 * The moulding's profile, outer edge to inner, as light over the wood: a
 * rounded outer edge falling away, a satin crown catching the light, a flat
 * face, a stepped groove, and a small bevel down to the canvas.
 */
private val PROFILE = listOf(
    0.00f to -0.50f,
    0.05f to -0.20f,
    0.14f to 0.22f,
    0.22f to 0.12f,
    0.45f to 0.00f,
    0.68f to -0.06f,
    0.72f to -0.42f,
    0.75f to -0.10f,
    0.79f to 0.16f,
    0.94f to -0.08f,
    1.00f to -0.45f,
)

/** The moulding's outside, from the wall out to the face: dark where it meets the wall, a lit arris at the front. */
private val SIDE_PROFILE = listOf(
    0.00f to -0.55f,
    0.65f to -0.15f,
    0.88f to 0.25f,
    1.00f to -0.30f,
)

/** Black over each side of the moulding — top, right, bottom, left — for a light from the upper left. */
private val SIDE_SHADE = floatArrayOf(0f, 0.22f, 0.32f, 0.08f)

/** The warm light a satin finish throws back, and the two colours of the grain. */
private val SHEEN = Color(0xFFFFE6C4)
private val LATEWOOD = Color(0xFF241207)
private val EARLYWOOD = Color(0xFFF0CFA0)

/** Grain lines across a stick's face, and across its narrow outside. */
private const val FACE_GRAIN = 30
private const val SIDE_GRAIN = 12
private const val FIGURE_BANDS = 5
private const val PORES = 160

private const val TAU = 2f * PI.toFloat()

private fun shade(color: Color, by: Float): Color =
    if (by >= 0f) lerp(color, Color.White, by) else lerp(color, Color.Black, -by)

private fun light(by: Float): Color =
    if (by >= 0f) SHEEN.copy(alpha = by) else Color.Black.copy(alpha = -by)

private fun Random.between(from: Float, to: Float): Float = from + nextFloat() * (to - from)

/**
 * One stick of wood, [length] along by [across] wide, with x running along it
 * and y across. Every size is a share of [across] or a multiple of [unit], and
 * [random] is drawn on the same number of times at any size, so the painting
 * walked up to wears the same piece of wood it had from across the room.
 */
private fun DrawScope.drawWood(base: Color, grain: Float, length: Float, across: Float, unit: Float, lines: Int, random: Random) {
    drawRect(shade(base, random.between(-0.06f, 0.06f)), size = Size(length, across))
    if (grain <= 0f) return
    val step = max(2f, 0.01f * unit)
    val points = ceil(length / step).toInt() + 1
    val hairline = max(0.5f, 0.0012f * unit)
    // One flow the whole stick follows, each line wandering a little off it.
    val flowAmp = across * random.between(0.05f, 0.18f)
    val flowWave = unit * random.between(0.5f, 1.4f)
    val flowPhase = random.nextFloat() * TAU
    fun flow(x: Float) = flowAmp * sin(x / flowWave * TAU + flowPhase)
    fun tint(color: Color, alpha: Float) = color.copy(alpha = (alpha * grain).coerceIn(0f, 1f))
    fun line(color: Color, width: Float, y0: Float, amp: Float) {
        val wave = unit * random.between(0.15f, 0.8f)
        val phase = random.nextFloat() * TAU
        val path = Path()
        for (i in 0 until points) {
            val x = min(i * step, length)
            val y = y0 + flow(x) + amp * sin(x / wave * TAU + phase)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width))
    }

    // The figure: broad soft bands of darker and paler wood.
    repeat(FIGURE_BANDS) {
        val dark = random.nextBoolean()
        val color = if (dark) tint(LATEWOOD, random.between(0.06f, 0.14f)) else tint(EARLYWOOD, random.between(0.05f, 0.10f))
        line(color, across * random.between(0.12f, 0.35f), random.nextFloat() * across, across * random.between(0.05f, 0.2f))
    }
    // The grain itself: fine lines, mostly dark latewood, a few pale.
    repeat(lines) {
        val dark = random.nextFloat() < 0.8f
        val color = if (dark) tint(LATEWOOD, random.between(0.12f, 0.40f)) else tint(EARLYWOOD, random.between(0.08f, 0.20f))
        val width = max(0.5f, unit * random.between(0.0008f, 0.0024f))
        line(color, width, across * random.between(-0.1f, 1.1f), across * random.between(0.01f, 0.06f))
    }
    // Open pores, short dashes along the grain.
    repeat(PORES) {
        val x = random.nextFloat() * length
        val y = random.nextFloat() * across + flow(x)
        val dash = unit * random.between(0.004f, 0.015f)
        drawLine(tint(LATEWOOD, random.between(0.15f, 0.35f)), Offset(x, y), Offset(x + dash, y), hairline)
    }
}

/** The frame's left and right outsides, side by side, each [d] across and [h] tall. */
private fun DrawScope.drawFrameSides(frame: GalleryWallFrame, d: Float, h: Float, unit: Float, seed: Int) {
    val stops = SIDE_PROFILE.map { (at, by) -> at to light(by) }.toTypedArray()
    for (side in 0..1) {
        val x = side * d
        // The grain runs up the stick, so draw it lying down and stand it up.
        clipRect(x, 0f, x + d, h) {
            withTransform({ translate(x + d, 0f); rotate(90f, Offset.Zero) }) {
                drawWood(frame.color, frame.grain, h, d, unit, SIDE_GRAIN, Random(seed * 8 + 4 + side))
            }
        }
        drawRect(Brush.horizontalGradient(*stops, startX = x, endX = x + d), topLeft = Offset(x, 0f), size = Size(d, h))
    }
    // The left one turns towards the light, the right one away; both darken downwards.
    drawRect(Color.Black.copy(alpha = 0.3f), topLeft = Offset(d, 0f), size = Size(d, h))
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.2f))), size = Size(2f * d, h))
}

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

/**
 * The frame on a [w] by [h] bitmap, [border] deep all round, at [unit] pixels
 * to a card height: four sticks mitred at the corners, each its own cut of
 * wood with the grain running along it, so the grain turns at every joint.
 */
private fun DrawScope.drawFrame(frame: GalleryWallFrame, w: Float, h: Float, border: Float, unit: Float, seed: Int) {
    val mould = min(frame.width * unit, border)
    val stops = PROFILE.map { (at, by) -> at to light(by) }.toTypedArray()
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
        val length = if (side % 2 == 0) w else h
        clipPath(path) {
            // Into the stick's own frame: x along it clockwise, y in from the outer edge.
            withTransform({
                when (side) {
                    1 -> { translate(w, 0f); rotate(90f, Offset.Zero) }
                    2 -> { translate(w, h); rotate(180f, Offset.Zero) }
                    3 -> { translate(0f, h); rotate(270f, Offset.Zero) }
                }
            }) {
                drawWood(frame.color, frame.grain, length, mould, unit, FACE_GRAIN, Random(seed * 8 + side))
                drawRect(Brush.verticalGradient(*stops, startY = 0f, endY = mould), size = Size(length, mould))
            }
        }
        if (SIDE_SHADE[side] > 0f) drawPath(path, Color.Black.copy(alpha = SIDE_SHADE[side]))
    }

    // The mitre joints, and the sight edge where the wood meets the canvas.
    val seam = max(0.6f, 0.0016f * unit)
    val joint = Color.Black.copy(alpha = 0.45f)
    drawLine(joint, Offset(0f, 0f), Offset(mould, mould), seam)
    drawLine(joint, Offset(w, 0f), Offset(w - mould, mould), seam)
    drawLine(joint, Offset(w, h), Offset(w - mould, h - mould), seam)
    drawLine(joint, Offset(0f, h), Offset(mould, h - mould), seam)
    drawRect(joint, topLeft = Offset(mould, mould), size = Size(w - 2f * mould, h - 2f * mould), style = Stroke(seam))
}

/**
 * Black over each inside face of the moulding — top, right, bottom, left — for
 * a light from the upper left: the top and left face away from it, the bottom
 * and right towards it, all down a well.
 */
private val RETURN_SHADE = floatArrayOf(0.62f, 0.2f, 0.3f, 0.5f)

/**
 * The inside of the frame round the opening, from the canvas out, on a [w] by
 * [h] bitmap with the canvas [border] in. Drawn flat behind the face, it fills
 * exactly the band a return sloping from the face down to the canvas would
 * show, which is all of it that is ever seen.
 */
private fun DrawScope.drawReturns(frame: GalleryWallFrame, w: Float, h: Float, border: Float) {
    for (side in 0..3) {
        val path = Path()
        val (outerA, outerB, innerB, innerA) = when (side) {
            0 -> listOf(Offset(0f, 0f), Offset(w, 0f), Offset(w - border, border), Offset(border, border))
            1 -> listOf(Offset(w, 0f), Offset(w, h), Offset(w - border, h - border), Offset(w - border, border))
            2 -> listOf(Offset(w, h), Offset(0f, h), Offset(border, h - border), Offset(w - border, h - border))
            else -> listOf(Offset(0f, h), Offset(0f, 0f), Offset(border, border), Offset(border, h - border))
        }
        path.moveTo(outerA.x, outerA.y)
        path.lineTo(outerB.x, outerB.y)
        path.lineTo(innerB.x, innerB.y)
        path.lineTo(innerA.x, innerA.y)
        path.close()
        drawPath(path, lerp(frame.color, Color.Black, RETURN_SHADE[side]))
    }
}

/**
 * The shadow the frame throws onto a canvas sunk in it, [fall] onto it from
 * the top and left, and a little dark in the corners of the well on the others.
 */
private fun DrawScope.drawSightShadow(w: Float, h: Float, border: Float, fall: Float) {
    val iw = w - 2f * border
    val ih = h - 2f * border
    drawRect(
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent), border, border + fall),
        topLeft = Offset(border, border),
        size = Size(iw, fall),
    )
    drawRect(
        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent), border, border + fall),
        topLeft = Offset(border, border),
        size = Size(fall, ih),
    )
    val rim = fall * 0.5f
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f)), h - border - rim, h - border),
        topLeft = Offset(border, h - border - rim),
        size = Size(iw, rim),
    )
    drawRect(
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f)), w - border - rim, w - border),
        topLeft = Offset(w - border - rim, border),
        size = Size(rim, ih),
    )
}
