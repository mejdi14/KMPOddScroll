package io.github.mejdi14.oddscroll.taperack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as BitmapCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mejdi14.oddscroll.internal.SplitBy
import io.github.mejdi14.oddscroll.internal.SplitText
import io.github.mejdi14.oddscroll.internal.clamped
import io.github.mejdi14.oddscroll.internal.smoothstep
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One tape in the rack. */
data class TapeRackItem(
    /** Printed on the tape's label, cropped to fit it. */
    val image: ImageBitmap,
    val title: String = "",
    /** Second line of the caption, under the title. */
    val artist: String = "",
)

/*
 * The rack, in tape widths, as measured off the reference. The camera is far
 * enough away to be orthographic — a projective fit along the row comes back
 * with a perspective term of 0.0005 a tape, nothing over the dozen in view —
 * so every tape is the same shape on screen and only where it is changes.
 *
 * The row runs up and to the right at 27.29°, a tape every 0.322 widths;
 * pooled over 1,147 corners in 270 frames, that line and that pitch place every
 * tape to 1.1 px.
 */
private const val ROW_X = 0.88869f
private const val ROW_Y = -0.45849f
private const val PITCH = 0.322f

/** The face: its top edge falls 0.18 to the right, its sides are upright. */
private const val EDGE_X = 0.98419f
private const val EDGE_Y = 0.17715f
private const val FACE_HEIGHT = 0.719f

/** How deep a tape is, back along the row. Read off the top band; the loosest number here. */
private const val THICKNESS = 0.066f

/*
 * The tape in the slot stands 0.342 widths proud of the rest, straight up.
 * The rise is a fixed function of how far a tape is from the slot: flat over
 * the middle 0.077 tapes either side and gone by 1.002, a smoothstep between.
 * That fits 1.9 px across 50 px of lift; a per-tape timed lift, the other way
 * to build it, does not get under 7.
 */
private const val LIFT = 0.342f
private const val LIFT_FLAT = 0.077f
private const val LIFT_GONE = 1.002f

/*
 * The tapes are not quite in line: each sits a fixed small way off it, the
 * same every time it comes round. Measured spread, as standard deviations.
 */
private const val JITTER_ALONG = 0.009f
private const val JITTER_ACROSS = 0.018f

/*
 * A light sits over the slot: a tape's shell reads 18% brighter there than
 * two tapes off it, whether or not it is lifted. One gradient over the lot.
 */
private const val SHADE = 0.15f

/** Corner rounding of the shell, as a share of the tape's width. */
private const val SHELL_CORNER = 0.045f

private const val TAP_SLOP = 8f

@Composable
fun rememberTapeRackScrollState(intro: Int = 4): TapeRackScrollState =
    remember { TapeRackScrollState(intro) }

/**
 * A row of cassettes standing on edge, seen from above and to the left, that
 * slides along its own length — and the tape that comes to the slot in the
 * middle of the frame rises out of the row.
 *
 * The whole rack is one number. Each tape stands a fixed pitch up the row from
 * the one before, so where the rack has slid to says where every tape is, and
 * how high a tape stands is a fixed function of how far it is from the slot:
 * tapes rise as they come into it and settle back as they leave, with nothing
 * timed per tape. Nothing turns, grows, fades or rounds off; the camera is far
 * enough away that every tape is the same shape on screen, so each is drawn
 * once into a bitmap and then only moved — one `drawImage` a tape a frame.
 *
 * Drag along the row, or across it, and the tapes follow the finger; flick to
 * throw it, scroll to step it a tape, or tap a tape to bring it to the slot.
 *
 * @param items tapes, in order; the rack repeats, so it has no end.
 * @param background page colour.
 * @param shellColor the tapes' plastic.
 * @param contentColor caption text.
 * @param captions say what the tape in the slot is, top left.
 * @param state also sets how far the rack slides when it first appears.
 */
