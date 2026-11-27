package com.example.kmp_odd_scroll.showcase.gallerywall

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmp_odd_scroll.showcase.internal.lerp
import com.example.kmp_odd_scroll.showcase.internal.smoothstep
import kotlin.math.min

private val Table = Color(0xFF241B16)
private val caption = TextStyle(color = Color(0xFFF3E6CC), fontFamily = FontFamily.Serif, fontSize = 15.sp)

@Composable
internal fun ChessEntryPrompt(visible: Boolean, modifier: Modifier, enter: () -> Unit) {
    ChessCaption(if (visible) "Enter the chess painting  →" else "Look between the windows for the chess painting",
        modifier.padding(horizontal = 12.dp), if (visible) enter else null)
}

@Composable
private fun ChessCaption(text: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    BasicText(text, modifier.background(Table.copy(alpha = 0.82f), RoundedCornerShape(24.dp))
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 10.dp), style = caption.copy(fontStyle = FontStyle.Italic))
}

// This camera only magnifies and pans the photograph. Grid perspective, piece
// silhouettes and the bit of board hidden by the cup all stay in painting coordinates.
private class ChessCloseUp(val viewport: IntSize) {
    private val w = viewport.width.toFloat()
    private val h = viewport.height.toFloat()
    private val zoom = maxOf(w / PHOTO_W, h / PHOTO_H, min(w / 212f, h / 170f))
    private val left = (w / 2 - 278f * zoom).coerceIn(min(0f, w - PHOTO_W * zoom), 0f)
    private val top = (h * 0.52f - 357f * zoom).coerceIn(min(0f, h - PHOTO_H * zoom), 0f)
    private val fit = min(w * 0.90f / 512f, h * 0.93f / 595f)
    private val quad = FloatArray(8)
    val matrix = Matrix()

    fun toPaint(screen: Offset) = (screen - Offset(left, top)) / zoom

    fun placeFrame(entry: List<Offset>, progress: Float) {
        val straighten = smoothstep(0f, 0.47f, progress)
        val approach = smoothstep(0.42f, 1f, progress)
        for (i in entry.indices) {
            val x = if (i == 0 || i == 3) 0f else 512f
            val y = if (i < 2) 0f else 595f
            val uprightX = w / 2 + (x - 256f) * fit
            val uprightY = h / 2 + (y - 297.5f) * fit
            val closeX = left + (x - chessPhotoInFrame.left) * PHOTO_W / chessPhotoInFrame.width * zoom
            val closeY = top + (y - chessPhotoInFrame.top) * PHOTO_H / chessPhotoInFrame.height * zoom
            quad[2 * i] = lerp(lerp(entry[i].x * w, uprightX, straighten), closeX, approach)
            quad[2 * i + 1] = lerp(lerp(entry[i].y * h, uprightY, straighten), closeY, approach)
        }
        quadMatrix(quad, 512f, 595f, matrix)
    }
}

