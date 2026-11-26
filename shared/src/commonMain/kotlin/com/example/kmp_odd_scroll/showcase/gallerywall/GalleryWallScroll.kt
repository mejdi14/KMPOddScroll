package com.example.kmp_odd_scroll.showcase.gallerywall

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.example.kmp_odd_scroll.showcase.gallerywall.inspection.AnalysisCache
import com.example.kmp_odd_scroll.showcase.gallerywall.inspection.LabGrid
import com.example.kmp_odd_scroll.showcase.gallerywall.inspection.LocalInspectionLens
import com.example.kmp_odd_scroll.showcase.gallerywall.inspection.PaintingAnalysis
import com.example.kmp_odd_scroll.showcase.gallerywall.inspection.PaintingInspection
import com.example.kmp_odd_scroll.showcase.gallerywall.inspection.inspectionHover
import com.example.kmp_odd_scroll.showcase.gallerywall.inspection.rememberInspectionLens
import com.example.kmp_odd_scroll.showcase.internal.clamped
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
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
    /** Its own frame, in place of the one [GalleryWallScroll] hangs every painting in; null for that one. */
    val frame: GalleryWallFrame? = null,
    /**
     * The back of the canvas, shown when it is turned over out of its frame,
     * stretched to the painting's size; null for the painting again.
     */
    val back: ImageBitmap? = null,
    /**
     * How near each part of the scene is, for stepping inside the painting:
     * white near, black far, any size — what a single-image depth model writes
     * out. Null guesses it from the painting itself.
     */
    val depth: ImageBitmap? = null,
    /**
     * The painting rebuilt as a place to walk into, in place of [depth]:
     * stepping inside walks a route into it, where a drag turns within its
     * bounds and a scroll walks on or back. Null steps inside on [depth].
     */
    val scene: GalleryWallScene? = null,
    /** An optional subject mask aligned to the painting. Zero is background and each subject has its own grey value. */
    val inspectionMask: ImageBitmap? = null,
    /** A bundled subject mask decoded at its original size when opening the study. [inspectionMask] takes precedence. */
    val inspectionMaskResource: DrawableResource? = null,
)

/** What is carved into a [GalleryWallFrame]'s moulding. */
enum class GalleryWallOrnament {
    /**
     * Neoclassical: a rope twist round the outer edge, egg and dart along the
     * face, bead and reel at the sight edge and a rosette over each mitre.
     */
    Classical,

    /**
     * Baroque: acanthus running along a swelling torus towards the middle of
     * each side, pearls at the sight edge, and over each corner and each
     * middle a scallop shell between C-scrolls.
     */
    Shell,

    /**
     * Baroque: acanthus running clockwise all the way round, a spray of leaves
     * round a flower over each corner, and bead and reel at the sight edge.
     */
    Acanthus,

    /**
     * Baroque: a gadrooned torus, its lobes leaning in towards the middle of
     * each side, C-scrolls curling into each corner round a small shell, a fan
     * of leaves at each middle, and small pearls at the sight edge.
     */
    Gadroon,
}

/**
 * A wooden or [gilt] frame round every painting, and the painting made a thing that
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
     * How deep the hand carving is cut into the moulding, 0 to 1, every piece
     * a little off true the way a gouge leaves it. 0 for a plain moulding.
     */
    val carving: Float = 0f,
    /**
     * What the carving is. The baroque ones swell the moulding into a big
     * torus over a deep cove, and want a wider [width].
     */
    val ornament: GalleryWallOrnament = GalleryWallOrnament.Classical,
    /**
     * Gold leaf over the moulding instead of bare wood, [color] the gold:
     * burnished where it stands proud, rubbed through to the red bole on the
     * high outer edge, dull down the cove. Wants [grain] 0.
     */
    val gilt: Boolean = false,
    /**
     * A pane over the painting, flush with the face. It throws back the wall a
     * little square on and more and more as the wall turns away, and the lit
     * doorway behind you shows in it as a soft glow that stays put while the
     * paintings slide under it. Where the platform has the shader for it, the
     * pane is liquid glass whose edges, hidden under the moulding, bend
     * the painting under them, and show as it slides away once the frame
     * comes off.
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

// Bound the set of 18 wall textures on high-DPI displays. Close-ups use the original image.
private const val MAX_WALL_RESOLUTION = 640
private const val WALL_RESOLUTION_STEP = 64

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

/*
 * Inspecting. The frame cracks along its mitres, its four sticks parting a
 * little, then each flies off the screen past its own edge, gathering speed;
 * the label and the shadow fade as they part. With the frame gone the glass
 * slides straight up and out of the top.
 */

/** How far the sticks part, in card heights, over this first stretch of their time. */
private const val CRACK = 0.04f
private const val CRACK_TO = 0.3f

/** Past the edge of the screen, in card heights, before a stick or the glass is out of it. */
private const val CLEAR = 0.05f

private val LIFT_EASE = CubicBezierEasing(0.45f, 0f, 0.8f, 0.35f)

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
 * the hang has stood still for two seconds, [lookLabel] offers to walk up to
 * the painting in the middle. Up close, [inspectLabel] takes the painting out
 * of its frame. The four sticks fly past the edges of the screen and the glass
 * slides up out of sight. Both go back on as it is stepped away from. With them gone, [flipLabel]
 * turns the bare painting over like a sheet of paper — a crease sweeping in
 * from the lower left corner, ripples settling behind it — to its
 * [GalleryWallItem.back], and again turns it back. With the bare painting face up,
 * [stepInsideLabel] goes into the painting. It takes on depth, the room fades
 * to dark round it and the eye goes in, where a drag looks round and a scroll
 * walks in or back, until [exitLabel] steps back out. The room opens with a
 * walk in from the doorway, unless the state was made without one.
 *
 * @param items paintings, in order; the hang repeats, so it has no end.
 * @param background the wall.
 * @param contentColor the labels.
 * @param frame the frame round each painting that has none of its own, or
 *   null to hang those bare.
 * @param lookLabel the button that walks up to a painting, or null for none —
 *   a host can show its own off [GalleryWallScrollState.canLook].
 * @param exitLabel the button opposite Inspect that steps back from the painting. Before
 *   inspection, or when the label is null, tapping the painting steps back.
 * @param inspectLabel the button, bottom right up close, that takes the frame
 *   and the glass off, or null for none — a host can show its own off
 *   [GalleryWallScrollState.canInspect].
 * @param flipLabel the button, bottom left once the frame and glass are off,
 *   that turns the painting over and back, or null for none — a host can show its own
 *   off [GalleryWallScrollState.canFlip].
 * @param stepInsideLabel the button, bottom right, that enters the inspected
 *   painting while it is face up, or null for none. A host can show its own off
 *   [GalleryWallScrollState.canStepInside].
 * @param state also lets a host's own controls move it.
 * @param detailsReady whether the selected painting's inspection assets are available.
 * @param onWallReady called when the gallery-resolution bitmaps are ready to draw.
 */
