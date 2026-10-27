package io.github.mejdi14.oddscroll.cdrack

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
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
import io.github.mejdi14.oddscroll.internal.inOutCubic
import io.github.mejdi14.oddscroll.internal.lerp
import io.github.mejdi14.oddscroll.internal.smoothstep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/** One case in the rack. */
data class CdRackItem(
    /** Printed on the disc inside the case, cropped square to fit it. */
    val image: ImageBitmap,
    val title: String = "",
    /** Second line of the caption, under the title. */
    val artist: String = "",
    /** Slipped in under the lid's glass, cropped square to fill it; none leaves the lid clear. */
    val cover: ImageBitmap? = null,
    /**
     * The rest of the booklet [cover] is the front of. Read in off the main
     * thread while the case is out, and let go when it goes back; none leaves
     * the cover a single sheet.
     */
    val manual: (suspend () -> CdManual)? = null,
)

/** A case's booklet behind its cover: its spreads in reading order, and its back. */
class CdManual(
    /**
     * Each a picture of the booklet lying open, its spine down the middle, on
     * a clear ground: each half is cropped square and turns as a page.
     */
    val spreads: List<ImageBitmap>,
    /** The back of the booklet, cropped square; none leaves it plain paper. */
    val back: ImageBitmap? = null,
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
 * orthographic look; taking a case out, the camera comes back in.
 */
private const val RACK_ZOOM = 24f

/*
 * The case in the slot stands proud of the rest, straight up: flat over the
 * middle 0.077 cases either side and gone by 1.002, a smoothstep between.
 */
private const val LIFT_FLAT = 0.077f
private const val LIFT_GONE = 1.002f

/**
 * Cases leave the rack nearest first, this many seconds apart a place, up to
 * this many places out; past it they are off the frame anyway.
 */
private const val EXIT_STAGGER = 0.035f
private const val EXIT_STAGGER_PLACES = 8

/** A leaving case has faded out by this far along its way off. */
private const val EXIT_FADE = 0.8f

private const val TAP_SLOP = 8f

/** A drag across an open manual this long, in dp, turns a leaf. */
private const val LEAF_SWIPE = 24f

@Composable
fun rememberCdRackScrollState(intro: Int = 4): CdRackScrollState =
    remember { CdRackScrollState(intro) }

/**
 * A row of clear CD jewel cases standing on edge, seen from above and to the
 * left, that slides along its own length — the case that comes to the slot in
 * the middle of the frame rises out of the row. Tap a case and it comes to the
 * slot, the rest of the row clears off the screen, and it turns to face you:
 * drag it round, tap it to open it. Open, [inspectLabel] takes its cover out:
 * the cover turns over and comes to rest under the case — beside it, wider
 * than tall — which shuts once the cover has cleared it and moves over to
 * make room. With a [CdRackItem.manual] behind it, the cover is a
 * booklet: tap it and it folds open over its spine, coming up into the middle
 * as the case goes up out of the way; swipe it, tap a page or scroll to turn
 * its leaves. [CdRackScrollState.leave] — the system's back, say — shuts the
 * manual, puts the cover back, then the case, and brings the row in again.
 *
 * Each case is the same 3D case as [CdCase], drawn once at the rack's turn and
 * then only moved, since the rack's camera is far enough off that every case
 * is the same shape on screen; the one taken out is drawn live from then on.
 *
 * Drag along the row, or across it, and the cases follow the finger; flick to
 * throw it, or scroll to step it a case.
 *
 * @param items cases, in order; the rack repeats, so it has no end.
 * @param background the backdrop; the clear plastic is shaded for a pale one.
 * @param contentColor caption text.
 * @param captions say what the case in the slot is, top left.
 * Under the case, Back and a forward button walk it through its steps — shut,
 * open, its cover out, then each spread of the manual.
 *
 * @param inspectLabel the forward button's label on the open case, which takes
 *   its cover out; null for no buttons at all — a host can walk the steps
 *   itself off [CdRackScrollState.step], [CdRackScrollState.stepForward] and
 *   [CdRackScrollState.stepBack].
 * @param backLabel the back button's label.
 * @param openLabel the forward button's label on the shut case.
 * @param nextLabel the forward button's label from the cover on.
 * @param state also sets how far the rack slides when it first appears.
 */
@Composable
fun CdRackScroll(
    items: List<CdRackItem>,
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    contentColor: Color = Color(0xFF111114),
    captions: Boolean = true,
    inspectLabel: String? = "Inspect",
    backLabel: String = "Back",
    openLabel: String = "Open",
    nextLabel: String = "Next",
    state: CdRackScrollState = rememberCdRackScrollState(),
) {
    if (items.isEmpty()) {
        Box(modifier)
        return
    }

    val rack = remember(items) { RackScene(items) }
    val stars = rememberGraphicsLayer()
    var frameSize by remember { mutableStateOf(IntSize.Zero) }
    val graphics = LocalGraphicsContext.current
    val layerDensity = LocalDensity.current
    val direction = LocalLayoutDirection.current

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

    // Each case drawn once at the rack's turn, through the same renderer that
    // draws the one taken out — on Android, its shaders and all.
    LaunchedEffect(rack, frameSize) {
        if (frameSize.width <= 0 || frameSize.height <= 0) return@LaunchedEffect
        // The renderer is shared with the case taken out, so this waits for
        // it to be back in the rack, and takes taps away until it is done.
        while (state.chosen != NONE) withFrameNanos { }
        rack.ready = false
        rack.fit(frameSize.width.toFloat(), frameSize.height.toFloat())
        withContext(Dispatchers.Default) { rack.scene.prepare(rack.rackCamera) }
        val box = rack.spriteBox()
        rack.sprites.fill(null)
        rack.box = box
        for (index in items.indices) {
            withContext(Dispatchers.Default) { rack.scene.setArt(items[index].image) }
            rack.scene.setCover(items[index].cover)
            rack.scene.setManual(null)
            val layer = graphics.createGraphicsLayer()
            try {
                layer.record(layerDensity, direction, IntSize(box.width, box.height)) {
                    translate(-box.left.toFloat(), -box.top.toFloat()) {
                        rack.scene.draw(this, rack.rackCamera, rack.rackPose, shadow = 0f)
                    }
                }
                rack.sprites[index] = layer.toImageBitmap()
            } finally {
                graphics.releaseGraphicsLayer(layer)
            }
        }
        rack.ready = true
    }

    // The disc of the case tapped, baked off the main thread while it slides
    // to the slot; it is taken out once this is done.
    LaunchedEffect(rack, state.wanted) {
        val wanted = state.wanted
        if (wanted == NONE) return@LaunchedEffect
        withContext(Dispatchers.Default) { rack.scene.setArt(items[wrapCase(wanted, items.size)].image) }
        state.primed = wanted
    }

    // The manual of the case out, read in while it is out.
    LaunchedEffect(rack, state.inspecting) {
        rack.manual = null
        if (!state.inspecting) return@LaunchedEffect
        val load = items[wrapCase(state.chosen, items.size)].manual ?: return@LaunchedEffect
        rack.manual = withContext(Dispatchers.Default) { load() }
    }

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
                        // Out of the rack, the finger turns the case instead,
                        // and on its manual, the manual's leaves.
                        val out = state.inspecting && state.focus >= FOCUS_SECONDS
                        val leafing = out && state.case.canRead &&
                            rack.scene.bookSide(down.position.x, down.position.y) != 0
                        var swept = 0f
                        var lastMillis = down.uptimeMillis
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            lastMillis = change.uptimeMillis
                            val dx = change.position.x - last.x
                            val dy = change.position.y - last.y
                            last = change.position
                            travel += abs(dx) + abs(dy)
                            if (travel > TAP_SLOP) {
                                if (!started) {
                                    if (out && !leafing) state.case.onGrab()
                                    if (!out) state.onDragStart(change.uptimeMillis / 1000f)
                                    started = true
                                }
                                if (leafing) {
                                    swept += dx
                                } else if (out) {
                                    state.case.onGrabMove(dx / density, dy / density, change.uptimeMillis)
                                } else {
                                    // Only the part of the drag that runs along
                                    // the row counts, and up the row is back
                                    // through the rack: the cases follow the finger.
                                    val along = dx * ROW_X + dy * ROW_Y
                                    state.onDrag(-along / max(rack.pitchPx, 1f), change.uptimeMillis / 1000f)
                                }
                                change.consume()
                            }
                        }
                        if (started) {
                            if (leafing) {
                                // Left turns on, as a page is turned.
                                if (abs(swept) >= LEAF_SWIPE * density) state.case.turnPage(if (swept < 0f) 1 else -1)
                            } else if (out) {
                                state.case.onLetGo(lastMillis)
                            } else {
                                state.onDragEnd()
                            }
                        } else if (out) {
                            val side = if (state.case.canRead) rack.scene.bookSide(down.position.x, down.position.y) else 0
                            if (side != 0) {
                                state.case.tapManual(side)
                            } else if (rack.scene.hit(down.position.x, down.position.y)) {
                                state.case.toggle()
                            }
                        } else if (state.racked && rack.ready) {
                            val hit = rack.hit(down.position.x, down.position.y, state.position, items.size)
                            if (hit != null) state.inspect(hit)
                        }
                    }
                }
                .pointerInput(state) {
                    // A notch is a case, or a leaf of a manual out. A trackpad
                    // sends fractions of one, so they are added up until they
                    // come to a whole one.
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
                                if (state.inspecting) state.case.turnPage(by) else state.step(by)
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
            rack.fit(size.width, size.height)
            rack.draw(this, state, stars)
        }

        if (captions) Caption(items, state, contentColor)
        if (inspectLabel != null) {
            StepButtons(
                back = backLabel,
                state = state,
                forward = { step ->
                    when (step) {
                        0 -> openLabel
                        1 -> inspectLabel
                        else -> nextLabel
                    }
                },
                background = background,
                contentColor = contentColor,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun StepButtons(
    back: String,
    forward: (Int) -> String,
    state: CdRackScrollState,
    background: Color,
    contentColor: Color,
    modifier: Modifier,
) {
    val shown = state.inspecting
    // The case takes its time coming out; the buttons arrive once it has.
    val reveal by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(if (shown) 450 else 200, delayMillis = if (shown) (FOCUS_SECONDS * 1000f).toInt() else 0),
    )
    val step = state.step
    val more = step < state.lastStep
    Row(
        modifier.graphicsLayer {
            alpha = reveal
            translationY = (1f - reveal) * 10.dp.toPx()
        },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepButton(back, true, shown, background, contentColor) { state.stepBack() }
        StepButton(forward(step), more, shown, background, contentColor) { state.stepForward() }
    }
}

@Composable
private fun StepButton(
    label: String,
    enabled: Boolean,
    shown: Boolean,
    background: Color,
    contentColor: Color,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val alpha by animateFloatAsState(if (enabled) 1f else 0.3f, tween(200))
    val tap by rememberUpdatedState(onTap)
    BasicText(
        text = label.uppercase(),
        style = TextStyle(
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.4.sp,
        ),
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }
            // Only while it shows, or it would take taps meant for the case.
            .then(if (shown && enabled) Modifier.pointerInput(Unit) { detectTapGestures { tap() } } else Modifier)
            .background(background.copy(alpha = 0.9f), shape)
            .border(1.dp, contentColor.copy(alpha = 0.35f), shape)
            .padding(horizontal = 24.dp, vertical = 11.dp),
    )
}

