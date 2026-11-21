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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mejdi14.oddscroll.internal.clamped
import io.github.mejdi14.oddscroll.internal.smoothstep
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One case in the rack. */
data class CdRackItem(
    /** Printed on the disc inside the case, cropped square to fit it. */
    val image: ImageBitmap,
    /** First line of the caption while the case is in the slot; empty for none. */
    val title: String = "",
    /** Second line of the caption, under the title. */
    val artist: String = "",
    /** Slipped in under the lid's glass, cropped square to fill it; none leaves the lid clear. */
    val cover: ImageBitmap? = null,
)

/*
 * The rack, in case widths, as measured off the reference — a row of
 * cassettes, which is why the numbers are a tape's. Its camera is far enough
 * away to be orthographic: a projective fit along the row comes back with a
 * perspective term of 0.0005 a tape, nothing over the dozen in view.
 *
 * The row runs up and to the right at 27.29°, a case every 0.322 widths; the
 * face's top edge falls 0.18 to the right and its sides stand upright.
 */
private const val ROW_X = 0.88869f
private const val ROW_Y = -0.45849f
private const val PITCH = 0.322f

/*
 * Those three measurements are one turn of the case in 3D, exactly: tipped
 * back 0.310 rad and turned 0.534 rad to the left, no roll, the cases standing
 * face to face along their own axis — which is what makes the row climb to
 * the right. At that turn the face spans 0.875 of its width across the screen
 * and the axis 0.572 a millimetre, so the 0.322-width pitch is 71.9 mm
 * between cases, and the 0.342-width rise is 43.7 mm straight up.
 */
private const val RACK_PITCH = 0.3096594f
private const val RACK_YAW = -0.5335270f
private const val FACE_ACROSS = 0.8748554f
private const val SPACING_MM = 71.86961f
private const val LIFT_MM = 43.68f

/** The row's axis in the world: back along each case's face normal. */
private val AXIS = floatArrayOf(0.5085733f, 0.2623819f, -0.8200664f)

/**
 * How far out the rack's camera dollies. The lens lengthens to match, so the
 * case at the slot keeps its size and the row flattens to the reference's
 * orthographic look.
 */
private const val RACK_ZOOM = 24f

/** The sharpest the disc's print is kept for a case drawn in the rack. */
private const val RACK_ART_PX = 512

/** How much of [CdRackScrollState.loaded] baking the case's pieces counts for, in cases. */
private const val PREPARE_SHARE = 1f

/*
 * The case in the slot stands proud of the rest, straight up: flat over the
 * middle 0.077 cases either side and gone by 1.002, a smoothstep between.
 */
private const val LIFT_FLAT = 0.077f
private const val LIFT_GONE = 1.002f


/** A finger that moves further than this, in pixels, is dragging rather than tapping. */
private const val TAP_SLOP = 8f

/**
 * A row of clear CD jewel cases standing on edge, seen from above and to the
 * left, that slides along its own length — the case that comes to the slot in
 * the middle of the frame rises out of the row.
 *
 * Drag along the row, or across it, and the cases follow the finger; flick to
 * throw it, or scroll to step it a case. Tap a case and it slides to the slot,
 * unless [onItemClick] takes the tap instead.
 *
 * Each case is drawn once in 3D at the rack's turn — the disc's print, the
 * cover under the lid's glass, the clear plastic lit a pixel at a time where
 * the platform has the shaders for it — and from then on only moved, since
 * the rack's camera is far enough off that every case is the same shape on
 * screen. That drawing runs when the rack first has a size, and again when
 * its size changes; [CdRackScrollState.loaded] says how far it has got, for a
 * host that wants to show a loader over the rack until it is done.
 *
 * @param items cases, in order; the rack repeats, so it has no end.
 * @param modifier the rack fills whatever size this gives it.
 * @param background the backdrop; the clear plastic is shaded to sit on a pale one.
 * @param contentColor caption text.
 * @param caseSize how wide a case's face stands on screen, as a share of the
 *   frame's width — at 0.4 about two and a half fit across — and at most 0.9
 *   of that share of the frame's height, so a wide frame does not blow them
 *   up. The spacing along the row goes with it, so smaller cases show more of
 *   the row.
 * @param captions say what the case in the slot is, top left, from its
 *   [CdRackItem.title] and [CdRackItem.artist].
 * @param userScrollEnabled false ignores drags, flicks and the wheel, and a
 *   tap no longer slides a case to the slot; [state] can still move the rack,
 *   and [onItemClick] is still called.
 * @param onItemClick called with the tapped case's index in [items] in place
 *   of sliding it to the slot — the host can call
 *   [CdRackScrollState.animateScrollTo] itself. Null keeps the slide.
 * @param state where the rack is; see [rememberCdRackScrollState].
 */