@Composable
fun GalleryWallScroll(
    items: List<GalleryWallItem>,
    modifier: Modifier = Modifier,
    background: Color = Color(0xFFFFFFFF),
    contentColor: Color = Color(0xFF1A1A1A),
    frame: GalleryWallFrame? = null,
    lookLabel: String? = "Take a look",
    exitLabel: String? = "Exit",
    inspectLabel: String? = "Inspect",
    flipLabel: String? = "Flip",
    stepInsideLabel: String? = "Step inside",
    state: GalleryWallScrollState = rememberGalleryWallScrollState(),
    detailsReady: Boolean = true,
    onWallReady: () -> Unit = {},
) {
    if (items.isEmpty()) {
        Box(modifier)
        return
    }

    val measurer = rememberTextMeasurer()
    // Kept for as long as what the wall draws is the same: a painting's depth
    // or its scene arriving later leaves its frame as it was, and baking every
    // frame again is the slowest thing the wall does — seconds, in a browser.
    val look = remember(items) {
        items.map { WallLook(it.image, it.back, it.frame, it.scale, it.title, it.artist, it.date, it.medium) }
    }
    val hang = remember(look, frame) { Hang(items, frame) }
    val art = remember(look, contentColor, frame, measurer) { WallArt(items, contentColor, frame, measurer) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val pixels = focalLength(viewport.width.toFloat(), viewport.height.toFloat()) / DEPTH * BITMAP_SCALE
    val wallResolution = (ceil(pixels / WALL_RESOLUTION_STEP).toInt() * WALL_RESOLUTION_STEP)
        .coerceIn(0, MAX_WALL_RESOLUTION)
    val notifyReady by rememberUpdatedState(onWallReady)
    LaunchedEffect(art, wallResolution) {
        if (wallResolution > 0) {
            art.prepare(wallResolution)
            notifyReady()
        }
    }
    val room = remember { Room() }
    val inside = remember { StepInside() }
    val enter = remember { EnterPainting() }
    val canvas = remember { FloatArray(4) }
    // Where the bare canvas of the painting walked up to lies on screen, so its study opens on it there.
    val hung = remember { mutableStateOf(Rect.Zero) }
    SideEffect { state.hang = hang }
    val inspectionCache = remember { AnalysisCache() }
    val subjectIndex = hang.wrap(state.subject)
    val painting = items[subjectIndex]
    val lens = rememberInspectionLens()
    val gridVisible by remember(state) { derivedStateOf { state.inspected || state.lift > 0f } }
    val hasInspectionEffect = remember(painting.image) { mutableStateOf(false) }
    // Measuring the full image can occupy the web render thread.
    val analysis = if (state.looking && state.arrived && detailsReady) key(
        painting.image, painting.depth, painting.inspectionMask, painting.inspectionMaskResource,
    ) {
        produceState<PaintingAnalysis?>(null) { value = inspectionCache.get(painting) }.value
    } else null

    val wallReady = art.cards.isNotEmpty()
    val prepareClose = wallReady && (if (state.looking) state.arrived else state.resting)
    LaunchedEffect(art, subjectIndex, viewport, prepareClose) {
        if (prepareClose && viewport.width > 0 && viewport.height > 0) {
            // Let a new gesture or another resize cancel the bake before it starts.
            delay(150)
            val scale = hang.closeScale(subjectIndex, viewport.width.toFloat(), viewport.height.toFloat())
            art.prepareClose(subjectIndex, scale * 1.05f)
        }
    }
    LaunchedEffect(state, wallReady) {
        if (!wallReady) return@LaunchedEffect
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                val deltaMs = if (previous == 0L) 16.67f else (now - previous) / 1_000_000f
                previous = now
                state.advance(deltaMs.clamped(1f, 50f))
            }
        }
    }

    Box(modifier.background(background).fillMaxSize().onPlaced { lens.viewport = it }
        .onSizeChanged { viewport = it }
        .inspectionHover(lens, state.showingInspection && !state.inside)) {
        if (gridVisible) {
            LabGrid(hung::value, state::lift, state::entryWave, lens, Modifier.matchParentSize())
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state, exitLabel != null) {
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
                                // The back wall follows the finger, pixel for
                                // pixel; inside a painting, the scene does.
                                if (state.inside) state.lookAround(dx, dy, if (state.scene != null) enter.focal else room.focal)
                                else if (!state.looking) state.nudge(-dx / room.backPx)
                                change.consume()
                            }
                        }
                        state.release(dragging)
                        if (state.looking) {
                            if (!dragging && (exitLabel == null || state.canInspect)) {
                                if (state.inspected && !state.inside) state.uninspect() else state.leave()
                            }
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
                            if (state.inside) state.walkInside(by) else state.nudge(by * WHEEL_STEP)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            if (!wallReady || size.isEmpty()) return@Canvas
            state.tick // read, so the wall repaints as the hang moves
            room.fit(size.width, size.height)
            val subject = state.subject
            room.stroll(state.entry, state.walk, state.apart, state.lift, hang, subject)
            // Draw the prepared texture at rest too, so its first upload precedes the walk.
            val close = if (state.resting || state.looking || state.walk > 0f) art.close(hang.wrap(subject)) else null
            val item = items[hang.wrap(subject)]
            if (state.arrived) {
                val card = close ?: art.cards[hang.wrap(subject)]
                if (canvasOnScreen(card, hang.start(subject) - state.position, room, item, canvas)) {
                    val at = Rect(canvas[0], canvas[1], canvas[2], canvas[3])
                    if (at != hung.value) hung.value = at
                }
            }
            // Its depth is worked out as soon as it can be stepped into, so the step does not start on a dropped frame.
            val scene = item.scene
            state.scene = scene
            if (state.canStepInside || state.stepIn > 0f) {
                if (scene != null) enter.prepare(scene) else inside.prepare(item.image, item.depth)
            }
            val veil = veil(state.stepIn)
            rotate(room.roll, Offset(room.cx, room.cy)) {
                if (veil < 1f) {
                    // Only a tool or a flip takes over drawing the canvas from the wall.
                    val replaced = state.studyVisible && (hasInspectionEffect.value || state.fold > 0f || state.over)
                    drawWall(art, hang, room, state.position, close,
                        subject, background, replaced)
                }
                if (state.stepIn > 0f) {
                    drawRect(INSIDE_DARK, alpha = veil)
                    val card = close ?: art.cards[hang.wrap(subject)]
                    if (canvasOnScreen(card, hang.start(subject) - state.position, room, item, canvas) && scene != null) {
                        enter.draw(
                            this, scene, item.image, canvas[0], canvas[1], canvas[2], canvas[3],
                            state.stepIn, state.route, state.yaw, state.pitch, state.insideTime,
                        )
                    } else if (canvasOnScreen(card, hang.start(subject) - state.position, room, item, canvas)) {
                        inside.draw(
                            this, canvas[0], canvas[1], canvas[2], canvas[3], room.cx, room.cy, room.focal,
                            state.stepIn, state.yaw, state.pitch, state.wander, state.insideTime,
                        )
                    }
                }
            }
        }

        if (lookLabel != null) {
            val ready = state.canLook && art.close(subjectIndex) != null
            HudButton(lookLabel, ready, background, contentColor, Modifier.align(Alignment.BottomCenter).padding(bottom = HUD_BOTTOM)) {
                state.look()
            }
        }
        if (exitLabel != null) {
            val showExit = state.canInspect || (state.arrived && state.inspected && !state.showingInspection)
            HudButton(exitLabel, showExit, background, contentColor,
                Modifier.align(Alignment.BottomStart).padding(start = HUD_SIDE, bottom = HUD_BOTTOM)) {
                if (state.inspected && !state.inside) state.uninspect() else state.leave()
            }
        }
        if (inspectLabel != null) {
            HudButton(inspectLabel, state.canInspect && detailsReady, background, contentColor,
                Modifier.align(Alignment.BottomEnd).padding(end = HUD_SIDE, bottom = HUD_BOTTOM), state::inspect)
        }
        if (state.showingInspection) CompositionLocalProvider(LocalInspectionLens provides lens) {
            key(painting.image) {
                PaintingInspection(painting, analysis, hung::value, exitLabel,
                    flipLabel, stepInsideLabel, state, onEffect = { hasInspectionEffect.value = it }) { bounds, progress, over ->
                    val card = art.close(subjectIndex) ?: art.cards.getOrNull(subjectIndex) ?: return@PaintingInspection
                    drawFold(card, bounds, progress, over)
                }
            }
        }
    }
}

private val HUD_SIDE = 28.dp
private val HUD_BOTTOM = 48.dp

@Composable
private fun HudButton(
    label: String,
    shown: Boolean,
    background: Color,
    contentColor: Color,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val tap by rememberUpdatedState(onTap)
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
            .then(
                if (shown) Modifier.pointerInput(Unit) { detectTapGestures { tap() } }
                else Modifier.clearAndSetSemantics {}
            )
            .background(background.copy(alpha = 0.9f), shape)
            .border(1.dp, contentColor.copy(alpha = 0.5f), shape)
            .padding(horizontal = 26.dp, vertical = 11.dp),
    )
}

// --- the hang ----------------------------------------------------------------

