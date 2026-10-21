package io.github.mejdi14.oddscroll.liftstack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as BitmapCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mejdi14.oddscroll.internal.PI_F
import io.github.mejdi14.oddscroll.internal.SplitBy
import io.github.mejdi14.oddscroll.internal.SplitText
import io.github.mejdi14.oddscroll.internal.cardBitmap
import io.github.mejdi14.oddscroll.internal.clamped
import io.github.mejdi14.oddscroll.internal.smoothstep
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** One card of the deck. */
data class LiftStackItem(
    val image: ImageBitmap,
    val title: String = "",
    /** Second line of the caption, under the title. */
    val credit: String = "",
)

/*
 * The camera and the deck, in card sides, as measured off the reference. A
 * pinhole fitted to the resting stack places it within a pixel: a quarter turn
 * off square, looking down at 27°, from fifteen card sides away — far enough
 * that the stack is nearly isometric, near enough that the lower cards draw
 * two percent smaller a layer and the standing card's top edge is longer than
 * its foot.
 */
private const val YAW = 1.25f * PI_F
private const val ELEVATION = 27.28f * PI_F / 180f
private const val DISTANCE = 15.03f
private const val LOOK_AT_Y = 0.317f

/** Height of one layer of the deck. */
private const val LAYER = 0.2744f

/** How far the standing card rises off the top slot, and which way. */
private const val LIFT_HEIGHT = 0.734f
private const val LIFT_X = -0.150f
private const val LIFT_Y = 0.975f
private const val LIFT_Z = -0.164f

/** How far the card turns: flat to upright, about the line through its middle. */
private const val STAND_ANGLE = PI_F / 2f

/*
 * A card fades out over the first quarter of a slot above the top, and in over
 * the last two thirds of one below the fourth. Both are measured, both are
 * smoothsteps, and nothing else about a card changes on the way through.
 */
private const val OUT_FROM = 0.016f
private const val OUT_TO = -0.23f
private const val IN_FROM = 2.976f
private const val IN_TO = 3.629f

/** White mat round the picture, as a share of the card side. */
private const val MAT = 0.012f

private const val TAP_SLOP = 8f

@Composable
fun rememberLiftStackScrollState(intro: Int = 7): LiftStackScrollState =
    remember { LiftStackScrollState(intro) }

/**
 * A deck of pictures laid flat in a stack and seen from above at a quarter
 * turn, which runs up through itself one card at a time — and whose top card,
 * tapped, gets up off the deck and stands.
 *
 * The whole deck is one number. Every card is a fixed layer below the one
 * before, so where the deck has run to says where each of them is; a card
 * comes in at the bottom, rides up four layers and fades away above the top.
 * Tapping the card in the top slot selects it: it lifts off the slot and turns
 * upright about the line through its own middle until it faces you, on clocks
 * of its own, and says what it is underneath. Tapping again plays the same
 * animation backwards and lays it back in its slot. Nothing grows, shrinks or
 * rounds off at any point; the change in size between layers is only the
 * camera's perspective, and each card is drawn through its own exact
 * projection rather than an approximation of one.
 *
 * Drag up and down to run the deck by hand, flick it to throw it, or scroll to
 * step it on a card. None of that moves it while a card is selected, or on its
 * way back down.
 *
 * @param items cards, in order; the deck repeats, so it has no end.
 * @param background page colour.
 * @param contentColor caption text.
 * @param markerColor the small square that leads the caption.
 * @param captions say what the standing card is.
 * @param state also sets how far the deck runs when it first appears.
 */
@Composable
fun LiftStackScroll(
    items: List<LiftStackItem>,
    modifier: Modifier = Modifier,
    background: Color = Color(0xFFF6F6F1),
    contentColor: Color = Color(0xFF141414),
    markerColor: Color = Color(0xFFD9443F),
    captions: Boolean = true,
    state: LiftStackScrollState = rememberLiftStackScrollState(),
) {
    if (items.isEmpty()) {
        Box(modifier)
        return
    }

    val cards = remember(items) { items.map { matted(cardBitmap(it.image, 1f, 0f)) } }
    val frame = remember { StackFrame() }

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

    Box(modifier.background(background)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var last = down.position.y
                        var travel = 0f
                        var started = false
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dy = change.position.y - last
                            last = change.position.y
                            travel += abs(dy)
                            if (travel > TAP_SLOP) {
                                if (!started) {
                                    state.onDragStart(change.uptimeMillis / 1000f)
                                    started = true
                                }
                                // Up the screen is on through the deck: the
                                // cards follow the finger.
                                state.onDrag(-dy / max(frame.layerPx, 1f), change.uptimeMillis / 1000f)
                                change.consume()
                            }
                        }
                        if (started) {
                            state.onDragEnd()
                        } else {
                            state.onTap(frame.hitsTop(down.position.x, down.position.y, state))
                        }
                    }
                }
                .pointerInput(state) {
                    // A notch is a card. A trackpad sends fractions of one, so
                    // they are added up until they come to a whole card.
                    var notches = 0f
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val scroll = event.changes.firstOrNull()?.scrollDelta?.y ?: continue
                            if (scroll == 0f) continue
                            notches += scroll
                            while (abs(notches) >= 1f) {
                                val by = if (notches > 0f) 1 else -1
                                state.step(by)
                                notches -= by
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            state.tick // read, so the stack repaints every frame
            frame.fit(size.width, size.height, density, captions)
            drawStack(cards, state, frame)
        }

        if (captions) Caption(items, state, contentColor, markerColor)
    }
}