// --- the rack ------------------------------------------------------------------

private class SpriteBox(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * The rack and the case taken out of it: a camera dollied far out for the row,
 * one for the case that comes back in, each case's drawing at the rack's turn.
 */
private class RackScene(private val items: List<CdRackItem>) {
    val scene = CaseScene()
    val rackCamera = Camera().also { it.zoom = RACK_ZOOM }
    private val liveCamera = Camera()

    /** Where every case in the rack stands, but for its place along the row. */
    val rackPose = CasePose()
    private val outPose = CasePose()
    private val livePose = CasePose()
    private val coverPose = CasePose()
    private val bookPose = BookPose()

    /** The manual of the case out, once read in. */
    var manual: CdManual? = null

    val sprites = arrayOfNulls<ImageBitmap>(items.size)
    var box: SpriteBox? = null
    @kotlin.concurrent.Volatile
    var ready = false

    /** How large the rack draws a case, against the case taken out. */
    private var rackScale = 1f

    /** One place along the row, on screen. */
    var pitchPx = 1f
        private set

    private var width = -1f
    private var height = -1f

    /** Places from the slot between which a case can be seen, either way. */
    private var reach = 1f

    private val frame = Pose()
    private val point = FloatArray(3)
    private val screen = FloatArray(8)
    private val origin = FloatArray(2)

    fun fit(w: Float, h: Float) {
        if (w == width && h == height) return
        width = w
        height = h
        rackCamera.fit(w, h)
        liveCamera.fit(w, h)
        // The face as wide on screen as the reference's tapes.
        val facePx = min(w * 0.4f, h * 0.36f)
        rackScale = facePx / (CASE_MM * FACE_ACROSS * rackCamera.scaleAt(0f))
        pitchPx = PITCH * facePx
        rackPose.pitch = RACK_PITCH
        rackPose.yaw = RACK_YAW
        rackPose.roll = 0f
        rackPose.scale = rackScale
        reach = hypot(w, h) / pitchPx + 2f
        rackCamera.project(0f, 0f, 0f, origin)
    }

    /** The screen box a case at the slot takes up, lifted, walls and hinge and all. */
    fun spriteBox(): SpriteBox {
        place(frame, rackPose)
        var x0 = Float.MAX_VALUE
        var y0 = Float.MAX_VALUE
        var x1 = -Float.MAX_VALUE
        var y1 = -Float.MAX_VALUE
        for (sx in floatArrayOf(-1f, 1f)) for (sy in floatArrayOf(-1f, 1f)) for (sz in floatArrayOf(-1f, 1f)) {
            frame.apply(sx * (HALF_MM + 2f), sy * (HALF_MM + 2f), sz * 7f, point)
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

    /** The rack, and the case out of it with [stars] over it. */
    fun draw(scope: DrawScope, state: CdRackScrollState, stars: GraphicsLayer) = with(scope) {
        val position = state.position
        val count = items.size
        val focus = state.focus
        val chosen = state.chosen
        val first = ceil(position - reach).toInt()
        val last = floor(position + reach).toInt()

        // How far the case taken out has come, and the camera with it.
        val come = inOutCubic(((focus - COME_FROM) / COME_SECONDS).clamped(0f, 1f))
        if (chosen != NONE) {
            liveCamera.zoom = exp(lerp(ln(RACK_ZOOM), 0f, come))
            state.case.pose(outPose, liveCamera)
            blend(come)
            val item = items[wrapCase(chosen, count)]
            scene.setArt(item.image)
            scene.setCover(item.cover)
            val covered = item.cover != null
            if (state.case.hasCover != covered) state.case.hasCover = covered
            val manual = manual
            scene.setManual(manual)
            state.case.manualSpreads = if (covered && manual != null) manual.spreads.size else 0
        }
        val loose = if (chosen != NONE && state.case.coverPose(coverPose, livePose, liveCamera)) coverPose else null
        val book = if (loose != null && state.case.bookPose(bookPose)) bookPose else null

        // Back of the row first: each case covers the one behind it.
        for (i in last downTo first) {
            if (i == chosen) {
                scene.draw(this, liveCamera, livePose, shadow = come, cover = loose, book = book)
                continue
            }
            val sprite = sprites[wrapCase(i, count)] ?: continue
            val b = box ?: continue
            var w = i - position
            var alpha = 1f
            if (chosen != NONE) {
                // Each goes only as far as takes it off the frame, so the
                // near ones drift rather than fly, and fades as it goes.
                val apart = abs(i - chosen)
                val delay = EXIT_STAGGER * min(apart, EXIT_STAGGER_PLACES)
                val along = ((focus - delay) / EXIT_SECONDS).clamped(0f, 1f)
                val off = max(reach / 2f + 2f - apart, 0f)
                w += sign(i - chosen.toFloat()) * off * inOutCubic(along)
                alpha = 1f - smoothstep(0.1f, EXIT_FADE, along)
                if (alpha <= 0f) continue
            }
            centre(w, point)
            if (!rackCamera.project(point[0], point[1], point[2], screen)) continue
            val x = b.left + screen[0] - origin[0]
            val y = b.top + screen[1] - origin[1]
            if (x > size.width || y > size.height || x + b.width < 0f || y + b.height < 0f) continue
            // Translated rather than offset by whole pixels, so a slow slide
            // does not step.
            translate(x, y) { drawImage(sprite, alpha = alpha) }
        }
        // The rack's cases are drawn without them; the one out comes into
        // its stars as it comes out.
        if (chosen != NONE) scene.stars(this, stars, strength = come)
    }

    /**
     * The case on its way out: from its place in the rack to where the
     * case's own state holds it, turn, place and size by [k].
     */
    private fun blend(k: Float) {
        livePose.pitch = lerp(RACK_PITCH, outPose.pitch, k)
        livePose.yaw = lerp(RACK_YAW, outPose.yaw, k)
        livePose.roll = lerp(0f, outPose.roll, k)
        livePose.x = outPose.x * k
        livePose.y = outPose.y * k
        livePose.z = 0f
        livePose.scale = lerp(rackScale, outPose.scale, k)
        livePose.lid = outPose.lid
        livePose.spread = outPose.spread
        livePose.spin = outPose.spin * k
    }

    /**
     * The case under [x], [y], unwrapped, or null. The front of the row covers
     * the back, so cases are tried front first.
     */
    fun hit(x: Float, y: Float, position: Float, count: Int): Int? {
        place(frame, rackPose)
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

/** [frame] turned and sized as [pose] holds the case, standing at the origin. */
private fun place(frame: Pose, pose: CasePose) {
    eulerXyz(pose.pitch, pose.yaw, pose.roll, frame.r)
    frame.s = pose.scale
    frame.t[0] = 0f
    frame.t[1] = 0f
    frame.t[2] = 0f
}

/** How far up a case [w] places from the slot stands, from 0 to 1. */
private fun lift(w: Float): Float = 1f - smoothstep(LIFT_FLAT, LIFT_GONE, abs(w))

// --- caption -----------------------------------------------------------------

@Composable
private fun BoxScope.Caption(
    items: List<CdRackItem>,
    state: CdRackScrollState,
    contentColor: Color,
) {
    val item = items[wrapCase(state.slotted, items.size)]
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