/** Where each painting hangs along the wall, in card heights, with the hang at rest; frames included. */
internal class Hang(items: List<GalleryWallItem>, frame: GalleryWallFrame? = null) {
    val count = items.size
    private val borders = FloatArray(count) { i -> 2f * ((items[i].frame ?: frame)?.width ?: 0f) }
    val heights = FloatArray(count) { i -> items[i].scale + borders[i] }
    val widths = FloatArray(count) { i ->
        val image = items[i].image
        items[i].scale * image.width / max(image.height, 1).toFloat() + borders[i]
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

    /** Painting [index] hangs in a frame, so it comes out of one bare when inspected. */
    fun framed(index: Int): Boolean = borders[wrap(index)] > 0f

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

private fun focalLength(width: Float, height: Float) = min(FOCAL * width, FOCAL_PER_HEIGHT * height)

/** Pixels per card height where the walk ends, fitting both the framed width and height. */
private fun Hang.closeScale(index: Int, width: Float, height: Float): Float = max(
    focalLength(width, height) / DEPTH,
    min(FILL_WIDTH * width / widths[index], FILL_HEIGHT * height / heights[index]),
)

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

    /**
     * How much of the doorway shows in the glass: all of it from across the
     * room, none once walked up, where the visitor stands in its way.
     */
    var glare = 1f

    /**
     * The painting walked up to, being inspected: its frame off at all, how far
     * the sticks have cracked apart and then flown, how much of the label and
     * shadow is left, and how far the glass has slid, each eased, 0 to 1.
     */
    var unframed = false
    var crack = 0f
    var fly = 0f
    var label = 1f
    var lift = 0f

    /** How far the paintings either side of it have slid away, in card heights. */
    var part = 0f

    /** How far it has come off the wall's curve, 0 to 1. */
    var flat = 0f

    private val point = FloatArray(2)

    fun fit(w: Float, h: Float) {
        if (w == width && h == height) return
        width = w
        height = h
        focal = focalLength(w, h)
        cx = w / 2f
        cy = h * HORIZON
        backPx = focal / DEPTH
        // Where the side walls leave the frame, with a margin past it.
        val leaves = focal * HALF_WIDTH / max(w / 2f, 1f) * 0.9f
        sideFrom = max(leaves, NEAR)
        eyeZ = Float.NaN
    }

    /**
     * Puts the eye [entry] of the way into the room and [walk] of the way up to
     * painting [index], whose frame is [apart] of the way off and its glass
     * [glass] of the way away.
     */
    fun stroll(entry: Float, walk: Float, apart: Float, glass: Float, hang: Hang, index: Int) {
        val stand = focal / hang.closeScale(hang.wrap(index), width, height)
        val eased = WALK_EASE.transform(walk)
        glare = 1f - eased
        unframed = apart > 0f
        val cracked = (apart / CRACK_TO).coerceIn(0f, 1f)
        crack = cracked * cracked * (3f - 2f * cracked)
        label = 1f - crack
        val flown = ((apart - CRACK_TO) / (1f - CRACK_TO)).coerceIn(0f, 1f)
        fly = flown * flown
        lift = LIFT_EASE.transform(glass)
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
 * the one walked up to, which is taken apart and from which the rest slide
 * away; [wall] is what the glass throws back.
 */
private fun DrawScope.drawWall(
    art: WallArt,
    hang: Hang,
    room: Room,
    position: Float,
    close: CardArt?,
    closeIndex: Int,
    wall: Color,
    subjectReplaced: Boolean,
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
        if (!(subject && subjectReplaced) && left <= reach && left + card.wallWidth >= -reach) {
            if (subject) {
                flat = room.flat
                // Only the painting walked up to is taken apart.
                if (room.unframed && card.bare != null) drawApart(card, left, room, wall) else drawCard(card, left, room, wall)
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
 */
private fun DrawScope.drawCard(card: CardArt, left: Float, room: Room, wall: Color) {
    val px = card.pxPerUnit
    val sunk = card.depth - card.recess
    card.wall?.let { drawSheet(it, px, card.top, left, 0f, room) }
    val glass = if (card.glass) pane.aim(card, left, room, wall, card.back, 0f, 0f, 0f) else null
    card.back?.let { drawSheet(it, px, card.top, left, sunk, room, glaze = glass) }
    if (card.edges != null) drawEdges(card, card.edges, left, room)
    drawSheet(card.face, px, card.top, left, card.depth, room)
}

/**
 * The painting walked up to, being inspected, square on: the label and shadow
 * fading, the bare canvas, its glass sliding up off it, and the four sticks of
 * the frame on their way out past the four edges of the screen — each pushed
 * just far enough, from wherever the eye stands, to clear it.
 */
private fun DrawScope.drawApart(card: CardArt, left: Float, room: Room, wall: Color) {
    val px = card.pxPerUnit
    val bare = card.bare ?: return
    val sunk = card.depth - card.recess
    val f = room.focal
    val w = card.face.width / px
    val h = card.face.height / px
    val border = card.opening / px
    // Unframed cards carry the label on the face itself.
    if (room.label > 0f) drawSheet(card.wall ?: card.face, px, card.top, left, 0f, room, room.label)

    // Straight up, until its bottom edge is over the top of the screen.
    val glass = if (card.glass) {
        val z = DEPTH - sunk - room.eyeZ
        val rise = card.top + h - room.eyeY + room.cy * z / f + CLEAR
        pane.aim(card, left, room, wall, bare, 0f, -rise * room.lift, room.crack)
    } else {
        null
    }
    drawSheet(bare, px, card.top, left, sunk, room, glaze = glass)

    val sticks = card.sticks ?: return
    val z = DEPTH - card.depth - room.eyeZ
    // Each stick's inner edge, and how far it is to the side of the screen it leaves by.
    val out = floatArrayOf(
        card.top + border - room.eyeY + room.cy * z / f,
        room.eyeX + (size.width - room.cx) * z / f - (left + w - border),
        room.eyeY + (size.height - room.cy) * z / f - (card.top + h - border),
        left + border - room.eyeX + room.cx * z / f,
    )
    for (side in 0..3) {
        val by = CRACK * room.crack + max(out[side] + CLEAR - CRACK, 0f) * room.fly
        val stick = sticks[side]
        val x = left + stick.x / px + when (side) { 1 -> by; 3 -> -by; else -> 0f }
        val y = card.top + stick.y / px + when (side) { 0 -> -by; 2 -> by; else -> 0f }
        drawSheet(stick.image, px, y, x, card.depth, room)
    }
}

private val paper = PaperFold()

/**
 * The canvas turned [progress] of the way over from its current side.
 * The fold starts and ends on the inspection stage's [frame].
 */
private fun DrawScope.drawFold(card: CardArt, frame: Rect, progress: Float, over: Boolean) {
    val sides = card.sides ?: return
    val w = card.face.width
    val h = card.face.height
    val o = card.opening.toFloat()
    // The painting on the left of the two, its back on the right.
    val painting = o
    val back = w + o
    paper.draw(
        scope = this,
        image = sides,
        frontLeft = if (over) back else painting,
        backLeft = if (over) painting else back,
        srcTop = o,
        srcWidth = w - 2f * o,
        srcHeight = h - 2f * o,
        left = frame.left,
        top = frame.top,
        right = frame.right,
        bottom = frame.bottom,
        progress = progress,
    )
}

/** What the room fades to round a painting stepped into. */
private val INSIDE_DARK = Color(0xFF0A0A0B)

/**
 * Where the canvas of [item] — out of its frame, sunk where it sits in it —
 * lies on screen with its left edge [left] along the wall, square on: left,
 * top, right and bottom into [out]. False if it is behind the eye.
 */
private fun canvasOnScreen(card: CardArt, left: Float, room: Room, item: GalleryWallItem, out: FloatArray): Boolean {
    val z = DEPTH - (card.depth - card.recess) - room.eyeZ
    if (z <= 0.05f) return false
    val border = card.opening / card.pxPerUnit
    val x0 = left + border - room.eyeX
    val y0 = card.top + border - room.eyeY
    val h = item.scale
    val w = h * item.image.width / max(item.image.height, 1).toFloat()
    val f = room.focal
    out[0] = room.cx + f * x0 / z
    out[1] = room.cy + f * y0 / z
    out[2] = room.cx + f * (x0 + w) / z
    out[3] = room.cy + f * (y0 + h) / z
    return true
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
 * The band round the liquid glass's edge that bends the painting under it, in
 * card heights; it bends it in by as much again. The pane runs this far under
 * the moulding, so its edge only shows with the frame off.
 */
private const val LIQUID_BEVEL = 0.05f

/** At most this much of the moulding's width, so the band stays under it. */
private const val LIQUID_UNDER = 0.85f

/**
 * The band once out of the frame, in card heights. Nothing hides it then, so
 * it widens as the frame fades, in over the painting's edge, and bends more.
 */
private const val LIQUID_WIDE = 0.14f

/**
 * How far from each corner the bend starts turning round it, against the band.
 * The pane itself is square cornered; this only keeps the bend from creasing
 * along the diagonals — 1.5 of a 22dp radius to a 32dp band, as Apple's own
 * glass has it.
 */
private const val LIQUID_TURN = 1.5f * 22f / 32f

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

    /**
     * The liquid pane: its middle, half-sizes, bending band and how it turns
     * the corners, how much its edges show, and its rim.
     */
    var cx = 0f
    var cy = 0f
    var hw = 0f
    var hh = 0f
    var turn = 0f
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
        turn = min(this.bevel * LIQUID_TURN, min(hw, hh))
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
            turn = glaze.turn,
            refraction = glaze.bevel,
            edges = glaze.edges,
            rim = glaze.rim,
        )
        drawRect(brush, topLeft = Offset(l, t), size = Size(r - l, b - t))
        return
    }

    // The opening, wherever the pane has been slid to.
    val dx = glaze.cx - glaze.width / 2f
    val dy = glaze.cy - glaze.height / 2f
    val l = max(glaze.opening + dx, from.toFloat())
    val r = min(glaze.width - glaze.opening + dx, to.toFloat())
    val t = max(glaze.opening + dy, top.toFloat())
    val b = min(glaze.height - glaze.opening + dy, bottom.toFloat())
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
    // Laid flat, it is off the walls and can go anywhere across the view.
    if (flat >= 1f) {
        drawPiece(bitmap, pxPerUnit, top, left, inset, left + x0 / pxPerUnit, left + x1 / pxPerUnit,
            room, alpha, glaze, x0, x1, y0, y1)
        return
    }
    val from = max(left + x0 / pxPerUnit, -room.reach)
    val to = min(left + x1 / pxPerUnit, room.reach)
    if (to <= from) return
    var a = from
    while (a < to) {
        var b = to
        for (k in bends) if (k > a + 1e-5f && k < b) b = k
        val curved = abs((a + b) / 2f) in FLAT..(FLAT + ARC)
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
private fun DrawScope.drawEdges(card: CardArt, edges: ImageBitmap, left: Float, room: Room) {
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
    pair: (() -> ImageBitmap)? = null,
    split: (() -> List<Stick>)? = null,
) {
    /** How far along the wall it runs, label and shadow included. */
    val wallWidth: Float get() = max(face.width, wall?.width ?: 0) / pxPerUnit

    /** The canvas out of its frame, the size of the face; drawn the first time it is asked for. */
    val bare: ImageBitmap? by lazy { unframe?.invoke() }

    /**
     * The front and back side by side, the face's width apart, so one mesh
     * can turn the canvas over showing both; drawn the first time asked for.
     */
    val sides: ImageBitmap? by lazy { pair?.invoke() }

    /** The face cut along its mitres, top, right, bottom and left; cut the first time asked for. */
    val sticks: List<Stick>? by lazy { split?.invoke() }
}

/** One stick of a frame, cut out of the face at [x], [y] in its pixels. */
private class Stick(val image: ImageBitmap, val x: Int, val y: Int)

/** Every painting drawn once, rebuilt only when they change size on screen. */
/** All of a painting that [WallArt] and [Hang] draw from: they are kept while it is unchanged. */
private data class WallLook(
    val image: ImageBitmap,
    val back: ImageBitmap?,
    val frame: GalleryWallFrame?,
    val scale: Float,
    val title: String,
    val artist: String,
    val date: String,
    val medium: String,
)

private class WallArt(
    private val items: List<GalleryWallItem>,
    private val ink: Color,
    private val frame: GalleryWallFrame?,
    private val measurer: TextMeasurer,
) {
    var cards by mutableStateOf(emptyList<CardArt>())
        private set

    private var builtFor = -1

    private class CloseUp(val index: Int, val resolution: Int, val card: CardArt)
    private var closeUp by mutableStateOf<CloseUp?>(null)

    suspend fun prepare(px: Int) {
        if (px == builtFor) return
        // Publish the complete hang together. Resizing keeps the last usable set on screen.
        cards = items.mapIndexed { index, item ->
            withFrameNanos { }
            build(item, px.toFloat(), index)
        }
        builtFor = px
    }

    fun close(index: Int): CardArt? = closeUp?.takeIf { it.index == index }?.card

    /**
     * Painting [index] again at [pxPerUnit], for walking up to — no finer than
     * its own image, which would only blur it twice.
     */
    fun prepareClose(index: Int, pxPerUnit: Float) {
        val item = items[index]
        val px = min(pxPerUnit, item.image.height / item.scale).roundToInt().coerceAtLeast(8)
        val cached = closeUp
        if (cached?.index == index && cached.resolution == px) return
        val wallCard = cards[index]
        val card = if (wallCard.pxPerUnit >= px) wallCard else build(item, px.toFloat(), index)
        closeUp = CloseUp(index, px, card)
    }

    /** [seed] picks the painting's wood, the same at every size. */
    private fun build(item: GalleryWallItem, unit: Float, seed: Int): CardArt {
        val frame = item.frame ?: this.frame
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
            bitmap.finishWallTexture()
            return bitmap
        }
        fun DrawScope.canvas(image: ImageBitmap = item.image, x: Int = border) = drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(x, border),
            dstSize = IntSize(paintW, paintH),
            filterQuality = FilterQuality.High,
        )
        val reverse = item.back ?: item.image
        fun DrawScope.label() = lines.forEachIndexed { n, layout ->
            drawText(layout, topLeft = Offset(0f, labelTop + n * LABEL_LINE * unit))
        }

        if (frame == null) return CardArt(paint(bw, bh) { canvas(); label() }, pxPerUnit, top,
            unframe = { paint(paintW, paintH) { canvas() } })
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
            unframe = { paint(outerW, outerH) { canvas() } },
            pair = { paint(2 * outerW, outerH) { canvas(); canvas(reverse, outerW + border) } },
            split = { cut(face, border) },
        )
    }
}

// --- the frame ---------------------------------------------------------------

/**
 * The frame [face] cut along its mitres into its four sticks, [border] deep —
 * top, right, bottom and left, each in a bitmap just its own size.
 */
private fun cut(face: ImageBitmap, border: Int): List<Stick> {
    val w = face.width
    val h = face.height
    val b = border.toFloat()
    // Each stick's place in the face and its outline there, outer edge first.
    val places = listOf(
        IntOffset(0, 0) to floatArrayOf(0f, 0f, w.toFloat(), 0f, w - b, b, b, b),
        IntOffset(w - border, 0) to floatArrayOf(w.toFloat(), 0f, w.toFloat(), h.toFloat(), w - b, h - b, w - b, b),
        IntOffset(0, h - border) to floatArrayOf(w.toFloat(), h.toFloat(), 0f, h.toFloat(), b, h - b, w - b, h - b),
        IntOffset(0, 0) to floatArrayOf(0f, h.toFloat(), 0f, 0f, b, b, b, h - b),
    )
    return places.mapIndexed { side, (at, q) ->
        val size = if (side % 2 == 0) IntSize(w, border) else IntSize(border, h)
        val bitmap = ImageBitmap(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1))
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, BitmapCanvas(bitmap), Size(size.width.toFloat(), size.height.toFloat())) {
            translate(-at.x.toFloat(), -at.y.toFloat()) {
                val mitre = Path().apply {
                    moveTo(q[0], q[1])
                    lineTo(q[2], q[3])
                    lineTo(q[4], q[5])
                    lineTo(q[6], q[7])
                    close()
                }
                clipPath(mitre) { drawImage(face) }
            }
        }
        bitmap.finishWallTexture()
        Stick(bitmap, at.x, at.y)
    }
}

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

private fun light(by: Float, gilt: Boolean = false): Color = when {
    gilt && by >= 0f -> GILT_SHINE.copy(alpha = by)
    gilt -> GILT_SHADOW.copy(alpha = -by)
    by >= 0f -> SHEEN.copy(alpha = by)
    else -> Color.Black.copy(alpha = -by)
}

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
    val stops = SIDE_PROFILE.map { (at, by) -> at to light(by, frame.gilt) }.toTypedArray()
    for (side in 0..1) {
        val x = side * d
        // The grain runs up the stick, so draw it lying down and stand it up.
        clipRect(x, 0f, x + d, h) {
            withTransform({ translate(x + d, 0f); rotate(90f, Offset.Zero) }) {
                val random = Random(seed * 8 + 4 + side)
                if (frame.gilt) drawGilt(frame.color, h, d, unit, random) else drawWood(frame.color, frame.grain, h, d, unit, SIDE_GRAIN, random)
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
    val baroque = frame.ornament != GalleryWallOrnament.Classical
    val finish = if (frame.gilt) GOLD_FINISH else WOOD_FINISH
    val stops = (if (baroque) BAROQUE_PROFILE else PROFILE).map { (at, by) -> at to light(by, frame.gilt) }.toTypedArray()
    // Fades out where the moulding is too few pixels across to hold a cut.
    val carving = frame.carving.coerceIn(0f, 1f) * ((mould - CARVING_MIN) / CARVING_MIN).coerceIn(0f, 1f)
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
                if (frame.gilt) {
                    drawGilt(frame.color, length, mould, unit, Random(seed * 8 + side))
                } else {
                    drawWood(frame.color, frame.grain, length, mould, unit, FACE_GRAIN, Random(seed * 8 + side))
                }
                val cuts = Random(seed * 8 + side + CARVING_SEED)
                val carver = Carver(finish, frame.color, carving, mould, STICK_LIGHT[side], cuts)
                if (carving > 0f && baroque) {
                    drawBaroque(carver, length, frame.ornament)
                } else if (carving > 0f) {
                    drawCarving(length, mould, STICK_LIGHT[side], carving, cuts)
                }
                drawRect(Brush.verticalGradient(*stops, startY = 0f, endY = mould), size = Size(length, mould))
                // Stands proud of the moulding, so it keeps its own light.
                if (carving > 0f && baroque) drawCentrepiece(carver, length, frame.ornament)
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

    // A cartouche over each mitre on a baroque frame, a rosette on a classical
    // one, carved after the frame was joined, so it hides the seam.
    if (carving > 0f && baroque) {
        listOf(
            Triple(Offset(0f, 0f), Offset(1f, 0f), Offset(0f, 1f)),
            Triple(Offset(w, 0f), Offset(-1f, 0f), Offset(0f, 1f)),
            Triple(Offset(w, h), Offset(-1f, 0f), Offset(0f, -1f)),
            Triple(Offset(0f, h), Offset(1f, 0f), Offset(0f, -1f)),
        ).forEachIndexed { k, (corner, across, down) ->
            val carver = Carver(finish, frame.color, carving, mould, FRAME_LIGHT, Random(seed * 8 + k + CARVING_SEED + 4))
            drawCornerpiece(carver, corner, across, down, frame.ornament)
        }
    } else if (carving > 0f) {
        val at = ROSETTE_AT * mould
        listOf(Offset(at, at), Offset(w - at, at), Offset(w - at, h - at), Offset(at, h - at)).forEachIndexed { k, centre ->
            drawRosette(centre, ROSETTE_SIZE * mould, mould, frame.color, carving, Random(seed * 8 + k + CARVING_SEED + 4))
        }
    }
}

// --- the carving -------------------------------------------------------------

/** Towards the light from the upper left, in each stick's own frame — top, right, bottom, left — and on the frame. */
private val STICK_LIGHT = listOf(Offset(-1f, -1f), Offset(-1f, 1f), Offset(1f, 1f), Offset(1f, -1f)).map { it / sqrt(2f) }
private val FRAME_LIGHT = Offset(-1f, -1f) / sqrt(2f)

/** Keeps the carving's cuts apart from the grain's. */
private const val CARVING_SEED = 0x5eed

/** Below this many pixels across the moulding, the carving fades out. */
private const val CARVING_MIN = 10f

/** The rosettes' centres in from the corners, and their size, in shares of the moulding — on the flat face. */
private const val ROSETTE_AT = 0.45f
private const val ROSETTE_SIZE = 0.22f

/*
 * Where each run of carving sits across the moulding, outer edge to inner, in
 * shares of its width — on the rounded outer edge, the flat face and the bead
 * of [PROFILE].
 */
private const val ROPE_FROM = 0.03f
private const val ROPE_TO = 0.17f
private const val EGG_FROM = 0.27f
private const val EGG_TO = 0.63f
private const val BEAD_AT = 0.80f

/**
 * A closed curve round [centre], [rx] by [ry] turned [tilt] radians, never
 * quite true: each of its [points] wanders up to [wobble] of the way in or out.
 */
private fun lump(centre: Offset, rx: Float, ry: Float, tilt: Float, wobble: Float, random: Random, points: Int = 12): Path {
    val c = cos(tilt)
    val s = sin(tilt)
    val corners = List(points) { k ->
        val a = k * TAU / points
        val r = 1f + random.between(-wobble, wobble)
        val x = rx * r * cos(a)
        val y = ry * r * sin(a)
        centre + Offset(x * c - y * s, x * s + y * c)
    }
    // Through the midpoints, with the points themselves as handles, so it stays smooth.
    val path = Path()
    val start = (corners.last() + corners[0]) / 2f
    path.moveTo(start.x, start.y)
    for (k in 0 until points) {
        val handle = corners[k]
        val end = (handle + corners[(k + 1) % points]) / 2f
        path.quadraticTo(handle.x, handle.y, end.x, end.y)
    }
    path.close()
    return path
}

/**
 * Lights a carved [shape], [r] round [centre], from [toward]: one [raised]
 * catches the light on its near side, darkens on its far one and throws a
 * shadow [lift] past it; one cut in is the other way round, and dark at the
 * bottom.
 */
private fun DrawScope.carve(shape: Path, centre: Offset, r: Float, toward: Offset, lift: Float, raised: Boolean, strength: Float, edge: Float) {
    val near = centre + toward * r
    val far = centre - toward * r
    if (raised) {
        val cast = Path().apply { addPath(shape, -toward * lift) }
        drawPath(Path.combine(PathOperation.Difference, cast, shape), Color.Black.copy(alpha = 0.38f * strength))
    } else {
        drawPath(shape, Color.Black.copy(alpha = 0.2f * strength))
    }
    val lit = SHEEN.copy(alpha = 0.55f * strength)
    val dark = Color.Black.copy(alpha = 0.5f * strength)
    val (a, b) = if (raised) lit to dark else dark to lit
    drawPath(
        shape,
        Brush.linearGradient(
            0f to a.copy(alpha = a.alpha * 0.5f), 0.5f to a.copy(alpha = 0f),
            0.5f to b.copy(alpha = 0f), 1f to b.copy(alpha = b.alpha * 0.6f),
            start = near, end = far,
        ),
    )
    drawPath(shape, Brush.linearGradient(listOf(a, b), near, far), style = Stroke(edge))
}

/**
 * The carving along one stick, [length] along by [mould] wide, x along it and
 * y in from the outer edge, lit from [toward]. Each run draws on its own
 * [Random], so a run that fits one more piece at another size leaves the
 * others as they were.
 */
private fun DrawScope.drawCarving(length: Float, mould: Float, toward: Offset, strength: Float, random: Random) {
    val edge = max(0.5f, 0.016f * mould)
    val lift = 0.035f * mould

    // A rope twist round the outer edge: slanting cuts between the strands.
    val rope = Random(random.nextInt())
    val twist = length / max(1, (length / (0.075f * mould)).roundToInt())
    var x = 0f
    while (x <= length) {
        val from = x + rope.between(-0.08f, 0.08f) * twist
        val lean = 0.09f * mould * rope.between(0.85f, 1.15f)
        val cut = Path().apply {
            moveTo(from, ROPE_FROM * mould)
            quadraticTo(from + 0.3f * lean, (ROPE_FROM + ROPE_TO) / 2f * mould, from + lean, ROPE_TO * mould)
        }
        drawPath(cut, Color.Black.copy(alpha = 0.5f * strength), style = Stroke(0.02f * mould, cap = StrokeCap.Round))
        val wall = Path().apply { addPath(cut, -toward * 0.012f * mould) }
        drawPath(wall, SHEEN.copy(alpha = 0.35f * strength), style = Stroke(0.008f * mould, cap = StrokeCap.Round))
        x += twist
    }

    // Egg and dart along the flat face, stopping short of the rosettes.
    val eggs = Random(random.nextInt())
    val band = (EGG_TO - EGG_FROM) * mould
    val mid = (EGG_FROM + EGG_TO) / 2f * mould
    val end = (ROSETTE_AT + ROSETTE_SIZE) * mould + 0.08f * mould
    val span = length - 2f * end
    if (span > band) {
        val count = max(1, (span / (0.4f * mould)).roundToInt())
        val pitch = span / count
        for (i in 0 until count) {
            val centre = Offset(end + (i + 0.5f) * pitch + eggs.between(-0.03f, 0.03f) * pitch, mid + eggs.between(-0.03f, 0.03f) * band)
            val tilt = eggs.between(-0.07f, 0.07f)
            val size = eggs.between(0.94f, 1.06f)
            val shell = lump(centre, 0.38f * pitch * size, 0.48f * band * size, tilt, 0.04f, eggs)
            carve(shell, centre, 0.48f * band, toward, lift, raised = false, strength, edge)
            val egg = lump(centre + Offset(0f, 0.03f * band), 0.27f * pitch * size, 0.37f * band * size, tilt, 0.05f, eggs)
            carve(egg, centre, 0.37f * band, toward, lift, raised = true, strength, edge)
        }
        for (i in 0..count) {
            val at = end + i * pitch + eggs.between(-0.02f, 0.02f) * pitch
            val lean = eggs.between(-0.04f, 0.04f) * pitch
            val half = 0.07f * pitch * eggs.between(0.85f, 1.15f)
            val top = mid - 0.46f * band
            val bottom = mid + 0.46f * band
            val dart = Path().apply {
                moveTo(at, top)
                quadraticTo(at + half, mid, at + half + lean, mid + 0.15f * band)
                lineTo(at + lean, bottom)
                lineTo(at - half + lean, mid + 0.15f * band)
                quadraticTo(at - half, mid, at, top)
                close()
            }
            carve(dart, Offset(at, mid), 0.46f * band, toward, lift, raised = true, strength, edge)
        }
    }

    // Bead and reel along the bead at the sight edge: a long bead, then two discs.
    val beads = Random(random.nextInt())
    val across = 0.035f * mould
    val beadAt = BEAD_AT * mould
    val step = length / max(1, (length / (0.16f * mould)).roundToInt())
    x = 0f
    while (x <= length) {
        val bead = Offset(x + beads.between(-0.04f, 0.04f) * step, beadAt)
        carve(lump(bead, 0.05f * mould, across, beads.between(-0.05f, 0.05f), 0.05f, beads, 10), bead, 0.05f * mould, toward, lift * 0.6f, raised = true, strength, edge)
        for (disc in 0..1) {
            val reel = Offset(x + (0.085f + 0.026f * disc) * mould, beadAt)
            carve(lump(reel, 0.009f * mould, across * 0.95f, 0f, 0.06f, beads, 8), reel, across, toward, lift * 0.6f, raised = true, strength, edge)
        }
        x += step
    }
}

/**
 * A rosette [r] across round [centre], set in a sunk round over the mitre:
 * eight leaves round a button, each cut a little differently.
 */
private fun DrawScope.drawRosette(centre: Offset, r: Float, mould: Float, wood: Color, strength: Float, random: Random) {
    val edge = max(0.5f, 0.016f * mould)
    val lift = 0.035f * mould
    val solid = 0.9f * strength
    val well = lump(centre, r, r, 0f, 0.03f, random, 16)
    drawPath(well, shade(wood, -0.3f).copy(alpha = solid))
    carve(well, centre, r, FRAME_LIGHT, lift, raised = false, strength, edge)

    val leaves = 8
    val turn = random.nextFloat() * TAU / leaves
    repeat(leaves) { k ->
        val angle = turn + k * TAU / leaves + random.between(-0.08f, 0.08f)
        val along = Offset(cos(angle), sin(angle))
        val side = Offset(-along.y, along.x)
        val reach = r * random.between(0.78f, 0.9f)
        val inner = centre + along * (0.2f * r)
        val tip = centre + along * reach
        val belly = centre + along * (0.62f * reach)
        val leaf = Path().apply {
            moveTo(inner.x, inner.y)
            val left = belly + side * (0.4f * r * random.between(0.85f, 1.1f))
            val right = belly - side * (0.4f * r * random.between(0.85f, 1.1f))
            quadraticTo(left.x, left.y, tip.x, tip.y)
            quadraticTo(right.x, right.y, inner.x, inner.y)
            close()
        }
        drawPath(leaf, shade(wood, random.between(-0.05f, 0.05f)).copy(alpha = solid))
        val heart = centre + along * (0.5f * reach)
        carve(leaf, heart, 0.4f * reach, FRAME_LIGHT, lift, raised = true, strength, edge)
        // The vein, a single nick down the middle.
        val vein = centre + along * (0.78f * reach)
        drawLine(Color.Black.copy(alpha = 0.4f * strength), inner + along * (0.1f * r), vein, edge, cap = StrokeCap.Round)
    }
    val button = lump(centre, 0.24f * r, 0.24f * r, 0f, 0.05f, random, 10)
    drawPath(button, shade(wood, 0.04f).copy(alpha = solid))
    carve(button, centre, 0.24f * r, FRAME_LIGHT, lift, raised = true, strength, edge)
}

// --- the gilding -------------------------------------------------------------

/** Gold leaf: the light it throws back, the dark down in its hollows, and the red bole it is laid on. */
private val GILT_SHINE = Color(0xFFFFF2BF)
private val GILT_SHADOW = Color(0xFF2B1905)
private val BOLE = Color(0xFF9A3F1E)

/*
 * A baroque moulding, outer edge to inner, as light over it: a lip, a big
 * torus the carving runs along, a deep cove, a matt frieze, a bead and a slip
 * down to the canvas. The swings are wider than [PROFILE]'s, for burnished
 * gold throws back far more than a satin wood.
 */
private val BAROQUE_PROFILE = listOf(
    0.00f to -0.55f,
    0.04f to 0.35f,
    0.08f to -0.30f,
    0.12f to -0.10f,
    0.24f to 0.40f,
    0.36f to 0.05f,
    0.48f to -0.40f,
    0.53f to -0.50f,
    0.64f to -0.05f,
    0.70f to 0.30f,
    0.72f to -0.05f,
    0.83f to -0.10f,
    0.85f to -0.40f,
    0.88f to 0.35f,
    0.91f to -0.35f,
    0.95f to 0.25f,
    1.00f to -0.50f,
)

/** One square of gold leaf, in card heights; the seams where they overlap show faintly. */
private const val GILT_LEAF = 0.08f

/** Places the gold has rubbed through on the high outer edge, and dulled down the cove. */
private const val RUBS = 14
private const val GRIME = 10

/** The torus the carving runs along, and the beads, in shares of the moulding's width. */
private const val TORUS_FROM = 0.09f
private const val TORUS_TO = 0.49f
private const val PEARL_AT = 0.875f

/*
 * In moulding widths along a stick: how far from each end the corner
 * cartouches reach, how far either side of the middle the centre ones do,
 * each running leaf's length and spacing, and each gadroon's.
 */
private const val SHELL_CORNER = 1.35f
private const val SHELL_CENTRE = 0.7f
private const val SPRAY_CORNER = 1.15f
private const val GADROON_CORNER = 1.3f
private const val GADROON_CENTRE = 0.75f
private const val RUN_LEAF = 0.8f
private const val RUN_PITCH = 0.55f
private const val GADROON_PITCH = 0.24f

/** Points down each side of an acanthus leaf. */
private const val LEAF_POINTS = 28

/**
 * How carved relief takes the light: gold throws back a lot of it, near its
 * own colour, and darkens to brown; waxed wood throws back less, and warmer.
 */
private class Finish(val shine: Color, val shadow: Color, val gloss: Float)

private val GOLD_FINISH = Finish(GILT_SHINE, GILT_SHADOW, 1f)
private val WOOD_FINISH = Finish(SHEEN, Color(0xFF140A03), 0.55f)

/**
 * What every baroque piece cut into one stretch of a frame shares: its
 * finish and [color], how deep it is cut, [m] pixels to the moulding's width,
 * the light it is lit [toward], and the [random] that makes each piece a
 * little off true.
 */
private class Carver(
    val finish: Finish,
    val color: Color,
    val strength: Float,
    val m: Float,
    val toward: Offset,
    val random: Random,
) {
    val edge = max(0.5f, 0.016f * m)
    val lift = 0.045f * m

    /** The same, on a [Random] of its own, so one run fitting another piece at another size leaves the rest alone. */
    fun fork(toward: Offset = this.toward) = Carver(finish, color, strength, m, toward, Random(random.nextInt()))
}

/**
 * Gold leaf over one stick, [length] along by [across] wide: laid in
 * overlapping squares, rubbed through to the bole on the high outer edge and
 * dulled down the cove. [random] is drawn on the same number of times at any
 * size, so the gold walked up to wears the same as from across the room.
 */
private fun DrawScope.drawGilt(gold: Color, length: Float, across: Float, unit: Float, random: Random) {
    drawRect(shade(gold, random.between(-0.04f, 0.04f)), size = Size(length, across))
    val hairline = max(0.5f, 0.0012f * unit)
    val seams = Random(random.nextInt())
    val leaf = GILT_LEAF * unit
    var x = seams.nextFloat() * leaf
    while (x < length) {
        val lean = seams.between(-0.15f, 0.15f) * across
        drawLine(GILT_SHADOW.copy(alpha = seams.between(0.04f, 0.09f)), Offset(x, 0f), Offset(x + lean, across), hairline)
        x += leaf * seams.between(0.85f, 1.15f)
    }
    fun spot(color: Color, centre: Offset, radius: Float) =
        drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), centre, radius), radius, centre)
    repeat(RUBS) {
        val at = Offset(random.nextFloat() * length, across * random.between(0f, 0.3f))
        spot(BOLE.copy(alpha = random.between(0.12f, 0.3f)), at, across * random.between(0.06f, 0.2f))
    }
    repeat(GRIME) {
        val at = Offset(random.nextFloat() * length, across * random.between(0.45f, 0.75f))
        spot(GILT_SHADOW.copy(alpha = random.between(0.08f, 0.2f)), at, across * random.between(0.1f, 0.3f))
    }
}