@Composable
fun CdRackScroll(
    items: List<CdRackItem>,
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    contentColor: Color = Color(0xFF111114),
    caseSize: Float = 0.4f,
    captions: Boolean = true,
    userScrollEnabled: Boolean = true,
    onItemClick: ((index: Int) -> Unit)? = null,
    state: CdRackScrollState = rememberCdRackScrollState(),
) {
    require(caseSize > 0f) { "caseSize must be above 0, was $caseSize" }
    SideEffect { state.count = items.size }
    if (items.isEmpty()) {
        Box(modifier.background(background))
        return
    }

    // Kept for as long as the pictures are the same: a host that builds its
    // list afresh each composition would otherwise throw the drawn rack away
    // every time it recomposes.
    val rack = remember(items.map { it.image }, items.map { it.cover }) { RackScene(items) }
    rack.items = items
    var frameSize by remember { mutableStateOf(IntSize.Zero) }
    val graphics = LocalGraphicsContext.current
    val layerDensity = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val scrollEnabled by rememberUpdatedState(userScrollEnabled)
    val onClick by rememberUpdatedState(onItemClick)

    LaunchedEffect(state, rack) {
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                val deltaMs = if (previous == 0L) 16.67f else (now - previous) / 1_000_000f
                previous = now
                state.advance(deltaMs.clamped(1f, 50f))
            }
        }
    }

    // Each case drawn once at the rack's turn — on Android and through Skia,
    // its shaders and all.
    LaunchedEffect(rack, frameSize, caseSize) {
        if (frameSize.width <= 0 || frameSize.height <= 0) return@LaunchedEffect
        state.loaded = 0f
        rack.fit(frameSize.width.toFloat(), frameSize.height.toFloat(), caseSize)
        withContext(Dispatchers.Default) { rack.scene.prepare(rack.rackCamera) }
        val box = rack.spriteBox()
        rack.sprites.fill(null)
        rack.releaseLayers(graphics)
        rack.box = box
        // The print need be no sharper than the disc stands in the rack.
        val artPx = (box.width * 1.5f).roundToInt().coerceIn(256, RACK_ART_PX)
        val cases = rack.items
        for (index in cases.indices) {
            state.loaded = (index + PREPARE_SHARE) / (cases.size + PREPARE_SHARE)
            withContext(Dispatchers.Default) { rack.scene.setArt(cases[index].image, artPx) }
            rack.scene.setCover(cases[index].cover)
            val layer = graphics.createGraphicsLayer()
            layer.record(layerDensity, direction, IntSize(box.width, box.height)) {
                translate(-box.left.toFloat(), -box.top.toFloat()) {
                    rack.scene.draw(this, rack.rackCamera, rack.rackFrame)
                }
            }
            if (rackKeepsLayers) {
                rack.layers[index] = layer
            } else {
                try {
                    rack.sprites[index] = layer.toImageBitmap()
                } finally {
                    graphics.releaseGraphicsLayer(layer)
                }
            }
        }
        state.loaded = 1f
    }
    DisposableEffect(rack) { onDispose { rack.releaseLayers(graphics) } }

    Box(modifier.background(background)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state, rack) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var last = down.position
                        var travel = 0f
                        var started = false
                        // Whether this gesture drags the rack, fixed as it starts.
                        val scrolls = scrollEnabled
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dx = change.position.x - last.x
                            val dy = change.position.y - last.y
                            last = change.position
                            travel += abs(dx) + abs(dy)
                            if (travel <= TAP_SLOP) continue
                            if (!started) {
                                started = true
                                if (scrolls) state.onDragStart(change.uptimeMillis / 1000f)
                            }
                            if (!scrolls) continue
                            // Only the part of the drag that runs along the
                            // row counts, and up the row is back through the
                            // rack: the cases follow the finger.
                            val along = dx * ROW_X + dy * ROW_Y
                            state.onDrag(-along / max(rack.pitchPx, 1f), change.uptimeMillis / 1000f)
                            change.consume()
                        }
                        if (started) {
                            if (scrolls) state.onDragEnd()
                            return@awaitEachGesture
                        }
                        val hit = rack.hit(down.position.x, down.position.y, state.position) ?: return@awaitEachGesture
                        val click = onClick
                        when {
                            click != null -> click(wrapCase(hit, rack.items.size))
                            scrollEnabled -> state.travelTo(hit)
                        }
                    }
                }
                .pointerInput(state) {
                    // A notch is a case. A trackpad sends fractions of one,
                    // so they are added up until they come to a whole one.
                    var notches = 0f
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll || !scrollEnabled) continue
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
                },
        ) {
            state.tick // read, so the rack repaints every frame
            val w = size.width.toInt()
            val h = size.height.toInt()
            if (frameSize.width != w || frameSize.height != h) frameSize = IntSize(w, h)
            rack.fit(size.width, size.height, caseSize)
            rack.draw(this, state.position)
        }

        if (captions) Caption(items, state, contentColor)
    }
}

