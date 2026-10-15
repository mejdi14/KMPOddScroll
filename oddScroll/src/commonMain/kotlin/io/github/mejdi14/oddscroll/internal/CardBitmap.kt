package io.github.mejdi14.oddscroll.internal

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Cuts [source] to the card's aspect and rounds its corners, once, into a
 * bitmap the draw pass can then treat as a plain rectangle.
 *
 * Doing it here rather than per frame matters for both halves: a warped card is
 * no longer a rounded rectangle on screen, so it could not be clipped to one at
 * draw time, and a cover-fit applied after the rounding would cut the rounded
 * corners back off. Baked into the source, the rounding travels through the
 * warp with everything else.
 */
internal fun cardBitmap(
    source: ImageBitmap,
    ratio: Float,
    cornerFraction: Float,
): ImageBitmap {
    val sourceRatio = source.width.toFloat() / max(source.height, 1)
    val cropW: Int
    val cropH: Int
    if (sourceRatio > ratio) {
        cropH = source.height
        cropW = (source.height * ratio).roundToInt().coerceIn(1, source.width)
    } else {
        cropW = source.width
        cropH = (source.width / ratio).roundToInt().coerceIn(1, source.height)
    }
    val cropX = (source.width - cropW) / 2
    val cropY = (source.height - cropH) / 2

    val bitmap = ImageBitmap(cropW, cropH)
    val radius = cornerFraction * min(cropW, cropH)
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        Canvas(bitmap),
        Size(cropW.toFloat(), cropH.toFloat()),
    ) {
        val rounded = Path().apply {
            addRoundRect(
                RoundRect(
                    Rect(0f, 0f, cropW.toFloat(), cropH.toFloat()),
                    CornerRadius(radius, radius),
                )
            )
        }
        clipPath(rounded) {
            drawImage(
                image = source,
                srcOffset = IntOffset(cropX, cropY),
                srcSize = IntSize(cropW, cropH),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(cropW, cropH),
            )
        }
    }
    return bitmap
}
