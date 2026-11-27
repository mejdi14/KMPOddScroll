package com.example.kmp_odd_scroll.showcase.gallerywall

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor

// The crop of each actual chessman in the transparent atlas, its place in the
// supplied 508 × 604 painting, and the point at which its base meets the board.
private class PaintedMan(
    atlasX: Int, width: Int, height: Int, left: Int, top: Int, footX: Int, footY: Int,
) {
    val crop = IntOffset(atlasX, 0)
    val size = IntSize(width, height)
    val foot = Offset(footX.toFloat(), footY.toFloat())
    val origin = Offset(left.toFloat(), top.toFloat()) - foot
}

private val paintedMen = mapOf(
    4 to PaintedMan(0, 16, 43, 327, 342, 334, 383),
    16 to PaintedMan(18, 11, 21, 319, 332, 324, 351),
    28 to PaintedMan(31, 10, 14, 283, 362, 287, 373),
    33 to PaintedMan(43, 15, 36, 283, 323, 289, 358),
    41 to PaintedMan(60, 12, 22, 274, 334, 280, 353),
    49 to PaintedMan(74, 12, 23, 264, 332, 270, 353),
    52 to PaintedMan(88, 17, 43, 239, 329, 248, 369),
    57 to PaintedMan(107, 15, 44, 251, 309, 258, 350),
)

private val gridToPaint = Matrix().apply { check(quadMatrix(paintedGridCorners, 8f, 8f, this)) }
private val paintToGrid = Matrix(gridToPaint.values.copyOf()).apply { invert() }
private fun cellCenter(square: Int) = Offset(square % 8 + 0.5f, square / 8 + 0.5f)

internal fun paintedSquare(point: Offset): Int? {
    val cell = paintToGrid.map(point)
    if (cell.x !in 0f..<8f || cell.y !in 0f..<8f) return null
    return floor(cell.y).toInt() * 8 + floor(cell.x).toInt()
}

// Homogeneous depth of the square relative to the camera that saw this board.
private fun depth(square: Int): Float {
    val p = cellCenter(square)
    return gridToPaint[0, 3] * p.x + gridToPaint[1, 3] * p.y + gridToPaint[3, 3]
}

internal class PlacedChessman(val square: Int, val piece: ChessPiece) {
    private val painted = paintedMen.getValue(piece.id)
    // The ivory queen is the painting's model for a promotion.
    private val sprite = if (piece.kind == ChessKind.QUEEN) paintedMen.getValue(4) else painted
    val squareCenter = gridToPaint.map(cellCenter(square))
    val foot = if (square == piece.id) painted.foot else squareCenter
    private val scale = depth(if (piece.kind == ChessKind.QUEEN) 4 else piece.id) / depth(square)

    fun covers(point: Offset, alpha: ChessSilhouettes): Boolean {
        val local = (point - foot) / scale - sprite.origin
        if (local.x < 0f || local.y < 0f || local.x >= sprite.size.width || local.y >= sprite.size.height) return false
        return alpha.opaque(sprite.crop.x + local.x.toInt(), local.y.toInt())
    }

    fun draw(scope: DrawScope, atlas: ImageBitmap, base: Offset) = with(scope) {
        val origin = base + sprite.origin * scale
        withTransform({
            translate(origin.x, origin.y)
            scale(scale, scale, Offset.Zero)
        }) {
            drawImage(atlas, srcOffset = sprite.crop, srcSize = sprite.size, dstSize = sprite.size,
                colorFilter = if (piece.kind == ChessKind.QUEEN && piece.side == ChessSide.BLACK) darkPromotion else null,
                filterQuality = FilterQuality.Medium)
        }
    }
}

private val darkPromotion = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.23f, 0.24f, 0.28f, 1f) })