@Composable
fun TapeRackScroll(
    items: List<TapeRackItem>,
    modifier: Modifier = Modifier,
    background: Color = Color(0xFF050506),
    shellColor: Color = Color(0xFF1B1B1E),
    contentColor: Color = Color(0xFFEDEDE8),
    captions: Boolean = true,
    state: TapeRackScrollState = rememberTapeRackScrollState(),
) {
    if (items.isEmpty()) {
        Box(modifier)
        return
    }

    val art = remember(items, shellColor) { TapeArt(items, shellColor) }
    val frame = remember { RackFrame() }

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
                // The light goes over the finished rack, onto the tapes only.
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .pointerInput(state) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var last = down.position
                        var travel = 0f
                        var started = false
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dx = change.position.x - last.x
                            val dy = change.position.y - last.y
                            last = change.position
                            travel += abs(dx) + abs(dy)
                            if (travel > TAP_SLOP) {
                                if (!started) {
                                    state.onDragStart(change.uptimeMillis / 1000f)
                                    started = true
                                }
                                // Only the part of the drag that runs along
                                // the row counts, and up the row is back
                                // through the rack: the tapes follow the finger.
                                val along = dx * ROW_X + dy * ROW_Y
                                state.onDrag(-along / max(frame.pitchPx, 1f), change.uptimeMillis / 1000f)
                                change.consume()
                            }
                        }
                        if (started) {
                            state.onDragEnd()
                        } else {
                            val hit = frame.hit(down.position.x, down.position.y, state.position, items.size)
                            if (hit != null) state.scrollTo(hit)
                        }
                    }
                }
                .pointerInput(state) {
                    // A notch is a tape. A trackpad sends fractions of one, so
                    // they are added up until they come to a whole tape.
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
            state.tick // read, so the rack repaints every frame
            frame.fit(size.width, size.height)
            art.ensure(frame.tapePx)
            drawRack(art, state.position, frame)
        }

        if (captions) Caption(items, state, contentColor)
    }
}

// --- layout ------------------------------------------------------------------

/** Where the rack sits in the frame, worked out once per size. */
private class RackFrame {
    var width = -1f
    var height = -1f

    /** A tape's width on screen. */
    var tapePx = 1f
    var pitchPx = 1f

    /** Top-left corner of the face of an unlifted tape at the slot. */
    var originX = 0f
    var originY = 0f

    /** Distances from the slot, in tapes, between which a tape can be seen. */
    var nearest = 0f
    var farthest = 0f

    private val hex = FloatArray(12)

    fun fit(w: Float, h: Float) {
        if (w == width && h == height) return
        width = w
        height = h
        tapePx = min(w * 0.4f, h * 0.42f)
        pitchPx = PITCH * tapePx
        // The lifted tape's face is centred in the frame.
        originX = w / 2f - (EDGE_X * tapePx) / 2f
        originY = h / 2f - (EDGE_Y + FACE_HEIGHT) * tapePx / 2f + LIFT * tapePx

        // Scan out along the row for the first and last tape that lands in the
        // frame at all, lifted or not.
        nearest = 0f
        farthest = 0f
        var w0 = 0f
        while (w0 > -64f && overlaps(w0)) w0 -= 0.25f
        nearest = w0 - 1f
        w0 = 0f
        while (w0 < 64f && overlaps(w0)) w0 += 0.25f
        farthest = w0 + 1f
    }

    private fun overlaps(w: Float): Boolean {
        val x = originX + w * pitchPx * ROW_X
        val y = originY + w * pitchPx * ROW_Y
        val right = x + (EDGE_X + THICKNESS * ROW_X) * tapePx
        val top = y + (THICKNESS * ROW_Y - LIFT) * tapePx
        val bottom = y + (EDGE_Y + FACE_HEIGHT) * tapePx
        return right > 0f && x < width && bottom > 0f && top < height
    }

