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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
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
import androidx.compose.ui.unit.toSize
import com.example.kmp_odd_scroll.showcase.gallerywall.GalleryWallItem
import com.example.kmp_odd_scroll.showcase.gallerywall.GalleryWallScrollState
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
    var bounds by mutableStateOf(Rect.Zero)
}

private const val SETTLE_MS = 400

private val SurfaceModes = listOf(InspectionMode.Lighting, InspectionMode.Microscope, InspectionMode.Colors)
private val StructureModes = listOf(InspectionMode.Explode, InspectionMode.PointCloud, InspectionMode.Blueprint, InspectionMode.Objects)

/** The bare canvas travels between its wall position and the center of the archive. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PaintingInspection(
    item: GalleryWallItem,
    analysis: PaintingAnalysis?,
    hung: () -> Rect,
    gridReveal: () -> Float,
    exitLabel: String?,
    flipLabel: String?,
    stepLabel: String?,
    state: GalleryWallScrollState,
    drawFold: DrawScope.(frame: Rect, progress: Float, over: Boolean) -> Unit,
) {
    val canStudy = state.canStudy
    val studyLocked = state.studyVisible && !canStudy
    var requested by remember(item.image) { mutableStateOf<InspectionMode?>(null) }
    var active by remember(item.image) { mutableStateOf<InspectionMode?>(null) }
    var leaving by remember { mutableStateOf(false) }
    var replay by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    val settle = remember { Animatable(0f) }
    LaunchedEffect(Unit) { settle.animateTo(1f, tween(SETTLE_MS)) }
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
    LaunchedEffect(studyLocked) {
        if (studyLocked) requested = null
    }
    val onExitNow by rememberUpdatedState(state::leave)
    LaunchedEffect(requested, analysis, replay, leaving) {
        if (active != requested || leaving || (active == InspectionMode.Blueprint && replay > 0)) {
            if (progress.value > 0f) progress.animateTo(0f, tween(350))
            active = null
        }
        if (leaving) {
            settle.animateTo(0f, tween(SETTLE_MS))
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
    val exitStudy = {
        if (!leaving && state.studyVisible) {
            if (state.inside) state.leave() else leaving = true
        }
    }
    BackHandler(enabled = state.studyVisible) {
        if (!leaving) {
            if (requested != null || active != null) requested = null else exitStudy()
        }
    }
    val tool = requested ?: active
    Box(Modifier.fillMaxSize().clipToBounds().background(ArchivePaper).onPlaced { geometry.root = it }) {
        LabGrid(ArchiveInk, ArchivePaper, {
            restingFrame(geometry.bounds.size, item.image).translate(geometry.bounds.topLeft)
        }, gridReveal, state::entryWave, Modifier.matchParentSize())
        ArchiveLayout(
            title = item.title, exitLabel = exitLabel, onExit = exitStudy,
            index = { end, compact ->
                val modes = if (end) StructureModes else SurfaceModes
                if (tool != null && tool !in modes) {
                    InspectionAdjustments(requested, active, analysis, c, selection, toggle) { replay++ }
                } else {
                    ArchiveModeIndex(modes, tool, canStudy && !leaving, end, compact,
                        onMode = { requested = it })
                }
            },
            action = { end ->
                if (tool != null) {
                    if (end) ArchiveAction("Reset", "↺", true, enabled = !leaving) { requested = null }
                } else if (end && stepLabel != null && (state.canStepInside || studyLocked)) {
                    ArchiveAction(stepLabel, "→", true, state.canStepInside && !leaving, ArchiveAccent, state::stepInside)
                } else if (!end && flipLabel != null && (state.canFlip || studyLocked)) {
                    ArchiveAction(flipLabel, "↪", false, state.canFlip && !leaving, onClick = state::flip)
                }
            },
        ) { stage ->
            val input = if (state.studyVisible && !leaving) {
                Modifier
                    .pointerInput(effect, c, canStudy, exitLabel) {
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
                            if (!moved && active == null && requested == null && exitLabel == null) exitStudy()
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
            } else Modifier
            Canvas(stage.onPlaced { coordinates ->
                geometry.root?.let { root ->
                    geometry.bounds = Rect(root.localPositionOf(coordinates, Offset.Zero), coordinates.size.toSize())
                }
            }.semantics {
                contentDescription = listOfNotNull(item.title, active?.label).joinToString(", ")
            }.then(input)) {
                val t = progress.value
                val from = hung().translate(-geometry.bounds.topLeft)
                val rest = restingFrame(size, item.image)
                // Step inside picks up the painting at the wall after its grid pulse ends.
                val position = settle.value * (1f - ease(state.entryWave))
                val home = if (from.isEmpty) rest else lerp(from, rest, position)
                val fold = state.fold
                val over = state.over
                val frame = if (fold > 0f || over) home else lerp(home, toolFrame(home, active), ease(t))
                geometry.frame = frame
                if (!frame.isEmpty) {
                    when {
                        fold > 0f -> drawFold(frame, fold, over)
                        over -> paintImage(item.back ?: item.image, frame)
                        effect != null -> clipRect { effect.draw(this, frame, t, c) }
                        else -> paintImage(item.image, frame)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveLayout(
    title: String,
    exitLabel: String?,
    onExit: () -> Unit,
    index: @Composable (end: Boolean, compact: Boolean) -> Unit,
    action: @Composable (end: Boolean) -> Unit,
    painting: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val portrait = maxHeight > maxWidth
        val compact = maxHeight < 460.dp || maxWidth < 600.dp
        val inset = (maxWidth * 0.025f).coerceIn(12.dp, 36.dp)
        val verticalInset = (maxHeight * 0.025f).coerceIn(4.dp, 24.dp)
        val rail = (maxWidth * 0.12f).coerceIn(88.dp, 150.dp).coerceAtMost(maxWidth * 0.35f)
        val titleWidth = minOf(rail * 1.25f, maxWidth * 0.44f)
        val titleHeight = minOf(if (compact) 62.dp else 86.dp, maxHeight * 0.23f)
        val foot = minOf(48.dp, maxHeight * 0.18f)
        // In tall windows the two indexes sit below the canvas, still at opposite edges.
        val indexTop = if (portrait) maxOf(titleHeight, maxHeight * 0.59f) else titleHeight
        val stage = if (portrait) {
            Modifier.align(Alignment.TopCenter).padding(top = titleHeight + verticalInset)
                .fillMaxWidth(0.85f).height((indexTop - titleHeight).coerceAtLeast(0.dp))
        } else {
            Modifier.align(Alignment.Center).padding(horizontal = inset * 2 + rail)
                .fillMaxWidth().fillMaxHeight(0.74f)
        }
        painting(stage)

        Box(Modifier.fillMaxSize().padding(horizontal = inset, vertical = verticalInset)) {
            BasicText(
                title, Modifier.align(Alignment.TopStart).widthIn(max = titleWidth).padding(top = 5.dp),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = ArchiveInk, fontFamily = FontFamily.Serif,
                    fontSize = if (compact) 21.sp else 28.sp, lineHeight = if (compact) 23.sp else 31.sp),
            )
            if (exitLabel != null) {
                Box(Modifier.align(Alignment.TopEnd).width(rail)) {
                    ArchiveAction(exitLabel, "×", true, onClick = onExit)
                }
            }
            Row(
                Modifier.fillMaxSize().padding(top = indexTop, bottom = foot),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(rail)) { index(false, compact) }
                Box(Modifier.width(rail)) { index(true, compact) }
            }
            Box(Modifier.align(Alignment.BottomStart).width(rail)) { action(false) }
            Box(Modifier.align(Alignment.BottomEnd).width(rail)) { action(true) }
        }
    }
}

private fun aimLight(point: Offset, frame: Rect, c: InspectionControls) {
    if (frame.isEmpty) return
    c.lightX = ((point.x - frame.left) / frame.width).coerceIn(-0.25f, 1.25f)
    c.lightY = ((point.y - frame.top) / frame.height).coerceIn(-0.25f, 1.25f)
}