internal class ChessSilhouettes(atlas: ImageBitmap) {
    private val stride = atlas.width
    private val pixels = IntArray(stride * atlas.height).also { atlas.readPixels(it) }
    fun opaque(x: Int, y: Int): Boolean = (pixels[y * stride + x] ushr 24) > 32
}

internal fun ChessPosition.paintOrder(): List<PlacedChessman> =
    (0 until 64).mapNotNull { square -> get(square)?.let { PlacedChessman(square, it) } }.sortedBy { it.foot.y }

// The near cup masks the last squares. A chessman behind its rim stays behind it as it moves.
private val cup = Path().apply {
    moveTo(278f, 402f)
    cubicTo(279f, 392f, 306f, 390f, 320f, 392f)
    cubicTo(330f, 392f, 338f, 396f, 337f, 402f)
    lineTo(336f, 417f)
    lineTo(356f, 423f)
    lineTo(357f, 449f)
    lineTo(263f, 449f)
    lineTo(261f, 425f)
    lineTo(281f, 415f)
    close()
}

private fun squarePath(square: Int): Path {
    val x = (square % 8).toFloat()
    val y = (square / 8).toFloat()
    val p = Path()
    for ((index, corner) in listOf(Offset(x, y), Offset(x + 1, y), Offset(x + 1, y + 1), Offset(x, y + 1)).withIndex()) {
        val point = gridToPaint.map(corner)
        if (index == 0) p.moveTo(point.x, point.y) else p.lineTo(point.x, point.y)
    }
    p.close()
    return p
}

private val squareOutlines = List(64, ::squarePath)

internal fun DrawScope.drawChessPaint(
    picture: ScenePicture,
    men: List<PlacedChessman>,
    selected: Int? = null,
    destinations: Set<Int> = emptySet(),
    dragFoot: Offset? = null,
) {
    drawImage(picture.plate, dstOffset = IntOffset.Zero, filterQuality = FilterQuality.Medium)
    if (selected != null) {
        drawPath(squareOutlines[selected], Color(0xFFC5E5CB), style = Stroke(0.75f))
        for (square in destinations) {
            val center = gridToPaint.map(cellCenter(square))
            drawOval(Color(0xD7E8E6B2), center - Offset(1.7f, 0.85f), Size(3.4f, 1.7f))
            drawPath(squareOutlines[square], Color(0xA5EFE6C0), style = Stroke(0.45f))
        }
    }
    for (man in men) {
        if (man.square != selected || dragFoot == null) man.draw(this, picture.pieces, man.foot)
    }
    if (dragFoot != null) men.firstOrNull { it.square == selected }?.draw(this, picture.pieces, dragFoot)
    clipPath(cup) { drawImage(picture.plate, dstOffset = IntOffset.Zero, filterQuality = FilterQuality.Medium) }
}

// The sides of the original photograph inside its black-and-gold studio frame.
internal val chessPhotoInFrame = Rect(25f, 27f, 486f, 566f)
internal const val PHOTO_W = 508f
internal const val PHOTO_H = 604f

internal fun DrawScope.drawChessFrame(picture: ScenePicture, frame: Matrix, position: ChessPosition) {
    drawChessFrame(picture, frame, position.paintOrder())
}

internal fun DrawScope.drawChessFrame(
    picture: ScenePicture, frame: Matrix, men: List<PlacedChessman>,
    selected: Int? = null, destinations: Set<Int> = emptySet(), dragFoot: Offset? = null,
) {
    withTransform({ transform(frame) }) {
        drawImage(picture.atlas, srcOffset = picture.origin, srcSize = picture.size,
            dstSize = IntSize(512, 595), filterQuality = FilterQuality.Medium)
        withTransform({
            translate(chessPhotoInFrame.left, chessPhotoInFrame.top)
            scale(chessPhotoInFrame.width / PHOTO_W, chessPhotoInFrame.height / PHOTO_H, Offset.Zero)
        }) { drawChessPaint(picture, men, selected, destinations, dragFoot) }
    }
}
