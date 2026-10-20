package io.github.mejdi14.oddscroll.internal

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max
import kotlin.math.roundToInt

/** Taps to a side. Five by five is enough for a blur of a few pixels. */
private const val TAPS = 5

/**
 * A blurred, cover-fitted copy of [source], made once.
 *
 * There is no blur to reach for in common code, so this is the honest version
 * of one: the picture is laid down twenty-five times on a small offset lattice
 * and the results added together. `Plus` is what makes that a blur rather than
 * a pile — colour and coverage both accumulate, so twenty-five taps at a
 * twenty-fifth each come to exactly the average, and the edges feather where
 * some taps fall outside.
 *
 * It is built small on purpose. Nothing blurred is ever read closely, so a copy
 * at a fraction of the card's size costs a sixteenth of the memory and looks
 * the same once it is scaled back up.
 */
internal fun softCopy(
    source: ImageBitmap,
    width: Int,
    height: Int,
    blur: Float,
): ImageBitmap {
    val w = width.coerceAtLeast(1)
    val h = height.coerceAtLeast(1)
    val target = ImageBitmap(w, h)

    val aspect = w.toFloat() / h
    val sourceAspect = source.width.toFloat() / max(source.height, 1)
    val srcW: Int
    val srcH: Int
    if (sourceAspect > aspect) {
        srcH = source.height
        srcW = (source.height * aspect).roundToInt().coerceIn(1, source.width)
    } else {
        srcW = source.width
        srcH = (source.width / aspect).roundToInt().coerceIn(1, source.height)
    }
    val srcX = (source.width - srcW) / 2
    val srcY = (source.height - srcH) / 2

    val reach = TAPS / 2
    val step = if (reach == 0) 0f else blur / reach
    val weight = 1f / (TAPS * TAPS)

    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        Canvas(target),
        Size(w.toFloat(), h.toFloat()),
    ) {
        for (ty in -reach..reach) {
            for (tx in -reach..reach) {
                withTransform({ translate(tx * step, ty * step) }) {
                    drawImage(
                        image = source,
                        srcOffset = IntOffset(srcX, srcY),
                        srcSize = IntSize(srcW, srcH),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(w, h),
                        alpha = weight,
                        blendMode = BlendMode.Plus,
                        filterQuality = FilterQuality.Medium,
                    )
                }
            }
        }
    }
    return target
}