/**
 * A carved [shape] standing proud, [r] round [centre]: a shadow thrown past
 * it, the shine on its near side, the dark on its far one, and a burnished
 * spot where it faces the light.
 */
private fun DrawScope.gild(c: Carver, shape: Path, centre: Offset, r: Float) {
    val f = c.finish
    val s = c.strength
    val cast = Path().apply { addPath(shape, -c.toward * c.lift) }
    // Cut round only when the piece is too faint to hide its own shadow.
    drawPath(if (s < 1f) Path.combine(PathOperation.Difference, cast, shape) else cast, f.shadow.copy(alpha = 0.55f * s))
    drawPath(shape, shade(c.color, c.random.between(-0.05f, 0.05f)).copy(alpha = s))
    val near = centre + c.toward * r
    val far = centre - c.toward * r
    val shine = 0.55f * s * f.gloss
    drawPath(
        shape,
        Brush.linearGradient(
            0f to f.shine.copy(alpha = shine), 0.45f to f.shine.copy(alpha = 0f),
            0.55f to f.shadow.copy(alpha = 0f), 1f to f.shadow.copy(alpha = 0.7f * s),
            start = near, end = far,
        ),
    )
    val spot = centre + c.toward * (0.45f * r)
    drawPath(shape, Brush.radialGradient(listOf(f.shine.copy(alpha = 0.7f * s * f.gloss), f.shine.copy(alpha = 0f)), spot, 0.55f * r))
    drawPath(shape, Brush.linearGradient(listOf(f.shine.copy(alpha = 0.6f * s * f.gloss), f.shadow.copy(alpha = 0.8f * s)), near, far), style = Stroke(c.edge))
}