// --- the rack ------------------------------------------------------------------

private class SpriteBox(val left: Int, val top: Int, val width: Int, val height: Int)

/** The rack: a camera dollied far out for the row, and each case drawn at the rack's turn. */
private class RackScene(var items: List<CdRackItem>) {
    val scene = CaseScene()
    val rackCamera = Camera().also { it.zoom = RACK_ZOOM }

    /** How every case in the rack is turned and sized, standing at the origin. */
    val rackFrame = Pose()

    val sprites = arrayOfNulls<ImageBitmap>(items.size)

    /** Each case as recorded, in place of [sprites] where [rackKeepsLayers]. */
    val layers = arrayOfNulls<GraphicsLayer>(items.size)

    fun releaseLayers(graphics: GraphicsContext) {
        for (i in layers.indices) {
            layers[i]?.let { graphics.releaseGraphicsLayer(it) }
            layers[i] = null
        }
    }

    var box: SpriteBox? = null

    /** How large the rack draws a case. */
    private var rackScale = 1f

    /** One place along the row, on screen. */
    var pitchPx = 1f
        private set

    private var width = -1f
    private var height = -1f
    private var caseSize = -1f

    /** Places from the slot between which a case can be seen, either way. */
    private var reach = 1f

    private val frame = Pose()
    private val point = FloatArray(3)
    private val screen = FloatArray(8)
    private val origin = FloatArray(2)
    private val at = FloatArray(2)

    fun fit(w: Float, h: Float, caseSize: Float) {
        if (w == width && h == height && caseSize == this.caseSize) return
        width = w
        height = h
        this.caseSize = caseSize
        rackCamera.fit(w, h)
        // By default, the face as wide on screen as the reference's tapes.
        val facePx = min(w * caseSize, h * caseSize * 0.9f)
        rackScale = facePx / (CASE_MM * FACE_ACROSS * rackCamera.scaleAt(0f))
        pitchPx = PITCH * facePx
        eulerXyz(RACK_PITCH, RACK_YAW, 0f, rackFrame.r)
        rackFrame.s = rackScale
        reach = hypot(w, h) / pitchPx + 2f
        rackCamera.project(0f, 0f, 0f, origin)
    }

    /** The screen box a case at the slot takes up, lifted, walls and hinge and all. */
    fun spriteBox(): SpriteBox {
        var x0 = Float.MAX_VALUE
        var y0 = Float.MAX_VALUE
        var x1 = -Float.MAX_VALUE
        var y1 = -Float.MAX_VALUE
        for (sx in floatArrayOf(-1f, 1f)) for (sy in floatArrayOf(-1f, 1f)) for (sz in floatArrayOf(-1f, 1f)) {
            rackFrame.apply(sx * (HALF_MM + 2f), sy * (HALF_MM + 2f), sz * 7f, point)
            if (!rackCamera.project(point[0], point[1], point[2], screen)) continue
            x0 = min(x0, screen[0]); x1 = max(x1, screen[0])
            y0 = min(y0, screen[1]); y1 = max(y1, screen[1])
        }
        val left = floor(x0).toInt() - 4
        val top = floor(y0).toInt() - 4
        return SpriteBox(left, top, ceil(x1).toInt() + 4 - left, ceil(y1).toInt() + 4 - top)
    }

