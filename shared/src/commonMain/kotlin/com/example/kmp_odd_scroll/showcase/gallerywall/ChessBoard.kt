package com.example.kmp_odd_scroll.showcase.gallerywall

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlin.math.sin
import kotlin.random.Random

@Composable
internal fun ChessBoard(
    position: ChessPosition,
    onMove: (ChessPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedSquare by remember(position) { mutableStateOf<Int?>(null) }
    var draggedPiece by remember(position) { mutableStateOf<Offset?>(null) }
    val legalTargets by remember(position) {
        derivedStateOf { selectedSquare?.let(position::legalDestinations).orEmpty() }
    }
    val moveCallback by rememberUpdatedState(onMove)

    fun activate(square: Int) {
        val from = selectedSquare
        when {
            square == from -> selectedSquare = null
            from != null && square in legalTargets -> position.move(from, square)?.let(moveCallback)
            position[square]?.side == position.turn -> selectedSquare = square
            else -> selectedSquare = null
        }
    }

    Column(
        modifier
            .aspectRatio(1f)
            .clipToBounds()
            .drawWithCache {
                val cell = size.width / 8
                val woods = List(64) { index -> makeWood(index, cell) }
                onDrawBehind {
                    val source = selectedSquare
                    val drag = draggedPiece
                    val targets = legalTargets
                    woods.forEachIndexed { index, wood ->
                        val origin = Offset(index % 8 * cell, index / 8 * cell)
                        drawRect(wood.color, origin, Size(cell, cell))
                        drawPath(wood.grain, wood.grainColor, style = Stroke(cell * 0.008f))
                        drawLine(wood.edgeLight, origin, origin + Offset(cell, 0f), cell * 0.012f)
                        drawRect(wood.shade, origin, Size(cell, cell))
                        when {
                            index == source -> drawRect(Selection, origin, Size(cell, cell))
                            index in targets -> {
                                val center = origin + Offset(cell / 2, cell / 2)
                                if (position[index] == null) {
                                    drawCircle(MoveDot, cell * 0.105f, center)
                                    drawCircle(MoveOutline, cell * 0.105f, center, style = Stroke(cell * 0.023f))
                                } else {
                                    drawCircle(MoveOutline, cell * 0.46f, center, style = Stroke(cell * 0.045f))
                                }
                            }
                        }
                        val piece = position[index]
                        if (piece != null && (drag == null || index != source)) {
                            drawChessPiece(piece, origin, cell)
                        }
                    }
                    if (drag != null && source != null) {
                        position[source]?.let { drawChessPiece(it, drag - Offset(cell / 2, cell / 2), cell) }
                    }
                }
            }
            .pointerInput(position) {
                fun squareAt(point: Offset): Int? {
                    val edge = size.width
                    if (point.x < 0 || point.y < 0 || point.x >= edge || point.y >= edge) return null
                    return (point.y * 8 / edge).toInt() * 8 + (point.x * 8 / edge).toInt()
                }

                detectDragGestures(
                    orientationLock = null,
                    onDragStart = { down, slopTrigger, _ ->
                        val square = squareAt(down.position)
                        if (square != null && position[square]?.side == position.turn) {
                            selectedSquare = square
                            draggedPiece = slopTrigger.position
                        }
                    },
                    onDrag = { change, _ ->
                        if (draggedPiece != null) {
                            draggedPiece = change.position
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        val from = selectedSquare
                        val to = draggedPiece?.let(::squareAt)
                        draggedPiece = null
                        if (from != null && to != null) position.move(from, to)?.let(moveCallback)
                    },
                    onDragCancel = { draggedPiece = null },
                )
            },
    ) {
        repeat(8) { row ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(8) { column ->
                    val square = row * 8 + column
                    val piece = position[square]
                    val coordinate = "${'a' + column}${8 - row}"
                    val description = buildString {
                        append(coordinate)
                        if (piece != null) {
                            append(", ")
                            append(piece.side.name.lowercase())
                            append(' ')
                            append(piece.kind.name.lowercase())
                        }
                        if (square in legalTargets) append(", legal destination")
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(role = Role.Button) { activate(square) }
                            .semantics {
                                contentDescription = description
                                selected = selectedSquare == square
                            },
                    )
                }
            }
        }
    }
}

private val Selection = Color(0x887C792C)
private val MoveDot = Color(0xA64B4B20)
private val MoveOutline = Color(0xCEEEE1A4)

private data class WoodSquare(
    val color: Color,
    val grain: Path,
    val grainColor: Color,
    val edgeLight: Color,
    val shade: Brush,
)

private fun makeWood(index: Int, cell: Float): WoodSquare {
    val row = index / 8
    val column = index % 8
    val dark = (row + column) % 2 == 1
    val origin = Offset(column * cell, row * cell)
    val random = Random(index * 251 + 54)
    val pigment = if (dark) DarkWood else LightWood
    val grain = Path()
    repeat(13) { line ->
        val y = (line + random.nextFloat()) / 14f
        val start = random.nextFloat() * 0.24f
        val end = 0.76f + random.nextFloat() * 0.24f
        val bend = sin(line * 1.31f + index) * 0.019f
        grain.moveTo(origin.x + start * cell, origin.y + y * cell)
        grain.cubicTo(
            origin.x + 0.32f * cell, origin.y + (y - bend) * cell,
            origin.x + 0.63f * cell, origin.y + (y + bend) * cell,
            origin.x + end * cell, origin.y + (y + bend / 2) * cell,
        )
    }

    return WoodSquare(
        color = pigment[random.nextInt(pigment.size)],
        grain = grain,
        grainColor = if (dark) Color(0x2681160C) else Color(0x26824926),
        edgeLight = if (dark) Color(0x2BECC28A) else Color(0x63FFF0C8),
        shade = Brush.linearGradient(
            colors = listOf(Color(0x09FFF1BE), Color.Transparent, Color(0x251E0802)),
            start = origin,
            end = origin + Offset(cell, cell),
        ),
    )
}

private val LightWood = listOf(Color(0xFFDAB680), Color(0xFFE0BD89), Color(0xFFD4AE79), Color(0xFFE4C18F))
private val DarkWood = listOf(Color(0xFF8D472B), Color(0xFF894329), Color(0xFF995336), Color(0xFF914A2D))

private data class ChessCarving(val silhouette: Path, val detail: Path? = null)

private fun carving(silhouette: String, detail: String? = null) = ChessCarving(
    PathParser().parsePathString(silhouette).toPath(),
    detail?.let { PathParser().parsePathString(it).toPath() },
)

// Each carving shares a 100-unit square and a common foot at y=81.
private val carvings = mapOf(
    ChessKind.PAWN to carving(
        "M27 81 L28 74 Q29 70 35 68 C41 62 43 54 43 50 L39 50 Q36 48 39 45 L42 44 C32 36 38 23 49 23 C61 22 67 36 57 44 L61 45 Q64 48 61 50 L57 50 C57 56 59 63 65 68 Q71 70 72 74 L73 81 Z",
        "M36 68 Q50 71 64 68 M30 75 Q50 78 70 75",
    ),
    ChessKind.KNIGHT to carving(
        "M24 81 L26 72 Q28 68 34 67 C33 58 41 47 53 39 Q43 42 37 49 L26 49 L21 43 Q20 40 24 36 L37 24 L38 13 L47 20 L55 14 L58 23 C73 29 79 46 72 67 Q77 70 77 75 L78 81 Z",
        "M43 30 L47 29 M23 40 L29 41 M55 26 C65 33 70 44 65 58 M34 67 Q52 71 72 67 M27 75 Q51 78 76 75",
    ),
    ChessKind.BISHOP to carving(
        "M25 81 L27 73 Q28 70 35 68 Q42 60 42 53 L38 53 Q34 51 38 47 L41 46 C26 36 43 19 48 16 Q44 12 49 9 Q55 8 56 13 Q56 15 53 17 C62 23 73 37 60 46 L64 48 Q67 51 63 53 L58 53 Q58 62 65 68 Q72 69 74 74 L76 81 Z",
        "M52 36 L60 27 M40 47 Q50 50 61 47 M35 68 Q49 71 65 68 M28 75 Q50 78 73 75",
    ),
    ChessKind.ROOK to carving(
        "M23 81 L25 73 Q27 69 32 67 L38 52 L37 36 L30 31 L28 16 L38 16 L39 23 L45 23 L45 16 L55 16 L55 23 L61 23 L62 16 L72 16 L70 31 L63 36 L62 52 L68 67 Q74 69 75 74 L77 81 Z",
        "M33 31 L67 31 M38 37 L62 37 M33 68 Q50 71 67 68 M26 75 Q50 78 74 75",
    ),
    ChessKind.QUEEN to carving(
        "M24 81 L26 74 Q28 70 34 68 C40 61 41 55 39 51 L35 48 L29 32 C23 32 22 25 27 24 Q32 23 34 28 L42 36 L41 23 C37 21 38 14 43 14 Q49 14 48 20 L50 31 L54 19 C50 14 55 8 60 11 C64 13 63 18 59 21 L58 35 L67 27 C66 22 72 19 76 23 C79 27 76 31 72 31 L64 49 L61 52 C60 59 63 64 66 68 Q73 70 74 75 L76 81 Z",
        "M37 47 Q50 51 63 47 M36 68 Q50 71 65 68 M28 75 Q50 78 73 75",
    ),
    ChessKind.KING to carving(
        "M23 81 L25 74 Q27 70 34 68 C39 63 40 57 38 52 L34 48 C25 45 22 32 30 28 Q40 23 47 33 L47 22 L39 22 L39 16 L47 16 L47 8 L53 8 L53 16 L61 16 L61 22 L53 22 L53 33 Q60 23 70 28 C78 31 75 44 66 48 L62 52 C60 57 61 63 66 68 Q73 70 75 75 L77 81 Z",
        "M33 38 Q38 34 43 41 M57 41 Q62 34 67 38 M36 48 Q50 52 64 48 M35 68 Q50 72 65 68 M27 75 Q50 78 73 75",
    ),
)

private val ivory = Brush.linearGradient(
    0f to Color(0xFFFFFFFF),
    0.34f to Color(0xFFF7E8C1),
    0.65f to Color(0xFFD7B47D),
    1f to Color(0xFFF3D293),
    start = Offset(25f, 25f),
    end = Offset(75f, 81f),
)

private val ebony = Brush.linearGradient(
    0f to Color(0xFF997252),
    0.38f to Color(0xFF5C3D2E),
    0.69f to Color(0xFF2C1B17),
    1f to Color(0xFF624231),
    start = Offset(25f, 25f),
    end = Offset(75f, 81f),
)

private fun DrawScope.drawChessPiece(piece: ChessPiece, origin: Offset, cell: Float) {
    val carving = carvings.getValue(piece.kind)
    val white = piece.side == ChessSide.WHITE
    val unit = cell / 100
    withTransform({
        translate(origin.x, origin.y + cell * 0.055f)
        scale(unit, unit, pivot = Offset.Zero)
    }) {
        drawOval(Color(0x331D0D06), Offset(20f, 76f), Size(64f, 13f))
        drawOval(Color(0x391D0D06), Offset(25f, 78f), Size(54f, 8f))
        drawPath(carving.silhouette, if (white) ivory else ebony)
        drawPath(
            carving.silhouette,
            if (white) Color(0xFF825E3D) else Color(0xFF24140E),
            style = Stroke(1.8f),
        )
        carving.detail?.let { detail ->
            drawPath(detail, if (white) Color(0x9C80572E) else Color(0xAAE4B17E), style = Stroke(1.8f))
        }
    }
}