/** A bead [rx] by [ry] round [centre]; cheap, for the hundreds along a sight edge. */
private fun DrawScope.bead(c: Carver, centre: Offset, rx: Float, ry: Float) {
    val s = c.strength
    val size = Size(2f * rx, 2f * ry)
    val topLeft = centre - Offset(rx, ry)
    val r = min(rx, ry)
    val shine = centre + c.toward * (0.35f * r)
    drawOval(c.finish.shadow.copy(alpha = 0.5f * s), topLeft - c.toward * c.lift, size)
    drawOval(shade(c.color, c.random.between(-0.08f, 0f)).copy(alpha = s), topLeft, size)
    drawCircle(Brush.radialGradient(listOf(c.finish.shine.copy(alpha = 0.85f * s * c.finish.gloss), c.finish.shine.copy(alpha = 0f)), shine, 0.65f * r), 0.65f * r, shine)
}

/** A band down [line], [widths] across at each point. */
private fun ribbon(line: List<Offset>, widths: List<Float>): Path {
    val left = ArrayList<Offset>(line.size)
    val right = ArrayList<Offset>(line.size)
    for (i in line.indices) {
        val d = line[min(i + 1, line.lastIndex)] - line[max(i - 1, 0)]
        val n = Offset(-d.y, d.x) / max(d.getDistance(), 1e-3f)
        left += line[i] + n * (widths[i] / 2f)
        right += line[i] - n * (widths[i] / 2f)
    }
    val path = Path()
    path.moveTo(left[0].x, left[0].y)
    for (k in 1 until left.size) path.lineTo(left[k].x, left[k].y)
    for (k in right.indices.reversed()) path.lineTo(right[k].x, right[k].y)
    path.close()
    return path
}

