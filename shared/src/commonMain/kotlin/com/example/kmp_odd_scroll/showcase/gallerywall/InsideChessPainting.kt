package com.example.kmp_odd_scroll.showcase.gallerywall

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmp_odd_scroll.showcase.internal.lerp
import com.example.kmp_odd_scroll.showcase.internal.smoothstep
import kotlin.math.min

private val Table = Color(0xFF241B16)
private val Brass = Color(0xFFAF8C56)
private val Ivory = Color(0xFFF3E6CC)
private val caption = TextStyle(color = Ivory, fontFamily = FontFamily.Serif, fontSize = 15.sp)

@Composable
internal fun ChessEntryPrompt(visible: Boolean, modifier: Modifier, enter: () -> Unit) {
    BasicText(
        if (visible) "Enter the chess painting  →" else "Look between the windows for the chess painting",
        modifier = modifier.padding(horizontal = 12.dp)
            .background(Table.copy(alpha = 0.87f), RoundedCornerShape(24.dp))
            .then(if (visible) Modifier.clickable(role = Role.Button, onClick = enter) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        style = caption.copy(fontStyle = FontStyle.Italic),
    )
}

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
        } else {
            travel.animateTo(1f, tween(2100, easing = LinearEasing))
        }
    }

    BoxWithConstraints(
        // Own the full pane, including the margins, while the studio is behind the board.
        Modifier.fillMaxSize().clipToBounds().pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent() }
        },
        contentAlignment = Alignment.Center,
    ) {
        val boardSize = minOf(maxWidth - 32.dp, maxHeight - 104.dp, 650.dp).coerceAtLeast(1.dp)
        Canvas(Modifier.fillMaxSize()) {
            val progress = travel.value
            drawRect(Table, alpha = smoothstep(0f, 0.48f, progress))
            if (progress < 0.98f) drawApproach(picture, entry, boardSize.toPx(), progress)
        }
        Column(
            Modifier.width(boardSize).graphicsLayer { alpha = smoothstep(0.78f, 0.98f, travel.value) }
                .then(if (playing) Modifier else Modifier.clearAndSetSemantics {}),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val side = if (position.turn == ChessSide.WHITE) "White" else "Black"
            val turnLabel = when (position.status) {
                ChessStatus.PLAYING -> "$side to move"
                ChessStatus.CHECK -> "$side to move · Check"
                ChessStatus.CHECKMATE -> if (position.turn == ChessSide.WHITE) "Checkmate · Black wins" else "Checkmate · White wins"
                ChessStatus.STALEMATE -> "Stalemate · Draw"
            }
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                BasicText(turnLabel, style = caption.copy(fontSize = 19.sp))
            }
            Box(Modifier.size(boardSize).background(Color(0xFF493020)).border(1.dp, Brass).padding(6.dp)) {
                ChessBoard(position, { if (playing) onMove(it) }, Modifier.fillMaxSize())
            }
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("‹  Studio", Modifier.clickable(enabled = playing, role = Role.Button) { leaving = true }
                    .padding(vertical = 10.dp), style = caption)
                Spacer(Modifier.weight(1f))
                if (position.status == ChessStatus.CHECKMATE || position.status == ChessStatus.STALEMATE) {
                    BasicText("New game", Modifier.clickable(enabled = playing, role = Role.Button) {
                        onMove(ChessPosition.initial())
                    }.padding(vertical = 10.dp), style = caption)
                } else {
                    BasicText("Tap or drag a piece", style = caption.copy(fontStyle = FontStyle.Italic))
                }
            }
        }
        // A press during the approach must not select an invisible piece.
        if (!playing) Box(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
        })
    }
}

// Corners of the wooden board on the 512 × 595 framed picture in the studio atlas.
private val paintedBoard = floatArrayOf(247f, 324f, 366f, 341f, 332f, 402f, 193f, 377f)

private fun DrawScope.drawApproach(picture: ScenePicture, entry: List<Offset>, board: Float, progress: Float) {
    val w = picture.size.width.toFloat()
    val h = picture.size.height.toFloat()
    val fit = min(size.width * 0.9f / w, size.height * 0.93f / h)
    val approachFrame = smoothstep(0f, 0.46f, progress)
    val approachBoard = smoothstep(0.4f, 0.94f, progress)
    val source = FloatArray(8)
    val target = FloatArray(8)
    for (i in 0..3) {
        val u = if (i == 0 || i == 3) 0f else 1f
        val v = if (i < 2) 0f else 1f
        val portraitX = center.x + (u - 0.5f) * w * fit
        val portraitY = center.y + (v - 0.5f) * h * fit
        val x = lerp(entry[i].x * size.width, portraitX, approachFrame)
        val y = lerp(entry[i].y * size.height, portraitY, approachFrame)
        target[2 * i] = lerp(x, center.x + (u - 0.5f) * board, approachBoard)
        target[2 * i + 1] = lerp(y, center.y + (v - 0.5f) * board, approachBoard)
        source[2 * i] = w * lerp(u, paintedBoard[2 * i] / 512f, approachBoard)
        source[2 * i + 1] = h * lerp(v, paintedBoard[2 * i + 1] / 595f, approachBoard)
    }

    // Map the shrinking patch of paint into the advancing frame, ending square over the playable board.
    val toScreen = Matrix()
    val fromPicture = Matrix()
    if (!quadMatrix(target, 1f, 1f, toScreen) || !quadMatrix(source, 1f, 1f, fromPicture)) return
    fromPicture.invert()
    val window = Path().apply {
        moveTo(target[0], target[1])
        for (i in 1..3) lineTo(target[2 * i], target[2 * i + 1])
        close()
    }
    clipPath(window) {
        withTransform({ transform(toScreen); transform(fromPicture) }) {
            drawImage(
                picture.atlas, srcOffset = picture.origin, srcSize = picture.size,
                dstOffset = IntOffset.Zero, dstSize = picture.size, filterQuality = FilterQuality.Medium,
            )
        }
    }
}
