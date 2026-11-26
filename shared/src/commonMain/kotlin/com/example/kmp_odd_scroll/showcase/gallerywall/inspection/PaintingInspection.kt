package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmp_odd_scroll.showcase.gallerywall.GalleryWallItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import kotlin.math.exp

/** Keep the expensive image measurements when revisiting a painting. The gallery owns this cache. */
internal class AnalysisCache {
    private val entries = mutableListOf<Pair<GalleryWallItem, PaintingAnalysis>>()

    @OptIn(ExperimentalResourceApi::class)
    suspend fun get(item: GalleryWallItem): PaintingAnalysis {
        val index = entries.indexOfFirst {
            it.first.image === item.image && it.first.depth === item.depth &&
                it.first.inspectionMask === item.inspectionMask &&
                it.first.inspectionMaskResource == item.inspectionMaskResource
        }
        if (index >= 0) return entries.removeAt(index).also { entries.add(it) }.second
        // Decode the original PNG so density scaling cannot mix categorical label values.
        val mask = item.inspectionMask ?: item.inspectionMaskResource?.let { resource ->
            withContext(Dispatchers.Default) {
                getDrawableResourceBytes(getSystemResourceEnvironment(), resource).decodeToImageBitmap()
            }
        }
        val result = analyzePainting(item.image, item.depth, mask)
        if (entries.size >= 3) entries.removeAt(0)
        entries.add(item to result)
        return result
    }
}

private class StageGeometry {
    var frame = Rect.Zero
    var root: LayoutCoordinates? = null
    var stage: LayoutCoordinates? = null
}

/**
 * The study fading in over the bare painting, which moves in from where it
 * hung only if the stage cannot hold it there; then the grid spreading out.
 */
private const val SETTLE_MS = 400
private const val GRID_MS = 1300