/*
 * The baroque pieces below are laid out in (p, q) moulding widths — p along a
 * stick, q in from its outer edge — and [place] takes them to pixels, so one
 * layout serves every corner and both halves of every side, mirrored.
 */

/**
 * An acanthus leaf from [from] to [to], [width] across at its widest, its edge
 * cut into serrated lobes and its tip curled [curl] off the line, with a rib
 * down the middle and a vein out to each lobe.
 */
private fun DrawScope.drawAcanthus(c: Carver, place: (Offset) -> Offset, from: Offset, to: Offset, width: Float, curl: Float) {
    val along = to - from
    val nrm = Offset(-along.y, along.x) / along.getDistance()
    val lobes = c.random.between(3f, 4f)
    val phase = c.random.between(-0.2f, 0.2f)
    val size = c.random.between(0.92f, 1.06f)
    val upper = ArrayList<Offset>(LEAF_POINTS + 1)
    val lower = ArrayList<Offset>(LEAF_POINTS + 1)
    val rib = ArrayList<Offset>(LEAF_POINTS + 1)
    for (i in 0..LEAF_POINTS) {
        val t = i / LEAF_POINTS.toFloat()
        val spine = from + along * t + nrm * (curl * t * t * t)
        val swell = sin(PI.toFloat() * t.pow(0.7f))
        val half = 0.5f * width * size * swell * (0.66f + 0.34f * abs(cos(lobes * PI.toFloat() * t + phase)))
        upper += place(spine - nrm * half)
        lower += place(spine + nrm * half)
        rib += place(spine)
    }
    val leaf = Path().apply {
        moveTo(upper[0].x, upper[0].y)
        for (k in 1..LEAF_POINTS) lineTo(upper[k].x, upper[k].y)
        for (k in LEAF_POINTS downTo 0) lineTo(lower[k].x, lower[k].y)
        close()
    }
    gild(c, leaf, rib[LEAF_POINTS / 2], 0.5f * width * c.m)

    val groove = c.finish.shadow.copy(alpha = 0.5f * c.strength)
    val midrib = Path().apply {
        moveTo(rib[1].x, rib[1].y)
        for (k in 2..(LEAF_POINTS * 0.85f).toInt()) lineTo(rib[k].x, rib[k].y)
    }
    drawPath(midrib, groove, style = Stroke(1.6f * c.edge, cap = StrokeCap.Round))
    drawPath(
        Path().apply { addPath(midrib, c.toward * c.edge) },
        c.finish.shine.copy(alpha = 0.4f * c.strength * c.finish.gloss),
        style = Stroke(0.8f * c.edge, cap = StrokeCap.Round),
    )
    // A lobe stands out where the serration peaks.
    var k = 1
    while (true) {
        val t = (k - phase / PI.toFloat()) / lobes
        if (t >= 0.9f) break
        if (t > 0.1f) {
            val i = (t * LEAF_POINTS).roundToInt()
            val root = rib[max(i - 2, 0)]
            drawLine(groove, root, rib[i] + (upper[i] - rib[i]) * 0.75f, c.edge, cap = StrokeCap.Round)
            drawLine(groove, root, rib[i] + (lower[i] - rib[i]) * 0.75f, c.edge, cap = StrokeCap.Round)
        }
        k++
    }
}