    /** Top-left corner of tape [index]'s face, [w] tapes from the slot, into [out]. */
    fun corner(w: Float, index: Int, out: FloatArray) {
        val along = w * PITCH + jitter(index, 0) * JITTER_ALONG
        val across = jitter(index, 1) * JITTER_ACROSS
        out[0] = originX + (along * ROW_X - across * ROW_Y) * tapePx
        out[1] = originY + (along * ROW_Y + across * ROW_X - lift(w) * LIFT) * tapePx
    }

    /**
     * The tape under [x], [y], unwrapped, or null. The front of the row covers
     * the back, so tapes are tried front first.
     */
    fun hit(x: Float, y: Float, position: Float, count: Int): Int? {
        val corner = FloatArray(2)
        val first = ceil(position + nearest).toInt()
        val last = floor(position + farthest).toInt()
        for (i in first..last) {
            corner(i - position, wrapTape(i, count), corner)
            outline(corner[0], corner[1])
            if (inside(x, y)) return i
        }
        return null
    }

    /** The six corners of a tape's silhouette: face, top and right side. */
    private fun outline(x: Float, y: Float) {
        val ex = EDGE_X * tapePx
        val ey = EDGE_Y * tapePx
        val tx = THICKNESS * ROW_X * tapePx
        val ty = THICKNESS * ROW_Y * tapePx
        val vy = FACE_HEIGHT * tapePx
        hex[0] = x; hex[1] = y
        hex[2] = x + tx; hex[3] = y + ty
        hex[4] = x + ex + tx; hex[5] = y + ey + ty
        hex[6] = x + ex + tx; hex[7] = y + ey + ty + vy
        hex[8] = x + ex; hex[9] = y + ey + vy
        hex[10] = x; hex[11] = y + vy
    }

    // Inside a convex outline is on the same side of every edge.
    private fun inside(x: Float, y: Float): Boolean {
        var sign = 0f
        for (k in 0 until 6) {
            val ax = hex[k * 2]
            val ay = hex[k * 2 + 1]
            val bx = hex[(k + 1) % 6 * 2]
            val by = hex[(k + 1) % 6 * 2 + 1]
            val cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
            if (cross == 0f) continue
            if (sign == 0f) sign = cross else if (sign * cross < 0f) return false
        }
        return true
    }
}

/** How far up a tape [w] tapes from the slot stands, from 0 to 1. */
private fun lift(w: Float): Float = 1f - smoothstep(LIFT_FLAT, LIFT_GONE, abs(w))

/**
 * A tape's fixed offset off the line, from -√3 to √3 so its spread over the
 * rack is one standard deviation. Hashed from the tape, so it is the same each
 * time the tape comes round.
 */
private fun jitter(index: Int, axis: Int): Float {
    var h = index * 374761393 + axis * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return ((h and 0xFFFF) / 65535f * 2f - 1f) * SQRT_3
}

private const val SQRT_3 = 1.7320508f

// --- drawing -----------------------------------------------------------------

private fun DrawScope.drawRack(art: TapeArt, position: Float, frame: RackFrame) {
    if (size.width <= 0f || size.height <= 0f) return
    val count = art.tapes.size
    val corner = FloatArray(2)

    // Back of the row first: each tape covers the one behind it.
    val first = ceil(position + frame.nearest).toInt()
    val last = floor(position + frame.farthest).toInt()
    for (i in last downTo first) {
        val index = wrapTape(i, count)
        frame.corner(i - position, index, corner)
        val image = art.tapes[index]
        // Translated rather than offset by whole pixels, so a slow slide does
        // not step.
        withTransform({ translate(corner[0] + art.offsetX, corner[1] + art.offsetY) }) {
            drawImage(image)
        }
    }

    // The light over the slot, centred between where the slotted tape stands
    // and where it would sit, since it lights both.
    val cx = frame.originX + EDGE_X * frame.tapePx / 2f
    val cy = frame.originY + ((EDGE_Y + FACE_HEIGHT) / 2f - LIFT / 2f) * frame.tapePx
    val dark = Color.Black.copy(alpha = SHADE)
    drawRect(
        brush = Brush.radialGradient(
            0f to Color.Transparent,
            0.25f to Color.Transparent,
            0.62f to dark,
            1f to dark,
            center = Offset(cx, cy),
            radius = 1.6f * frame.tapePx,
        ),
        blendMode = BlendMode.SrcAtop,
    )
}