/**
 * The study of [item], with its frame and glass off. It fills the same box as
 * the wall, where the bare canvas lies at [hung], so the painting stays as it
 * is and the bench comes up round it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PaintingInspection(
    item: GalleryWallItem,
    analysis: PaintingAnalysis?,
    hung: () -> Rect,
    background: Color,
    ink: Color,
    exitLabel: String?,
    flipLabel: String?,
    stepLabel: String?,
    onExit: () -> Unit,
    onFlip: () -> Unit,
    onStep: () -> Unit,
) {
    var requested by remember(item.image) { mutableStateOf<InspectionMode?>(null) }
    var active by remember(item.image) { mutableStateOf<InspectionMode?>(null) }
    var leaving by remember { mutableStateOf(false) }
    var replay by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    val c = remember(active, item.image) { InspectionControls(active) }
    val geometry = remember { StageGeometry() }
    var selection by remember(c) { mutableIntStateOf(-1) }
    val toggle: (Int?) -> Unit = { selection = if (it == null || it == selection) -1 else it }
    val effect: InspectionEffect? = remember(active, item.image, analysis) {
        when (active) {
            InspectionMode.Microscope -> MicroscopeInspection(item.image)
            null -> null
            else -> analysis?.let {
                when (active) {
                    InspectionMode.Explode, InspectionMode.Objects -> SpatialInspection(item.image, it, active == InspectionMode.Objects)
                    InspectionMode.Lighting -> LightingInspection(item.image, it).withMeshFallback()
                    InspectionMode.PointCloud -> PointCloudInspection(item.image, it).withMeshFallback()
                    InspectionMode.Blueprint -> BlueprintInspection(item.image, it)
                    InspectionMode.Colors -> ColorInspection(item.image, it).withMeshFallback()
                    else -> null
                }
            }
        }
    }
    DisposableEffect(effect) { onDispose { effect?.close() } }
    val onExitNow by rememberUpdatedState(onExit)
    LaunchedEffect(requested, analysis, replay, leaving) {
        if (active != requested || leaving || (active == InspectionMode.Blueprint && replay > 0)) {
            progress.animateTo(0f, tween(350))
            active = null
        }
        if (leaving) {
            onExitNow()
            return@LaunchedEffect
        }
        val next = requested ?: return@LaunchedEffect
        if (analysis == null && next != InspectionMode.Microscope) return@LaunchedEffect
        active = next
        val duration = when (next) {
            InspectionMode.Blueprint -> 2800
            InspectionMode.Colors -> 1450
            InspectionMode.Objects, InspectionMode.Explode -> 1150
            else -> 850
        }
        progress.animateTo(1f, tween(duration, easing = LinearEasing))
    }
    LaunchedEffect(selection, c) {
        val focus = Animatable(c.focus)
        if (c.selected >= 0 && c.selected != selection) focus.animateTo(0f, tween(230)) { c.focus = value }
        c.selected = selection
        if (selection >= 0) focus.animateTo(1f, tween(460)) { c.focus = value }
    }
    BackHandler(enabled = !leaving) {
        if (requested != null || active != null) requested = null else leaving = true
    }
    // The painting settles from where it hung into the stage as the study fades in, and only then
    // does the grid spread out behind it.
    val settle = remember { Animatable(0f) }
    val grid = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        settle.animateTo(1f, tween(SETTLE_MS))
        grid.animateTo(1f, tween(GRID_MS, easing = LinearEasing))
    }
    // Where the painting hung, in the stage's own coordinates; read as the stage is drawn.
    val hungAt: DrawScope.() -> Rect = {
        val root = geometry.root
        val stage = geometry.stage
        if (root != null && stage != null && root.isAttached && stage.isAttached) {
            hung().translate(-root.localPositionOf(stage, Offset.Zero))
        } else Rect.Zero
    }
    val rest: DrawScope.() -> Rect = { restingFrame(size, item.image, hungAt()) }
    BoxWithConstraints(Modifier.fillMaxSize().background(background).onPlaced { geometry.root = it }) {
        val compact = maxWidth < 620.dp
        val short = maxHeight < 440.dp
        val side = if (compact || short) 14.dp else 30.dp
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(start = side, end = side, top = if (short) 4.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    BasicText(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = ink, fontFamily = FontFamily.Serif, fontSize = if (short) 18.sp else 24.sp))
                    if (!short) BasicText(listOf(item.artist, item.date).filter { it.isNotEmpty() }.joinToString("  ·  "),
                        style = TextStyle(color = ink.copy(alpha = 0.70f), fontSize = 12.sp))
                }
                if (requested != null || active != null) ToolButton("Reset", ink, background) { requested = null }
                if (exitLabel != null) ToolButton(exitLabel, ink, background) { leaving = true }
            }
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds().onPlaced { geometry.stage = it }) {
                LabGrid(ink, { rest().center }, grid::value, Modifier.matchParentSize())
                Canvas(Modifier.matchParentSize()
                    .semantics { contentDescription = listOfNotNull(item.title, active?.label).joinToString(", ") }
                    .pointerInput(effect, c) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val microscope = effect as? MicroscopeInspection
                            if (active == InspectionMode.Lighting) aimLight(down.position, geometry.frame, c)
                            val navigating = microscope?.navigate(down.position, c) == true
                            var moved = false
                            var travel = 0f
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                val point = pressed.first().position
                                val pan = event.calculatePan()
                                travel += pan.getDistance()
                                if (travel > viewConfiguration.touchSlop || pressed.size > 1) moved = true
                                when (active) {
                                    InspectionMode.Explode, InspectionMode.PointCloud -> {
                                        c.yaw = (c.yaw + pan.x * 3.0f / size.width.coerceAtLeast(1)).coerceIn(-1.3f, 1.3f)
                                        c.pitch = (c.pitch - pan.y * 2.4f / size.height.coerceAtLeast(1)).coerceIn(-0.85f, 0.85f)
                                    }
                                    InspectionMode.Microscope -> if (navigating) microscope.navigate(point, c)
                                        else if (pan != Offset.Zero || pressed.size > 1) microscope?.transform(
                                            geometry.frame, event.calculateCentroid(useCurrent = true), pan, event.calculateZoom(), c)
                                    InspectionMode.Lighting -> aimLight(point, geometry.frame, c)
                                    else -> Unit
                                }
                                if (moved) pressed.forEach { it.consume() }
                            } while (true)
                            if (!moved && (active == InspectionMode.Objects || active == InspectionMode.Colors))
                                toggle(effect?.hit(geometry.frame, down.position, c))
                            down.consume()
                        }
                    }
                    .pointerInput(effect, c) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                if (event.type == PointerEventType.Move && active == InspectionMode.Lighting)
                                    aimLight(change.position, geometry.frame, c)
                                if (event.type == PointerEventType.Scroll && effect is MicroscopeInspection) {
                                    effect.transform(geometry.frame, change.position, Offset.Zero,
                                        exp((-change.scrollDelta.y * 0.12f).coerceIn(-1f, 1f)), c)
                                    change.consume()
                                }
                            }
                        }
                    }
                ) {
                    val t = progress.value
                    val from = hungAt()
                    val home = if (from.isEmpty) rest() else lerp(from, rest(), ease(settle.value))
                    val frame = lerp(home, toolFrame(home, active), ease(t))
                    geometry.frame = frame
                    if (!frame.isEmpty) {
                        if (effect == null) paintImage(item.image, frame) else effect.draw(this, frame, t, c)
                    }
                }
            }
            InspectionToolbar(requested, active, analysis, c, selection, compact, short, ink, background,
                onMode = { if (!leaving) requested = it }, onSelect = toggle, onReplay = { replay++ },
                flipLabel = flipLabel, stepLabel = stepLabel, onFlip = onFlip, onStep = onStep)
        }
    }
}

private fun aimLight(point: Offset, frame: Rect, c: InspectionControls) {
    if (frame.isEmpty) return
    c.lightX = ((point.x - frame.left) / frame.width).coerceIn(-0.25f, 1.25f)
    c.lightY = ((point.y - frame.top) / frame.height).coerceIn(-0.25f, 1.25f)
}