/**
 * A C-scroll: a band [width] across from [start], running into a volute round
 * [eye] that it enters [r] out at angle [a0], winding clockwise in (p, q) for
 * [sign] 1 and tightening as it goes, a channel down its middle and a boss in
 * the eye.
 */
private fun DrawScope.drawScroll(
    c: Carver, place: (Offset) -> Offset, start: Offset, eye: Offset, r: Float, a0: Float, sign: Float, width: Float,
) {
    val m = c.m
    val entry = eye + Offset(cos(a0), sin(a0)) * r
    val tangent = Offset(-sin(a0), cos(a0)) * sign
    val handle = entry - tangent * ((entry - start).getDistance() * 0.5f)
    val line = ArrayList<Offset>()
    val widths = ArrayList<Float>()
    val run = 10
    for (i in 0 until run) {
        val t = i / run.toFloat()
        val u = 1f - t
        line += place(start * (u * u) + handle * (2f * u * t) + entry * (t * t))
        widths += width * (1f - 0.2f * t) * m
    }
    val turns = 1.15f * c.random.between(0.95f, 1.05f)
    val coil = 32
    for (i in 0..coil) {
        val s = i / coil.toFloat()
        val a = a0 + sign * s * turns * TAU
        line += place(eye + Offset(cos(a), sin(a)) * (r * (1f - 0.7f * s)))
        widths += width * (0.8f - 0.55f * s) * m
    }
    val centre = place(eye)
    gild(c, ribbon(line, widths), centre, r * m)
    val channel = Path().apply {
        moveTo(line[0].x, line[0].y)
        for (k in 1 until line.size - 4) lineTo(line[k].x, line[k].y)
    }
    drawPath(channel, c.finish.shadow.copy(alpha = 0.4f * c.strength), style = Stroke(0.25f * width * m, cap = StrokeCap.Round))
    drawPath(
        Path().apply { addPath(channel, c.toward * (0.1f * width * m)) },
        c.finish.shine.copy(alpha = 0.3f * c.strength * c.finish.gloss),
        style = Stroke(c.edge, cap = StrokeCap.Round),
    )
    val boss = 0.3f * r * m
    gild(c, lump(centre, boss, boss, 0f, 0.05f, c.random, 10), centre, boss)
}

/**
 * A scallop shell hinged at [hinge] and opening towards [tip]: [ribs] fluted
 * ribs fanned [spread] radians either side, each ending in a scallop, the
 * outer ones [taper] shorter, and a boss over the hinge. Fanned all the way
 * round, it is a flower.
 */
private fun DrawScope.drawShell(
    c: Carver, place: (Offset) -> Offset, hinge: Offset, tip: Offset, spread: Float, ribs: Int, taper: Float = 0.15f,
) {
    val h = place(hinge)
    val reach = (place(tip) - h).getDistance()
    val out = (place(tip) - h) / reach
    val side = Offset(-out.y, out.x)
    fun dir(a: Float) = out * cos(a) + side * sin(a)
    val step = 2f * spread / ribs
    for (i in 0 until ribs) {
        val a = -spread + (i + 0.5f) * step + c.random.between(-0.04f, 0.04f) * step
        val len = reach * c.random.between(0.92f, 1f) * (1f - taper * abs(a) / spread)
        val half = 0.45f * step
        val root = h + dir(a) * (0.12f * reach)
        val left = h + dir(a - half) * (0.9f * len)
        val right = h + dir(a + half) * (0.9f * len)
        // Through the crown, so each rib ends in a scallop.
        val crown = (h + dir(a) * (1.08f * len)) * 2f - (left + right) / 2f
        val rib = Path().apply {
            moveTo(root.x, root.y)
            lineTo(left.x, left.y)
            quadraticTo(crown.x, crown.y, right.x, right.y)
            close()
        }
        gild(c, rib, h + dir(a) * (0.6f * len), 0.5f * len)
        drawLine(c.finish.shadow.copy(alpha = 0.35f * c.strength), h + dir(a) * (0.25f * len), h + dir(a) * (0.85f * len), c.edge, cap = StrokeCap.Round)
    }
    val boss = lump(h, 0.16f * reach, 0.13f * reach, atan2(out.y, out.x), 0.05f, c.random, 12)
    gild(c, boss, h, 0.16f * reach)
}

