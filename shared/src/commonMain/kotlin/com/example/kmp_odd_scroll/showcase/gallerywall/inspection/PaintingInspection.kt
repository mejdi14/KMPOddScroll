package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmp_odd_scroll.showcase.gallerywall.GalleryWallItem
import com.example.kmp_odd_scroll.showcase.gallerywall.GalleryWallScrollState
import com.example.kmp_odd_scroll.showcase.gallerywall.supportsHardwareMeshes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import kotlin.math.roundToInt
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
        // Analysis uses the depth and mask. Let the room release the much larger scene textures.
        entries.add(item.copy(scene = null) to result)
        return result
    }
}

private val SurfaceModes = listOf(InspectionMode.Lighting, InspectionMode.Microscope, InspectionMode.Colors)
private val StructureModes = listOf(InspectionMode.Explode, InspectionMode.PointCloud, InspectionMode.Blueprint, InspectionMode.Objects)

/** The wall owns the resting painting. Gestures and effects use its exact projection. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PaintingInspection(
    item: GalleryWallItem,
    analysis: PaintingAnalysis?,
    hung: () -> Rect,
    exitLabel: String?,
    flipLabel: String?,
    stepLabel: String?,
    state: GalleryWallScrollState,
    onEffect: (Boolean) -> Unit,
    drawFold: DrawScope.(frame: Rect, progress: Float, over: Boolean) -> Unit,
) {
    val canStudy = state.canStudy
    val studyLocked = state.studyVisible && !canStudy
    var requested by remember(item.image) { mutableStateOf<InspectionMode?>(null) }
    var active by remember(item.image) { mutableStateOf<InspectionMode?>(null) }
    var leaving by remember { mutableStateOf(false) }
    var replay by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    val c = remember(active, item.image) { InspectionControls(active) }
    val drawnFrame = remember { mutableStateOf(Rect.Zero) }
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
    DisposableEffect(effect) {
        onEffect(effect != null)
        onDispose { onEffect(false); effect?.close() }
    }
    LaunchedEffect(studyLocked) {
        if (studyLocked) requested = null
    }
    val onExitNow by rememberUpdatedState(state::uninspect)
    LaunchedEffect(requested, analysis, replay, leaving) {
        if (active != requested || leaving || (active == InspectionMode.Blueprint && replay > 0)) {
            val duration = ((active?.durationMillis ?: 0) * progress.value).roundToInt()
            progress.animateTo(0f, tween(duration, easing = LinearEasing))
            active = null
        }
        if (leaving) {
            onExitNow()
            return@LaunchedEffect
        }
        val next = requested ?: return@LaunchedEffect
        if (analysis == null && next != InspectionMode.Microscope) return@LaunchedEffect
        active = next
        val remaining = ((1f - progress.value) * next.durationMillis).roundToInt()
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
    }
    LaunchedEffect(selection, c) {
        val focus = Animatable(c.focus)
        if (c.selected >= 0 && c.selected != selection) focus.animateTo(0f, tween(230)) { c.focus = value }
        c.selected = selection
        if (selection >= 0) focus.animateTo(1f, tween(460)) { c.focus = value }
    }
    val exitStudy = {
        if (!leaving && state.showingInspection) {
            if (state.inside) state.leave() else leaving = true
        }
    }
    BackHandler(enabled = state.showingInspection) {
        if (!leaving) {
            if (requested != null || active != null) requested = null else exitStudy()
        }
    }
    val tool = requested ?: active
    val lens = LocalInspectionLens.current
    Box(Modifier.fillMaxSize().clipToBounds()) {
        val input = if (state.studyVisible && !leaving) {
            Modifier
                .pointerInput(effect, c, canStudy, exitLabel) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val microscope = effect as? MicroscopeInspection
                        if (active == InspectionMode.Lighting) aimLight(down.position, drawnFrame.value, c)
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
                                        drawnFrame.value, event.calculateCentroid(useCurrent = true), pan, event.calculateZoom(), c)
                                InspectionMode.Lighting -> aimLight(point, drawnFrame.value, c)
                                else -> Unit
                            }
                            if (moved) pressed.forEach { it.consume() }
                        } while (true)
                        if (!moved && (active == InspectionMode.Objects || active == InspectionMode.Colors))
                            toggle(effect?.hit(drawnFrame.value, down.position, c))
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
                                aimLight(change.position, drawnFrame.value, c)
                            if (event.type == PointerEventType.Scroll && effect is MicroscopeInspection) {
                                effect.transform(drawnFrame.value, change.position, Offset.Zero,
                                    exp((-change.scrollDelta.y * 0.12f).coerceIn(-1f, 1f)), c)
                                change.consume()
                            }
                        }
                    }
                }
        } else Modifier
        Canvas(Modifier.fillMaxSize().semantics {
            contentDescription = listOfNotNull(item.title, active?.label).joinToString(", ")
        }.then(input)) {
            val t = progress.value
            val home = hung()
            val fold = state.fold
            val over = state.over
            val frame = if (fold > 0f || over) home else lerp(home, toolFrame(home, active), ease(t))
            drawnFrame.value = frame
            if (!frame.isEmpty) {
                when {
                    fold > 0f -> drawFold(frame, fold, over)
                    over -> paintImage(item.back ?: item.image, frame)
                    effect != null -> clipRect { effect.draw(this, frame, t, c) }
                    else -> Unit
                }
            }
        }

        InspectionLayout(
            title = item.title, exitLabel = exitLabel, onExit = exitStudy,
            hung = hung, state = state,
            index = { end, compact ->
                val modes = if (end) StructureModes else SurfaceModes
                if (tool != null && tool !in modes) {
                    InspectionAdjustments(requested, active, analysis, c, selection, toggle) { replay++ }
                } else {
                    InspectionModeIndex(modes, tool, canStudy && !leaving, end, compact,
                        onMode = { requested = it })
                }
            },
            action = { end ->
                if (tool != null) {
                    if (end) InspectionAction("Reset", "↺", true, enabled = !leaving) { requested = null }
                } else if (end && stepLabel != null && supportsHardwareMeshes) {
                    InspectionAction(stepLabel, "→", true, state.canStepInside && !leaving,
                        gridFeedback = false) {
                        lens?.clear()
                        state.stepInside()
                    }
                } else if (!end && flipLabel != null && state.hasFlipAction) {
                    InspectionAction(flipLabel, "↪", false, state.canFlip && !leaving, onClick = state::flip)
                }
            },
        )
    }
}

@Composable
private fun InspectionLayout(
    title: String,
    exitLabel: String?,
    onExit: () -> Unit,
    hung: () -> Rect,
    state: GalleryWallScrollState,
    index: @Composable (end: Boolean, compact: Boolean) -> Unit,
    action: @Composable (end: Boolean) -> Unit,
) {
    if (!state.studyVisible) return

    val entrance = remember { Animatable(0f) }
    LaunchedEffect(entrance) { entrance.animateTo(1f, tween(440)) }

    val safeDrawing = WindowInsets.safeDrawing
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val safePadding = safeDrawing.asPaddingValues(density)
    val safeStart = safePadding.calculateStartPadding(direction)
    val safeEnd = safePadding.calculateEndPadding(direction)
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .windowInsetsPadding(safeDrawing.only(WindowInsetsSides.Vertical))
            .graphicsLayer { alpha = 1f - ease(state.entryWave) },
    ) {
        val safeWidth = (maxWidth - safeStart - safeEnd).coerceAtLeast(0.dp)
        val portrait = maxHeight > safeWidth
        val compact = maxHeight < 460.dp || safeWidth < 600.dp
        val inset = (safeWidth * 0.025f).coerceIn(12.dp, 36.dp)
        val endInset = safeEnd + inset
        val startInset = maxOf(safeStart, endInset)
        val verticalInset = (maxHeight * 0.025f).coerceIn(4.dp, 24.dp)
        val availableWidth = (maxWidth - startInset - endInset).coerceAtLeast(0.dp)
        val rail = (safeWidth * 0.14f).coerceIn(102.dp, 156.dp)
            .coerceAtMost(minOf(safeWidth * 0.35f, availableWidth * 0.5f))
        val titleWidth = minOf(280.dp, safeWidth * 0.48f, availableWidth)
        val titleHeight = minOf(if (compact) 62.dp else 86.dp, maxHeight * 0.23f)
        val foot = minOf(48.dp, maxHeight * 0.18f)
        val startTravel = with(density) { (startInset + rail + 8.dp).toPx() }
        val endTravel = with(density) { (endInset + maxOf(rail, titleWidth) + 8.dp).toPx() }
        val start = Modifier.graphicsLayer {
            translationX = (if (direction == LayoutDirection.Ltr) -startTravel else startTravel) * (1f - entrance.value)
        }
        val end = Modifier.graphicsLayer {
            translationX = (if (direction == LayoutDirection.Ltr) endTravel else -endTravel) * (1f - entrance.value)
        }
        // On narrow screens the two lists start below the canvas where it actually hangs.
        val indexTop = if (portrait) {
            val safeTop = safeDrawing.getTop(density)
            val belowArt = with(density) { (hung().bottom - safeTop).toDp() } + 6.dp
            belowArt.coerceIn(titleHeight, maxOf(titleHeight, maxHeight - foot - verticalInset * 2 - 48.dp))
        } else titleHeight
        Box(Modifier.fillMaxSize().padding(start = startInset, end = endInset, top = verticalInset, bottom = verticalInset)) {
            BasicText(
                title, Modifier.align(Alignment.TopEnd).then(end).widthIn(max = titleWidth).padding(top = 8.dp),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = InspectionInk, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.End, fontSize = if (compact) 16.sp else 22.sp),
            )
            if (exitLabel != null) {
                Box(Modifier.align(Alignment.TopStart).then(start).width(rail)) {
                    InspectionAction(exitLabel, "←", false, onClick = onExit)
                }
            }
            Row(
                Modifier.fillMaxSize().padding(top = indexTop, bottom = foot),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(start.width(rail)) { index(false, compact) }
                Box(end.width(rail)) { index(true, compact) }
            }
            Box(Modifier.align(Alignment.BottomStart).then(start).width(rail)) { action(false) }
            Box(Modifier.align(Alignment.BottomEnd).then(end).width(rail)) { action(true) }
        }
    }
}

private fun aimLight(point: Offset, frame: Rect, c: InspectionControls) {
    if (frame.isEmpty) return
    c.lightX = ((point.x - frame.left) / frame.width).coerceIn(-0.25f, 1.25f)
    c.lightY = ((point.y - frame.top) / frame.height).coerceIn(-0.25f, 1.25f)
}
