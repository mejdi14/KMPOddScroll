package io.github.mejdi14.oddscroll.cdrack

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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** One case in the rack. */
data class CdRackItem(
    /** Printed on the disc inside the case, cropped square to fit it. */
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

/**
 * The face: its top edge falls 0.18 to the right, its sides are upright. A
 * jewel case is square, so the face is as tall as it is wide.
 */
private const val EDGE_X = 0.98419f
private const val EDGE_Y = 0.17715f
private const val FACE_HEIGHT = 1f

/** How deep a case is, back along the row: 10.4 mm on 146. */
private const val THICKNESS = 0.071f

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
 * A light sits over the slot: a case reads 18% brighter there than two cases
 * off it, whether or not it is lifted. One gradient over the lot.
 */
private const val SHADE = 0.15f

/* The case's own measurements are in CaseGeometry.kt, shared with CdCase. */

/** Share of the case's depth, from the face back, taken up by the lid. */
private const val LID_DEPTH = 0.19f

private const val TAP_SLOP = 8f

@Composable
fun rememberCdRackScrollState(intro: Int = 4): CdRackScrollState =
    remember { CdRackScrollState(intro) }

/**
 * A row of clear CD jewel cases standing on edge, seen from above and to the
 * left, that slides along its own length — and the case that comes to the slot
 * in the middle of the frame rises out of the row.
 *
 * The whole rack is one number. Each tape stands a fixed pitch up the row from
 * the one before, so where the rack has slid to says where every tape is, and
 * how high a tape stands is a fixed function of how far it is from the slot:
 * tapes rise as they come into it and settle back as they leave, with nothing
 * timed per tape. Nothing turns, grows, fades or rounds off; the camera is far
 * enough away that every tape is the same shape on screen, so each is drawn
 * once into a bitmap and then only moved — one `drawImage` a tape a frame.
 *
 * The cases are clear, so each is drawn translucent and the ones behind show
 * through the plastic round its disc.
 *
 * Drag along the row, or across it, and the cases follow the finger; flick to
 * throw it, scroll to step it a case, or tap a case to bring it to the slot.
 *
 * @param items cases, in order; the rack repeats, so it has no end.
 * @param background page colour.
 * @param shellColor the tint of the cases' clear plastic.
 * @param contentColor caption text.
 * @param captions say what the tape in the slot is, top left.
 * @param state also sets how far the rack slides when it first appears.
 */
@Composable
fun CdRackScroll(
    items: List<CdRackItem>,
    modifier: Modifier = Modifier,
    background: Color = Color(0xFF050506),
    shellColor: Color = Color(0xFFDBE6ED),
    contentColor: Color = Color(0xFFEDEDE8),
    captions: Boolean = true,
    state: CdRackScrollState = rememberCdRackScrollState(),
) {
    if (items.isEmpty()) {
        Box(modifier)
        return
    }

    val art = remember(items, shellColor) { CaseArt(items, shellColor) }
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
            art.ensure(frame.casePx)
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
    var casePx = 1f
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
        casePx = min(w * 0.4f, h * 0.36f)
        pitchPx = PITCH * casePx
        // The lifted tape's face is centred in the frame.
        originX = w / 2f - (EDGE_X * casePx) / 2f
        originY = h / 2f - (EDGE_Y + FACE_HEIGHT) * casePx / 2f + LIFT * casePx

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
        val right = x + (EDGE_X + THICKNESS * ROW_X) * casePx
        val top = y + (THICKNESS * ROW_Y - LIFT) * casePx
        val bottom = y + (EDGE_Y + FACE_HEIGHT) * casePx
        return right > 0f && x < width && bottom > 0f && top < height
    }

    /** Top-left corner of tape [index]'s face, [w] tapes from the slot, into [out]. */
    fun corner(w: Float, index: Int, out: FloatArray) {
        val along = w * PITCH + jitter(index, 0) * JITTER_ALONG
        val across = jitter(index, 1) * JITTER_ACROSS
        out[0] = originX + (along * ROW_X - across * ROW_Y) * casePx
        out[1] = originY + (along * ROW_Y + across * ROW_X - lift(w) * LIFT) * casePx
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
            corner(i - position, wrapCase(i, count), corner)
            outline(corner[0], corner[1])
            if (inside(x, y)) return i
        }
        return null
    }

    /** The six corners of a tape's silhouette: face, top and right side. */
    private fun outline(x: Float, y: Float) {
        val ex = EDGE_X * casePx
        val ey = EDGE_Y * casePx
        val tx = THICKNESS * ROW_X * casePx
        val ty = THICKNESS * ROW_Y * casePx
        val vy = FACE_HEIGHT * casePx
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

private fun DrawScope.drawRack(art: CaseArt, position: Float, frame: RackFrame) {
    if (size.width <= 0f || size.height <= 0f) return
    val count = art.cases.size
    val corner = FloatArray(2)

    // Back of the row first: each tape covers the one behind it.
    val first = ceil(position + frame.nearest).toInt()
    val last = floor(position + frame.farthest).toInt()
    for (i in last downTo first) {
        val index = wrapCase(i, count)
        frame.corner(i - position, index, corner)
        val image = art.cases[index]
        // Translated rather than offset by whole pixels, so a slow slide does
        // not step.
        withTransform({ translate(corner[0] + art.offsetX, corner[1] + art.offsetY) }) {
            drawImage(image)
        }
    }

    // The light over the slot, centred between where the slotted tape stands
    // and where it would sit, since it lights both.
    val cx = frame.originX + EDGE_X * frame.casePx / 2f
    val cy = frame.originY + ((EDGE_Y + FACE_HEIGHT) / 2f - LIFT / 2f) * frame.casePx
    val dark = Color.Black.copy(alpha = SHADE)
    drawRect(
        brush = Brush.radialGradient(
            0f to Color.Transparent,
            0.25f to Color.Transparent,
            0.62f to dark,
            1f to dark,
            center = Offset(cx, cy),
            radius = 1.6f * frame.casePx,
        ),
        blendMode = BlendMode.SrcAtop,
    )
}

/**
 * Every case drawn once, already as the camera sees it: face, top and right
 * side in one bitmap, rebuilt only when the cases change size on screen.
 */
private class CaseArt(private val items: List<CdRackItem>, private val shell: Color) {
    var cases: List<ImageBitmap> = emptyList()
        private set

    /** Where a bitmap's top-left sits from its tape's face corner. */
    var offsetX = 0f
        private set
    var offsetY = 0f
        private set

    private var builtFor = -1

    fun ensure(casePx: Float) {
        val px = casePx.roundToInt().coerceAtLeast(8)
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
        // Seen edge on, clear plastic is a lot of plastic: the top band reads
        // pale, the shaded side less so, with the seam where the lid closes.
        val seam = Path().apply {
            moveTo(LID_DEPTH * tx, LID_DEPTH * ty)
            lineTo(ex + LID_DEPTH * tx, ey + LID_DEPTH * ty)
            lineTo(ex + LID_DEPTH * tx, ey + LID_DEPTH * ty + vy)
        }
        val hairline = max(1f, w / 300f)

        cases = items.map { item ->
            val flat = caseFace(item.image, faceW, shell)
            val bitmap = ImageBitmap(bw, bh)
            CanvasDrawScope().draw(
                Density(1f),
                LayoutDirection.Ltr,
                BitmapCanvas(bitmap),
                Size(bw.toFloat(), bh.toFloat()),
            ) {
                translate(-offsetX, -offsetY) {
                    drawPath(top, shell.copy(alpha = 0.26f))
                    drawPath(side, shell.copy(alpha = 0.11f))
                    drawPath(seam, Color.Black.copy(alpha = 0.45f), style = Stroke(hairline))
                    drawPath(top, Color.White.copy(alpha = 0.35f), style = Stroke(hairline))
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
 * The face of a case, square on, through the lid: the tray's rails and ribs,
 * the disc carrying the picture, the hub that holds it, the lid and its hinge.
 * Drawn in millimetres, back to front, and all of it but the disc's print
 * translucent, so whatever is behind the case shows through its plastic.
 */
private fun caseFace(source: ImageBitmap, px: Int, tint: Color): ImageBitmap {
    val bitmap = ImageBitmap(px, px)
    val size = px.toFloat()
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        BitmapCanvas(bitmap),
        Size(size, size),
    ) {
        val k = size / CASE_MM
        withTransform({
            translate(size / 2f, size / 2f)
            scale(k, k, Offset.Zero)
        }) {
            tray(tint)
            disc(source, tint)
            hub(tint)
            lid(tint)
        }
    }
    return bitmap
}

private fun DrawScope.tray(tint: Color) {
    // The floor, and the thin rim round its edge.
    drawPath(roundedSquare(HALF_MM, 8f), tint.copy(alpha = 0.05f))
    rod(roundedSquare(HALF_MM - 0.75f, 7.4f), 1.5f, tint, 0.7f)

    // The rail round the tray, and the collars along it.
    val at = HALF_MM - 3.3f
    rod(roundedSquare(at, 6.5f), 6.6f, tint)
    for (s in floatArrayOf(-1f, 1f)) {
        for (c in floatArrayOf(-35f, -12f, 12f, 35f)) {
            rod(segment(c - 1.2f, s * at, c + 1.2f, s * at), 7.5f, tint, 0.8f)
            // The tab takes the middle of the hinge side.
            if (s < 0f && abs(c) < 20f) continue
            rod(segment(s * at, c - 1.2f, s * at, c + 1.2f), 7.5f, tint, 0.8f)
        }
    }

    // A rib in each corner, stepped back to the rail at both ends.
    val e = HALF_MM - 9.6f
    for (sx in floatArrayOf(-1f, 1f)) {
        for (sy in floatArrayOf(-1f, 1f)) {
            val along = if (sx < 0f) 26f else 35f
            rod(corner(sx * e, sy * along, sx * e, sy * e, sx * 35f, sy * e, 4.5f), 4.8f, tint)
            rod(segment(sx * e, sy * along, sx * at, sy * along), 3.8f, tint, 0.8f)
            rod(segment(sx * 35f, sy * e, sx * 35f, sy * at), 3.8f, tint, 0.8f)
        }
    }

    // The well the disc sits in, and the clips at its edge.
    rod(circle(61.6f), 2.3f, tint)
    rod(circle(60.05f), 1f, tint, 0.6f)
    for (deg in floatArrayOf(59f, 121f, 238.5f, 301.5f)) {
        val a = deg / 180f * PI.toFloat()
        val dx = cos(a)
        val dy = -sin(a)
        rod(segment(dx * 58.6f, dy * 58.6f, dx * 63.2f, dy * 63.2f), 3.2f, tint, 0.8f)
    }

    // The two hooks along the foot that hold the booklet.
    for (sx in floatArrayOf(-1f, 1f)) {
        rod(corner(sx * 46f, e, sx * 46f, 49.5f, sx * 42.6f, 45.6f, 4.2f), 3.8f, tint, 0.8f)
    }
}

private fun DrawScope.disc(source: ImageBitmap, tint: Color) {
    // The disc's edge, and the bare dark ring inside the print.
    drawPath(ring(DISC_R, PRINT_OUT), Color(0xFF66686D))
    drawPath(ring(PRINT_IN, CLEAR_R), Color(0xFF0E0E10))

    clipPath(ring(PRINT_OUT, PRINT_IN)) {
        val crop = min(source.width, source.height).coerceAtLeast(1)
        val d = (2f * PRINT_OUT).roundToInt()
        drawImage(
            image = source,
            srcOffset = IntOffset((source.width - crop) / 2, (source.height - crop) / 2),
            srcSize = IntSize(crop, crop),
            dstOffset = IntOffset(-d / 2, -d / 2),
            dstSize = IntSize(d, d),
            filterQuality = FilterQuality.High,
        )
        // The gloss on the print, caught along two opposite radii the way a
        // disc's tracks throw the light back.
        drawCircle(
            brush = Brush.sweepGradient(
                0f to Color.Transparent,
                0.08f to Color.White.copy(alpha = 0.16f),
                0.18f to Color.Transparent,
                0.5f to Color.Transparent,
                0.58f to Color.White.copy(alpha = 0.1f),
                0.68f to Color.Transparent,
                1f to Color.Transparent,
                center = Offset.Zero,
            ),
            radius = PRINT_OUT,
            center = Offset.Zero,
        )
    }

    // A rainbow round the rim, where the bare tracks show past the print.
    drawCircle(
        brush = Brush.sweepGradient(
            0f to Color(0xFFFF5E8A),
            0.17f to Color(0xFFFFD35E),
            0.33f to Color(0xFF6EF0A0),
            0.5f to Color(0xFF5EC8FF),
            0.67f to Color(0xFFA77BFF),
            0.83f to Color(0xFFFF7BD5),
            1f to Color(0xFFFF5E8A),
            center = Offset.Zero,
        ),
        radius = (DISC_R + PRINT_OUT) / 2f,
        center = Offset.Zero,
        alpha = 0.55f,
        style = Stroke(DISC_R - PRINT_OUT),
    )
    drawCircle(Color.Black.copy(alpha = 0.35f), radius = 16.6f, center = Offset.Zero, style = Stroke(0.5f))

    // The clear middle the hub shows through, and the edge of the hole.
    drawPath(ring(CLEAR_R, DISC_HOLE), tint.copy(alpha = 0.12f))
    rod(circle(DISC_HOLE + 0.25f), 0.5f, tint, 0.8f)
}

private fun DrawScope.hub(tint: Color) {
    val cap = Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(Rect(Offset.Zero, 19.4f))
        addOval(Rect(Offset.Zero, 6.2f))
        for (hx in floatArrayOf(-1f, 1f)) for (hy in floatArrayOf(-1f, 1f)) {
            addOval(Rect(Offset(hx * 8.9f, hy * 8.9f), 1.7f))
        }
    }
    drawPath(cap, tint.copy(alpha = 0.08f))
    rod(circle(18.8f), 1.6f, tint)
    rod(circle(16.5f), 0.9f, tint, 0.7f)
    rod(circle(9.6f), 1.9f, tint)
    rod(circle(6.6f), 1.1f, tint, 0.8f)
    for (hx in floatArrayOf(-1f, 1f)) for (hy in floatArrayOf(-1f, 1f)) {
        rod(circle(2f, hx * 8.9f, hy * 8.9f), 0.9f, tint, 0.7f)
    }
}

private fun DrawScope.lid(tint: Color) {
    // The tab on the hinge side, over the edge of the disc.
    val tabLeft = -HALF_MM
    val tabRight = -50.5f
    val tabMid = (tabLeft + tabRight) / 2f
    val tabHw = (tabRight - tabLeft) / 2f
    drawPath(roundedRect(tabMid, 0f, tabHw, 22f, 4.2f), tint.copy(alpha = 0.07f))
    rod(roundedRect(tabMid, 0f, tabHw - 0.8f, 21.2f, 3.4f), 1.6f, tint, 0.8f)
    rod(
        Path().apply {
            moveTo(-69f, -13f)
            lineTo(-61.7f, -13f)
            quadraticTo(-58.5f, -13f, -58.5f, -9.8f)
            lineTo(-58.5f, 9.8f)
            quadraticTo(-58.5f, 13f, -61.7f, 13f)
            lineTo(-69f, 13f)
        },
        2.7f,
        tint,
    )
    rod(roundedRect(-55.4f, 0f, 4.4f, 3.2f, 1.2f), 1.3f, tint, 0.7f)

    // The lid, its rim, the notches along its edges and the hinge.
    drawPath(roundedSquare(HALF_MM, 8f), tint.copy(alpha = 0.04f))
    rod(roundedSquare(HALF_MM - 1f, 7f), 2f, tint)
    val inset = HALF_MM - 3.4f
    rod(roundedSquare(inset, 5f), 0.84f, tint, 0.6f)
    for (x in floatArrayOf(-42f, 30f)) {
        rod(
            Path().apply {
                arcTo(Rect(Offset(x, -inset), 4.4f), 0f, 180f, true)
                arcTo(Rect(Offset(x, inset), 4.4f), 180f, 180f, true)
            },
            0.84f,
            tint,
            0.6f,
        )
    }
    for (sx in floatArrayOf(-1f, 1f)) for (y in floatArrayOf(-30f, 30f)) {
        rod(segment(sx * inset, y - 3f, sx * inset, y + 3f), 1f, tint, 0.6f)
    }
    for ((y0, y1) in listOf(-63f to -44f, -42f to -20f, 20f to 42f, 44f to 63f)) {
        rod(segment(HINGE_X, y0, HINGE_X, y1), 4.2f, tint, cap = StrokeCap.Butt)
    }

    // A long glare across the whole lid, from the softbox over the left.
    drawPath(
        roundedSquare(HALF_MM, 8f),
        Brush.linearGradient(
            0f to Color.Transparent,
            0.26f to Color.Transparent,
            0.33f to Color.White.copy(alpha = 0.11f),
            0.4f to Color.Transparent,
            0.62f to Color.Transparent,
            0.66f to Color.White.copy(alpha = 0.05f),
            0.7f to Color.Transparent,
            1f to Color.Transparent,
            start = Offset(-HALF_MM, -HALF_MM),
            end = Offset(HALF_MM, HALF_MM),
        ),
    )
}

/**
 * A clear rod, seen from the front: it bends the light from behind away, so
 * it darkens what it lies over, with a pale body and a bright line down the
 * side the light comes from and a dimmer one opposite.
 */
private fun DrawScope.rod(
    path: Path,
    width: Float,
    tint: Color,
    strength: Float = 1f,
    cap: StrokeCap = StrokeCap.Round,
) {
    drawPath(path, Color.Black.copy(alpha = 0.3f * strength), style = Stroke(width, cap = cap, join = StrokeJoin.Round))
    drawPath(path, tint.copy(alpha = 0.16f * strength), style = Stroke(width, cap = cap, join = StrokeJoin.Round))
    val line = Stroke(width * 0.16f, cap = cap, join = StrokeJoin.Round)
    translate(-width * 0.24f, -width * 0.24f) {
        drawPath(path, Color.White.copy(alpha = 0.6f * strength), style = line)
    }
    translate(width * 0.28f, width * 0.28f) {
        drawPath(path, Color.White.copy(alpha = 0.18f * strength), style = line)
    }
}

// --- caption -----------------------------------------------------------------

@Composable
private fun BoxScope.Caption(
    items: List<CdRackItem>,
    state: CdRackScrollState,
    contentColor: Color,
) {
    val item = items[wrapCase(state.slotted, items.size)]
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