// --- camera ------------------------------------------------------------------

/** The fitted camera, as a basis to take dot products against. */
private object Camera {
    val cx: Float
    val cy: Float
    val cz: Float
    val rx: Float
    val ry: Float
    val rz: Float
    val ux: Float
    val uy: Float
    val uz: Float
    val fx: Float
    val fy: Float
    val fz: Float

    init {
        cx = DISTANCE * cos(ELEVATION) * sin(YAW)
        cy = LOOK_AT_Y + DISTANCE * sin(ELEVATION)
        cz = DISTANCE * cos(ELEVATION) * cos(YAW)
        fx = -cx / DISTANCE
        fy = (LOOK_AT_Y - cy) / DISTANCE
        fz = -cz / DISTANCE
        // Right is forward crossed with world up; up is right crossed with forward.
        val r0 = -fz
        val r2 = fx
        val rl = sqrt(r0 * r0 + r2 * r2)
        rx = r0 / rl
        ry = 0f
        rz = r2 / rl
        ux = ry * fz - rz * fy
        uy = rz * fx - rx * fz
        uz = rx * fy - ry * fx
    }

    /** Screen offset of a world point, in card sides at the look-at distance. */
    fun project(x: Float, y: Float, z: Float, out: FloatArray, at: Int) {
        val vx = x - cx
        val vy = y - cy
        val vz = z - cz
        val depth = max(vx * fx + vy * fy + vz * fz, 0.01f)
        out[at] = DISTANCE * (vx * rx + vy * ry + vz * rz) / depth
        out[at + 1] = -DISTANCE * (vx * ux + vy * uy + vz * uz) / depth
    }
}

/**
 * Where a card's four corners land, picture top-left first and on round.
 *
 * The card is a unit square in the floor plane, turned [angle] about the line
 * through its middle that runs along one pair of its edges, then set [p]
 * layers down the deck and [lift] of the way up off it.
 */
private fun cardCorners(p: Float, lift: Float, angle: Float, out: FloatArray) {
    val c = cos(angle)
    val s = sin(angle)
    val ox = lift * LIFT_HEIGHT * LIFT_X
    val oy = -p * LAYER + lift * LIFT_HEIGHT * LIFT_Y
    val oz = lift * LIFT_HEIGHT * LIFT_Z
    for (k in 0 until 4) {
        val lx = if (k < 2) 0.5f else -0.5f
        val lz = if (k == 0 || k == 3) -0.5f else 0.5f
        Camera.project(lx * c + ox, lx * s + oy, lz + oz, out, k * 2)
    }
}

/** Where the scene sits in the frame, worked out once per size. */
private class StackFrame {
    var width = -1f
    var height = -1f
    var side = 1f
    var originX = 0f
    var originY = 0f
    var layerPx = 1f

    fun fit(w: Float, h: Float, density: Float, captions: Boolean) {
        if (w == width && h == height) return
        width = w
        height = h
        // Bound the resting scene: the four flat layers and the card standing
        // above them.
        val pts = FloatArray(8)
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        fun take() {
            for (k in 0 until 4) {
                minX = min(minX, pts[k * 2]); maxX = max(maxX, pts[k * 2])
                minY = min(minY, pts[k * 2 + 1]); maxY = max(maxY, pts[k * 2 + 1])
            }
        }
        for (layer in 0..3) {
            cardCorners(layer.toFloat(), 0f, 0f, pts)
            take()
        }
        cardCorners(0f, 1f, STAND_ANGLE, pts)
        take()

        // The caption sits under the scene, so the scene is centred in what
        // is left above it.
        val reserve = if (captions) 96f * density else 0f
        val room = (h - reserve).coerceAtLeast(h * 0.5f)
        side = min(w * 0.82f / (maxX - minX), room * 0.84f / (maxY - minY))
        originX = w / 2f - side * (minX + maxX) / 2f
        originY = room / 2f - side * (minY + maxY) / 2f

        cardCorners(0f, 0f, 0f, pts)
        val top = pts[1]
        cardCorners(1f, 0f, 0f, pts)
        layerPx = abs(pts[1] - top) * side
    }