private fun squareName(square: Int) = "${'a' + square % 8}${8 - square / 8}"

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun InsideChessPainting(
    picture: ScenePicture,
    entry: List<Offset>,
    position: ChessPosition,
    onMove: (ChessPosition) -> Unit,
    returnToStudio: () -> Unit,
) {
    val travel = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }
    val playing by remember { derivedStateOf { travel.value == 1f && !leaving } }
    BackHandler { leaving = true }
    LaunchedEffect(leaving) {
        if (leaving) {
            travel.animateTo(0f, tween((1150 * travel.value).toInt(), easing = LinearEasing))
            returnToStudio()
        } else travel.animateTo(1f, tween(1900, easing = LinearEasing))
    }

    val men = remember(position) { position.paintOrder() }
    val silhouettes = remember(picture) { ChessSilhouettes(picture.pieces) }
    var selected by remember(position) { mutableStateOf<Int?>(null) }
    var dragFoot by remember(position) { mutableStateOf<Offset?>(null) }
    val targets = remember(position, selected) { selected?.let(position::legalDestinations).orEmpty() }
    val move by rememberUpdatedState(onMove)
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val camera = remember(viewport) { ChessCloseUp(viewport) }

    fun activate(square: Int?) {
        val from = selected
        val next = if (from != null && square != null) position.move(from, square) else null
        if (next != null) move(next)
        else selected = square?.takeIf { it != from && position[it]?.side == position.turn }
    }

    val side = if (position.turn == ChessSide.WHITE) "White" else "Black"
    val status = when (position.status) {
        ChessStatus.PLAYING -> "$side to move"
        ChessStatus.CHECK -> "$side to move · Check"
        ChessStatus.CHECKMATE -> if (position.turn == ChessSide.WHITE) "Checkmate · Black wins" else "Checkmate · White wins"
        ChessStatus.STALEMATE -> "Stalemate · Draw"
    }
    val original = remember { ChessPosition.initial() }
    val atStart = position.turn == ChessSide.WHITE && men.size == 8 && men.all { it.piece == original[it.square] }

    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewport = it }) {
        Canvas(Modifier.fillMaxSize()
            .semantics {
                if (playing) {
                    contentDescription = "Painted chessboard. $status."
                    customActions = men.filter { it.piece.side == position.turn }.map { man ->
                        CustomAccessibilityAction("Select ${man.piece.kind.name.lowercase()} on ${squareName(man.square)}") {
                            selected = man.square
                            true
                        }
                    } + targets.map { to ->
                        CustomAccessibilityAction("Move to ${squareName(to)}") { activate(to); true }
                    }
                }
            }
            .pointerInput(camera, playing, position) {
                if (!playing) {
                    awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                    return@pointerInput
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val start = camera.toPaint(down.position)
                    // Pick the topmost silhouette first. Its crown can be several ranks above its foot.
                    val hit = men.asReversed().firstOrNull { it.covers(start, silhouettes) }
                    val square = hit?.square ?: paintedSquare(start)
                    val held = men.firstOrNull { it.square == square && it.piece.side == position.turn }
                    var dragged = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (pointer.isConsumed || event.changes.count { it.pressed } > 1) break
                            val point = camera.toPaint(pointer.position)
                            if ((pointer.position - down.position).getDistance() > viewConfiguration.touchSlop) dragged = true
                            val foot = held?.let { it.foot + (point - start) }
                            if (!pointer.pressed) {
                                if (!dragged) {
                                    val floor = paintedSquare(start)
                                    // A tall friendly piece can hide a legal square behind its crown.
                                    val from = selected
                                    val legal = from?.let(position::legalDestinations).orEmpty()
                                    activate(when {
                                        hit?.square in legal -> hit?.square
                                        floor in legal -> floor
                                        else -> square
                                    })
                                } else if (held != null && foot != null) {
                                    val landing = held.squareCenter + (point - start)
                                    val drop = paintedSquare(landing)?.let { position.move(held.square, it) }
                                        ?: paintedSquare(point)?.let { position.move(held.square, it) }
                                    drop?.let(move)
                                }
                                pointer.consume()
                                break
                            }
                            if (dragged && held != null) {
                                selected = held.square
                                dragFoot = foot
                            }
                            pointer.consume()
                        }
                    } finally {
                        dragFoot = null
                    }
                }
            }) {
            if (viewport == IntSize.Zero) return@Canvas
            val progress = travel.value
            drawRect(Table, alpha = smoothstep(0f, 0.50f, progress))
            camera.placeFrame(entry, progress)
            drawChessFrame(picture, camera.matrix, men,
                if (playing) selected else null, if (playing) targets else emptySet(), dragFoot)
        }
        if (playing) {
            ChessCaption(if (atStart) "White to move · Mate in three" else status,
                Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
            ChessCaption("‹  Studio", Modifier.align(Alignment.BottomStart).padding(16.dp)) { leaving = true }
            if (!atStart) {
                ChessCaption("Reset puzzle", Modifier.align(Alignment.BottomEnd).padding(16.dp)) { move(original) }
            }
        }
    }
}