    /** Where case [w] places from the slot stands, in the world, into [out]. */
    private fun centre(w: Float, out: FloatArray) {
        val s = rackScale
        val drop = (lift(w) - 1f) * LIFT_MM
        out[0] = s * w * SPACING_MM * AXIS[0]
        out[1] = s * (w * SPACING_MM * AXIS[1] + drop)
        out[2] = s * w * SPACING_MM * AXIS[2]
    }

    /** The rack slid to [position]: one drawing per case in view. */
    fun draw(scope: DrawScope, position: Float) = with(scope) {
        val b = box ?: return@with
        val count = items.size
        val first = ceil(position - reach).toInt()
        val last = floor(position + reach).toInt()
        // Back of the row first: each case covers the one behind it.
        for (i in last downTo first) {
            val sprite = sprites[wrapCase(i, count)]
            val layer = layers[wrapCase(i, count)]
            if (sprite == null && layer == null) continue
            if (!spriteAt(i - position)) continue
            val x = at[0]
            val y = at[1]
            if (x > size.width || y > size.height || x + b.width < 0f || y + b.height < 0f) continue
            // Translated rather than offset by whole pixels, so a slow slide
            // does not step.
            translate(x, y) { if (layer != null) drawLayer(layer) else drawImage(sprite!!) }
        }
    }

    /** The top-left of the sprite of a case [w] places from the slot, into [at]. */
    private fun spriteAt(w: Float): Boolean {
        val b = box ?: return false
        centre(w, point)
        if (!rackCamera.project(point[0], point[1], point[2], screen)) return false
        at[0] = b.left + screen[0] - origin[0]
        at[1] = b.top + screen[1] - origin[1]
        return true
    }

    /**
     * The case under [x], [y] with the rack at [position], unwrapped, or null.
     * The front of the row covers the back, so cases are tried front first.
     */
    fun hit(x: Float, y: Float, position: Float): Int? {
        frame.copyFrom(rackFrame)
        val first = ceil(position - reach).toInt()
        val last = floor(position + reach).toInt()
        for (i in first..last) {
            centre(i - position, point)
            frame.t[0] = point[0]; frame.t[1] = point[1]; frame.t[2] = point[2]
            var ok = true
            for ((k, corner) in CORNERS.withIndex()) {
                frame.apply(corner.first * HALF_MM, corner.second * HALF_MM, 0f, point)
                ok = ok && rackCamera.project(point[0], point[1], point[2], screen, k * 2)
            }
            if (ok && inside(x, y)) return i
        }
        return null
    }

    // Inside a convex outline is on the same side of every edge.
    private fun inside(x: Float, y: Float): Boolean {
        var sign = 0f
        for (k in 0 until 4) {
            val ax = screen[k * 2]
            val ay = screen[k * 2 + 1]
            val bx = screen[(k + 1) % 4 * 2]
            val by = screen[(k + 1) % 4 * 2 + 1]
            val cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
            if (cross == 0f) continue
            if (sign == 0f) sign = cross else if (sign * cross < 0f) return false
        }
        return true
    }
}

private val CORNERS = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)

/** How far up a case [w] places from the slot stands, from 0 to 1. */
private fun lift(w: Float): Float = 1f - smoothstep(LIFT_FLAT, LIFT_GONE, abs(w))

// --- caption -----------------------------------------------------------------

@Composable
private fun BoxScope.Caption(
    items: List<CdRackItem>,
    state: CdRackScrollState,
    contentColor: Color,
) {
    val item = items[state.currentIndex.coerceIn(0, items.lastIndex)]
    if (item.title.isEmpty() && item.artist.isEmpty()) return

    Column(
        Modifier
            .align(Alignment.TopStart)
            .padding(start = 24.dp, top = 36.dp, end = 24.dp),
    ) {
        BasicText(
            text = item.title.uppercase(),
            style = TextStyle(
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.4.sp,
            ),
        )
        BasicText(
            text = item.artist.uppercase(),
            style = TextStyle(
                color = contentColor.copy(alpha = 0.45f),
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
            ),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