    /** Whether a tap at [x], [y] lands on the card nearest the top slot. */
    fun hitsTop(x: Float, y: Float, state: LiftStackScrollState): Boolean {
        val pts = FloatArray(8)
        cardCorners(state.top - state.phase, 0f, 0f, pts)
        // Inside a convex quad is on the same side of all four edges.
        var sign = 0f
        for (k in 0 until 4) {
            val ax = originX + pts[k * 2] * side
            val ay = originY + pts[k * 2 + 1] * side
            val bx = originX + pts[(k + 1) % 4 * 2] * side
            val by = originY + pts[(k + 1) % 4 * 2 + 1] * side
            val cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
            if (cross == 0f) continue
            if (sign == 0f) sign = cross else if (sign * cross < 0f) return false
        }
        return true
    }
}

// --- the stack ---------------------------------------------------------------

private fun deckAlpha(p: Float): Float =
    smoothstep(OUT_TO, OUT_FROM, p) * (1f - smoothstep(IN_FROM, IN_TO, p))

private fun DrawScope.drawStack(
    cards: List<ImageBitmap>,
    state: LiftStackScrollState,
    frame: StackFrame,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val phase = state.phase
    val count = cards.size
    val selected = state.selected
    val corners = FloatArray(8)
    val matrix = Matrix()

    fun card(index: Int, p: Float, lift: Float, angle: Float, alpha: Float) {
        if (alpha <= 0.002f) return
        cardCorners(p, lift, angle, corners)
        for (k in 0 until 4) {
            corners[k * 2] = frame.originX + corners[k * 2] * frame.side
            corners[k * 2 + 1] = frame.originY + corners[k * 2 + 1] * frame.side
        }
        val image = cards[wrapItem(index, count)]
        if (!quadMatrix(corners, image.width.toFloat(), image.height.toFloat(), matrix)) return
        withTransform({ transform(matrix) }) {
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(image.width, image.height),
                alpha = alpha.coerceAtMost(1f),
                filterQuality = FilterQuality.Medium,
            )
        }
    }

    // Bottom of the deck first: the camera is above it, so each layer covers
    // the one below.
    val first = ceil(phase + OUT_TO).toInt()
    val last = floor(phase + IN_TO).toInt()

    for (index in last downTo first) {
        if (index == selected.index) continue
        card(index, index - phase, 0f, 0f, deckAlpha(index - phase))
    }

    // Off the deck it goes over everything. It rises from wherever its slot
    // was and is held at the top one while it is up, so a deck still easing
    // the last fraction of a card in does not carry it along.
    if (selected.index != NONE) {
        val p = (selected.index - phase) * (1f - selected.up)
        card(selected.index, p, selected.lift, selected.stand * STAND_ANGLE, max(deckAlpha(p), selected.up))
    }
}

/**
 * The projective map taking a [w] by [h] picture onto four screen corners,
 * written into [out].
 *
 * Compose's `Matrix` carries the perspective terms in its fourth column and
 * every target honours them, so a card is one `drawImage` through its own
 * exact projection — no strips. Rows are the input axes, as everywhere else.
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

/** The picture set in a thin white mat, the way the reference's cards are. */
private fun matted(source: ImageBitmap): ImageBitmap {
    val side = source.width
    val bitmap = ImageBitmap(side, side)
    val inset = (side * MAT).roundToInt().coerceAtLeast(1)
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        BitmapCanvas(bitmap),
        Size(side.toFloat(), side.toFloat()),
    ) {
        drawRect(Color.White)
        drawImage(
            image = source,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(source.width, source.height),
            dstOffset = IntOffset(inset, inset),
            dstSize = IntSize(side - inset * 2, side - inset * 2),
            filterQuality = FilterQuality.Medium,
        )
    }
    return bitmap
}

// --- caption -----------------------------------------------------------------

@Composable
private fun BoxScope.Caption(
    items: List<LiftStackItem>,
    state: LiftStackScrollState,
    contentColor: Color,
    markerColor: Color,
) {
    val standing = state.standing
    if (standing == NONE) return
    val item = items[wrapItem(standing, items.size)]
    if (item.title.isEmpty() && item.credit.isEmpty()) return

    key(state.stood) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 34.dp)
                // Goes as the card goes back down, read on the layer so the
                // caption is not recomposed every frame of it.
                .graphicsLayer {
                    state.tick
                    alpha = smoothstep(0.35f, 0.75f, state.selected.stand)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).background(markerColor))
            Box(Modifier.size(34.dp, 1.dp).background(contentColor.copy(alpha = 0.22f)))
            Spacer(Modifier.width(12.dp))
            Column {
                SplitText(
                    text = item.title,
                    style = TextStyle(color = contentColor, fontSize = 20.sp, fontFamily = FontFamily.Serif),
                    splitBy = SplitBy.Chars,
                    rise = 10.dp,
                )
                SplitText(
                    text = item.credit,
                    style = TextStyle(
                        color = contentColor.copy(alpha = 0.5f),
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Serif,
                    ),
                    splitBy = SplitBy.Chars,
                    rise = 10.dp,
                    startDelay = 180,
                )
            }
        }
    }
}