/**
 * Every tape drawn once, already as the camera sees it: face, top and right
 * side in one bitmap, rebuilt only when the tapes change size on screen.
 */
private class TapeArt(private val items: List<TapeRackItem>, private val shell: Color) {
    var tapes: List<ImageBitmap> = emptyList()
        private set

    /** Where a bitmap's top-left sits from its tape's face corner. */
    var offsetX = 0f
        private set
    var offsetY = 0f
        private set

    private var builtFor = -1

    fun ensure(tapePx: Float) {
        val px = tapePx.roundToInt().coerceAtLeast(8)
        if (px == builtFor) return
        builtFor = px
        val w = px.toFloat()
        val ex = EDGE_X * w
        val ey = EDGE_Y * w
        val tx = THICKNESS * ROW_X * w
        val ty = THICKNESS * ROW_Y * w
        val vy = FACE_HEIGHT * w
        // One pixel of margin all round keeps the antialiased edges whole.
        offsetX = -1f
        offsetY = ty - 1f
        val bw = ceil(ex + tx).toInt() + 2
        val bh = ceil(ey + vy - ty).toInt() + 2

        val faceW = w.roundToInt()
        val faceH = (w * FACE_HEIGHT).roundToInt()
        val face = Matrix().apply {
            this[0, 0] = ex / faceW
            this[0, 1] = ey / faceW
            this[1, 0] = 0f
            this[1, 1] = vy / faceH
        }
        val top = Path().apply {
            moveTo(0f, 0f)
            lineTo(tx, ty)
            lineTo(ex + tx, ey + ty)
            lineTo(ex, ey)
            close()
        }
        val side = Path().apply {
            moveTo(ex, ey)
            lineTo(ex + tx, ey + ty)
            lineTo(ex + tx, ey + ty + vy)
            lineTo(ex, ey + vy)
            close()
        }
        val topColor = lerp(shell, Color.White, 0.14f)
        val sideColor = lerp(shell, Color.Black, 0.35f)

        tapes = items.map { item ->
            val flat = tapeFace(item.image, faceW, faceH, shell)
            val bitmap = ImageBitmap(bw, bh)
            CanvasDrawScope().draw(
                Density(1f),
                LayoutDirection.Ltr,
                BitmapCanvas(bitmap),
                Size(bw.toFloat(), bh.toFloat()),
            ) {
                translate(-offsetX, -offsetY) {
                    drawPath(top, topColor)
                    drawPath(side, sideColor)
                    withTransform({ transform(face) }) {
                        drawImage(
                            image = flat,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(flat.width, flat.height),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(flat.width, flat.height),
                            filterQuality = FilterQuality.High,
                        )
                    }
                }
            }
            bitmap
        }
    }
}

/**
 * The face of a tape, square on: the shell, a label carrying the picture, the
 * window with its two reels, the head opening along the foot and the screws.
 */