/**
 * The carving along one baroque stick, [length] along, x along it and y in
 * from the outer edge: what runs along the torus and the beads at the sight
 * edge, as [ornament] has them. The ends and the middles are left to the
 * cartouches. Draws on [c]'s random a fixed number of times.
 */
private fun DrawScope.drawBaroque(c: Carver, length: Float, ornament: GalleryWallOrnament) {
    val m = c.m
    // The torus is cut back round the carving, so between the pieces it is deep.
    drawRect(c.finish.shadow.copy(alpha = 0.3f * c.strength), topLeft = Offset(0f, TORUS_FROM * m), size = Size(length, (TORUS_TO - TORUS_FROM) * m))

    val run = c.fork()
    val mid = (TORUS_FROM + TORUS_TO) / 2f
    val band = TORUS_TO - TORUS_FROM
    val half = length / m / 2f
    fun end(sign: Float) = { o: Offset -> Offset(if (sign > 0f) o.x * m else length - o.x * m, o.y * m) }
    when (ornament) {
        // Acanthus from each end towards the middle, each leaf's tip over the next one's stalk.
        GalleryWallOrnament.Shell -> {
            val span = half - SHELL_CENTRE - SHELL_CORNER
            if (span > 0f) {
                val count = max(1, (span / RUN_PITCH).roundToInt())
                val pitch = span / count
                for (sign in listOf(1f, -1f)) for (i in count - 1 downTo 0) {
                    val p = SHELL_CORNER + i * pitch
                    drawAcanthus(run, end(sign), Offset(p, mid), Offset(p + RUN_LEAF, mid), 0.95f * band, -0.08f)
                }
            }
        }
        // Acanthus all the way along, clockwise round the frame.
        GalleryWallOrnament.Acanthus -> {
            val span = 2f * half - 2f * SPRAY_CORNER
            if (span > 0f) {
                val count = max(1, (span / RUN_PITCH).roundToInt())
                val pitch = span / count
                for (i in count - 1 downTo 0) {
                    val p = SPRAY_CORNER + i * pitch
                    drawAcanthus(run, end(1f), Offset(p, mid), Offset(p + RUN_LEAF, mid), 0.95f * band, -0.08f)
                }
            }
        }
        // Gadroons leaning in towards the middle from each end.
        GalleryWallOrnament.Gadroon -> {
            val span = half - GADROON_CENTRE - GADROON_CORNER
            if (span > 0f) {
                val count = max(1, (span / GADROON_PITCH).roundToInt())
                val pitch = span / count
                for (sign in listOf(1f, -1f)) for (i in 0 until count) {
                    val centre = end(sign)(Offset(GADROON_CORNER + (i + 0.5f) * pitch, mid))
                    val r = 0.47f * band * m
                    gild(run, lump(centre, 0.42f * pitch * m, r, sign * 0.45f, 0.04f, run.random), centre, r)
                }
            }
        }
        GalleryWallOrnament.Classical -> Unit
    }

    val beads = c.fork()
    when (ornament) {
        // Bead and reel: a long bead, then two discs.
        GalleryWallOrnament.Acanthus -> {
            val step = length / max(1, (length / (0.16f * m)).roundToInt())
            var x = step / 2f
            while (x < length) {
                bead(beads, Offset(x, PEARL_AT * m), 0.05f * m, 0.03f * m)
                bead(beads, Offset(x + 0.075f * m, PEARL_AT * m), 0.011f * m, 0.028f * m)
                bead(beads, Offset(x + 0.1f * m, PEARL_AT * m), 0.011f * m, 0.028f * m)
                x += step
            }
        }
        else -> {
            val r = (if (ornament == GalleryWallOrnament.Gadroon) 0.026f else 0.034f) * m
            val step = length / max(1, (length / (3f * r)).roundToInt())
            var x = step / 2f
            while (x < length) {
                bead(beads, Offset(x, PEARL_AT * m), r, r)
                x += step
            }
        }
    }
}

/** The cartouche over the middle of a baroque stick, [length] along, as [ornament] has it. */
private fun DrawScope.drawCentrepiece(c: Carver, length: Float, ornament: GalleryWallOrnament) {
    val m = c.m
    val centre = { o: Offset -> Offset(length / 2f + o.x * m, o.y * m) }
    when (ornament) {
        // A shell opening outwards between two scrolls, leaves trailing from under them.
        GalleryWallOrnament.Shell -> {
            for (side in listOf(1f, -1f)) {
                val place = { o: Offset -> Offset(length / 2f + side * o.x * m, o.y * m) }
                drawAcanthus(c, place, Offset(0.3f, 0.62f), Offset(0.95f, 0.68f), 0.22f, 0.06f)
                drawScroll(c, place, Offset(0.1f, 0.2f), Offset(0.5f, 0.32f), 0.13f, -PI.toFloat() / 2f, 1f, 0.09f)
            }
            drawShell(c, centre, Offset(0f, 0.7f), Offset(0f, 0.12f), 1.1f, 7 + c.random.nextInt(3))
        }
        // A palmette: a fan of leaves opening outwards from a boss.
        GalleryWallOrnament.Gadroon -> {
            val hinge = Offset(0f, 0.74f)
            val leaves = 5
            for (k in 0 until leaves) {
                val a = (k - (leaves - 1) / 2f) * 0.5f
                val tip = hinge + Offset(sin(a), -cos(a)) * (0.62f - 0.06f * abs(a))
                drawAcanthus(c, centre, hinge, tip, 0.24f, 0f)
            }
            val boss = centre(hinge)
            gild(c, lump(boss, 0.11f * m, 0.11f * m, 0f, 0.05f, c.random, 12), boss, 0.11f * m)
        }
        else -> Unit
    }
}

/**
 * The cartouche over a baroque mitre at [corner], [across] and [down] running
 * along the two sticks from it, as [ornament] has it.
 */
private fun DrawScope.drawCornerpiece(c: Carver, corner: Offset, across: Offset, down: Offset, ornament: GalleryWallOrnament) {
    val alongAcross = { o: Offset -> corner + (across * o.x + down * o.y) * c.m }
    val alongDown = { o: Offset -> corner + (down * o.x + across * o.y) * c.m }
    val halves = listOf(alongAcross, alongDown)
    val half = (PI / 2f).toFloat()
    when (ornament) {
        // A shell opening into the corner; along each stick a big C-scroll by
        // the outer edge, a small one by the sight edge, and a leaf from under them.
        GalleryWallOrnament.Shell -> {
            for (place in halves) {
                drawAcanthus(c, place, Offset(0.7f, 0.5f), Offset(1.75f, 0.34f), 0.3f, -0.1f)
                drawScroll(c, place, Offset(0.7f, 0.86f), Offset(1.12f, 0.68f), 0.1f, half, -1f, 0.07f)
                drawScroll(c, place, Offset(0.42f, 0.16f), Offset(1.28f, 0.3f), 0.16f, -half, 1f, 0.11f)
            }
            drawShell(c, alongAcross, Offset(0.62f, 0.62f), Offset(0.2f, 0.2f), 1.25f, 8 + c.random.nextInt(3))
        }
        // A spray of acanthus round a flower: two leaves along each stick, one into the corner.
        GalleryWallOrnament.Acanthus -> {
            for (place in halves) {
                drawAcanthus(c, place, Offset(0.45f, 0.5f), Offset(1.35f, 0.72f), 0.22f, 0.08f)
                drawAcanthus(c, place, Offset(0.42f, 0.42f), Offset(1.7f, 0.28f), 0.3f, -0.1f)
            }
            drawAcanthus(c, alongAcross, Offset(0.48f, 0.48f), Offset(0.1f, 0.1f), 0.28f, 0f)
            drawShell(c, alongAcross, Offset(0.47f, 0.47f), Offset(0.3f, 0.3f), PI.toFloat(), 9 + c.random.nextInt(4), taper = 0f)
        }
        // C-scrolls curling into the corner round a small shell.
        GalleryWallOrnament.Gadroon -> {
            for (place in halves) {
                drawScroll(c, place, Offset(1.25f, 0.8f), Offset(0.85f, 0.66f), 0.09f, half, 1f, 0.07f)
                drawScroll(c, place, Offset(1.55f, 0.24f), Offset(0.62f, 0.3f), 0.15f, -half, -1f, 0.1f)
            }
            drawShell(c, alongAcross, Offset(0.45f, 0.45f), Offset(0.12f, 0.12f), 1f, 5 + c.random.nextInt(2))
        }
        GalleryWallOrnament.Classical -> Unit
    }
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