private fun tapeFace(source: ImageBitmap, w: Int, h: Int, shell: Color): ImageBitmap {
    val bitmap = ImageBitmap(w, h)
    val fw = w.toFloat()
    val fh = h.toFloat()
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        BitmapCanvas(bitmap),
        Size(fw, fh),
    ) {
        val corner = CornerRadius(SHELL_CORNER * fw)
        drawRoundRect(shell, cornerRadius = corner)
        val rim = 0.012f * fw
        drawRoundRect(
            color = lerp(shell, Color.White, 0.1f),
            topLeft = Offset(rim / 2f, rim / 2f),
            size = Size(fw - rim, fh - rim),
            cornerRadius = corner,
            style = Stroke(rim),
        )

        // Label, the picture cropped to it.
        val label = Rect(0.075f * fw, 0.08f * fh, 0.925f * fw, 0.74f * fh)
        val clip = Path().apply {
            addRoundRect(RoundRect(label, CornerRadius(0.02f * fw)))
        }
        clipPath(clip) {
            val ratio = label.width / label.height
            val srcRatio = source.width.toFloat() / max(source.height, 1)
            val cropW: Int
            val cropH: Int
            if (srcRatio > ratio) {
                cropH = source.height
                cropW = (source.height * ratio).roundToInt().coerceIn(1, source.width)
            } else {
                cropW = source.width
                cropH = (source.width / ratio).roundToInt().coerceIn(1, source.height)
            }
            drawImage(
                image = source,
                srcOffset = IntOffset((source.width - cropW) / 2, (source.height - cropH) / 2),
                srcSize = IntSize(cropW, cropH),
                dstOffset = IntOffset(label.left.roundToInt(), label.top.roundToInt()),
                dstSize = IntSize(label.width.roundToInt(), label.height.roundToInt()),
                filterQuality = FilterQuality.High,
            )
        }

        // Window and reels.
        val windowH = 0.22f * fh
        val windowTop = 0.47f * fh - windowH / 2f
        drawRoundRect(
            color = Color(0xE6101012),
            topLeft = Offset(0.24f * fw, windowTop),
            size = Size(0.52f * fw, windowH),
            cornerRadius = CornerRadius(windowH / 2f),
        )
        val reel = windowH * 0.38f
        for (rx in floatArrayOf(0.355f, 0.645f)) {
            val c = Offset(rx * fw, 0.47f * fh)
            drawCircle(Color(0xFFE6E1D6), radius = reel, center = c)
            drawCircle(Color(0xFF151517), radius = reel * 0.52f, center = c)
            drawCircle(Color(0xFFE6E1D6), radius = reel * 0.52f, center = c, style = Stroke(reel * 0.12f))
        }

        // Head opening along the foot.
        val foot = Path().apply {
            moveTo(0.22f * fw, fh)
            lineTo(0.27f * fw, 0.8f * fh)
            lineTo(0.73f * fw, 0.8f * fh)
            lineTo(0.78f * fw, fh)
            close()
        }
        drawPath(foot, lerp(shell, Color.White, 0.06f))
        for (hx in floatArrayOf(0.37f, 0.63f)) {
            drawCircle(Color(0xFF09090A), radius = 0.028f * fh, center = Offset(hx * fw, 0.9f * fh))
        }

        // Screws.
        val screw = 0.016f * fw
        val screwColor = Color(0xFF8E8E94)
        for ((sx, sy) in listOf(0.035f to 0.05f, 0.965f to 0.05f, 0.035f to 0.93f, 0.965f to 0.93f, 0.5f to 0.88f)) {
            drawCircle(screwColor, radius = screw, center = Offset(sx * fw, sy * fh))
        }
    }
    return bitmap
}

// --- caption -----------------------------------------------------------------

@Composable
private fun BoxScope.Caption(
    items: List<TapeRackItem>,
    state: TapeRackScrollState,
    contentColor: Color,
) {
    val item = items[wrapTape(state.slotted, items.size)]
    if (item.title.isEmpty() && item.artist.isEmpty()) return

    key(state.slotted) {
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 36.dp, end = 24.dp),
        ) {
            SplitText(
                text = item.title.uppercase(),
                style = TextStyle(
                    color = contentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.4.sp,
                ),
                splitBy = SplitBy.Chars,
                rise = 6.dp,
            )
            SplitText(
                text = item.artist.uppercase(),
                style = TextStyle(
                    color = contentColor.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                ),
                modifier = Modifier.padding(top = 4.dp),
                splitBy = SplitBy.Chars,
                rise = 6.dp,
                startDelay = 120,
            )
        }
    }
}
