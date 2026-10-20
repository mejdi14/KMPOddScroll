package io.github.mejdi14.oddscroll.infinitespiral

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.mejdi14.oddscroll.internal.PI_F
import io.github.mejdi14.oddscroll.internal.clamped
import io.github.mejdi14.oddscroll.internal.softCopy
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** One card of the spiral. */
data class InfiniteSpiralItem(
    val image: ImageBitmap,
    /** Read out in place of the picture. */
    val label: String = "",
)

/** What moves the spiral. */
enum class SpiralMotion {
    /** Drifts on its own; a drag is ignored. */
    Drift,

    /** Only moves when it is dragged or scrolled. */
    Hand,

    /** Drifts, and gives way to a hand. */
    Both,
}

/** Which way the drift runs. */
enum class SpiralDirection { Up, Down }

/** Size of the blurred copy kept for each card, along its longer side. */
private const val SOFT_SIZE = 160

private const val DRAG_SLOP = 3f

@Composable
fun rememberInfiniteSpiralScrollState(): InfiniteSpiralScrollState =
    remember { InfiniteSpiralScrollState() }

/**
 * A column of cards wound onto a spiral, turning endlessly.
 *
 * Each card sits a fixed step further down than the one before and a fixed turn
 * further round, so the run reads as a helix seen from the side — but nothing
 * is actually in depth. The cards stay on the plane and the roundness comes
 * from where the turn puts them across the frame and how big the near side of
 * it draws them, which is why this one needs no projection and no mesh.
 *
 * The card at the middle is the largest and the sharpest; away from it they
 * shrink, soften and fade out, so the spiral has no visible end in either
 * direction.
 *
 * @param items cards wound onto the spiral, in order.
 * @param motion what moves it — drift, hand, or both.
 * @param speed cards a second while drifting.
 * @param direction which way the drift runs.
 * @param radius how far the spiral swings across the frame.
 * @param cardWidth card width.
 * @param cardHeight card height.
 * @param verticalSpacing step down the spiral between one card and the next.
 * @param perspective depth the near side is enlarged against; larger is flatter.
 * @param cardsPerTurn cards in one full turn of the spiral.
 * @param rotation turn of the whole spiral, in degrees.
 * @param cardTilt lie of each card, in degrees.
 * @param cardRadius corner rounding.
 * @param centerScale how much larger the card at the middle draws.
 * @param edgeFade share of the run over which the ends fade out.
 * @param edgeBlur how soft the ends go.
 * @param grayscale drain the colour, 0 to 1.
 */
@Composable
fun InfiniteSpiralScroll(
    items: List<InfiniteSpiralItem>,
    modifier: Modifier = Modifier,
    background: Color = Color(0xFF07070A),
    motion: SpiralMotion = SpiralMotion.Both,
    speed: Float = 0.55f,
    direction: SpiralDirection = SpiralDirection.Up,
    radius: Dp = 170.dp,
    cardWidth: Dp = 100.dp,
    cardHeight: Dp = 100.dp,
    verticalSpacing: Dp = 60.dp,
    perspective: Dp = 1000.dp,
    cardsPerTurn: Float = 7f,
    rotation: Float = 0f,
    cardTilt: Float = 0f,
    cardRadius: Dp = 10.dp,
    centerScale: Float = 1.2f,
    edgeFade: Float = 0.3f,
    edgeBlur: Dp = 6.dp,
    grayscale: Float = 0f,
    state: InfiniteSpiralScrollState = rememberInfiniteSpiralScrollState(),
) {
    if (items.isEmpty()) {
        Box(modifier)
        return
    }

    val density = LocalDensity.current.density
    val softWidth = if (cardWidth >= cardHeight) SOFT_SIZE else
        (SOFT_SIZE * (cardWidth / cardHeight)).roundToInt()
    val softHeight = if (cardHeight >= cardWidth) SOFT_SIZE else
        (SOFT_SIZE * (cardHeight / cardWidth)).roundToInt()

    val soft = remember(items, softWidth, softHeight, edgeBlur) {
        val reach = edgeBlur.value / cardWidth.value * softWidth
        items.map { softCopy(it.image, softWidth, softHeight, reach) }
    }
    val wash = remember(grayscale) {
        if (grayscale <= 0.001f) null else ColorFilter.colorMatrix(
            ColorMatrix().apply { setToSaturation(1f - grayscale.clamped(0f, 1f)) }
        )
    }

    val drift = motion == SpiralMotion.Drift || motion == SpiralMotion.Both
    val byHand = motion == SpiralMotion.Hand || motion == SpiralMotion.Both
    val signedSpeed = if (direction == SpiralDirection.Down) -speed else speed

    LaunchedEffect(state, signedSpeed, drift, items.size) {
        var previous = 0L
        while (true) {
            withFrameNanos { frame ->
                val deltaMs =
                    if (previous == 0L) 16.67f else (frame - previous) / 1_000_000f
                previous = frame
                state.advance(deltaMs.clamped(1f, 50f), signedSpeed, drift, items.size)
            }
        }
    }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state, byHand, verticalSpacing, density) {
                    if (!byHand) return@pointerInput
                    val spacing = verticalSpacing.value * density
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        state.onDragStart()
                        var last = down.position.y
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) break
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: break
                            if (!change.pressed) break
                            val dy = change.position.y - last
                            last = change.position.y
                            if (abs(dy) > DRAG_SLOP) {
                                state.onDrag(dy, spacing)
                                change.consume()
                            }
                        }
                        state.onDragEnd()
                    }
                }
                .pointerInput(state, byHand, verticalSpacing, density, speed) {
                    if (!byHand) return@pointerInput
                    val spacing = verticalSpacing.value * density
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val scroll = event.changes.firstOrNull()?.scrollDelta?.y
                                ?: continue
                            if (scroll == 0f) continue
                            state.onWheel(scroll * 60f, spacing, max(speed, 0f) / 0.55f)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            state.tick // read, so the spiral repaints every frame while it turns
            drawRect(background)
            // Everything below is authored in density-independent units, the
            // same ones the parameters are given in, and scaled once at the end.
            withTransform({ scale(density, density, Offset.Zero) }) {
                drawSpiral(
                    items = items,
                    soft = soft,
                    state = state,
                    wash = wash,
                    width = size.width / density,
                    height = size.height / density,
                    radius = radius.value,
                    cardW = cardWidth.value,
                    cardH = cardHeight.value,
                    spacing = verticalSpacing.value,
                    perspective = perspective.value,
                    turn = max(cardsPerTurn, 1f),
                    rotation = rotation,
                    tilt = cardTilt,
                    corner = cardRadius.value,
                    centerScale = centerScale,
                    edgeFade = edgeFade,
                    edgeBlur = edgeBlur.value,
                )
            }
        }
    }
}

// --- the spiral --------------------------------------------------------------

private fun DrawScope.drawSpiral(
    items: List<InfiniteSpiralItem>,
    soft: List<ImageBitmap>,
    state: InfiniteSpiralScrollState,
    wash: ColorFilter?,
    width: Float,
    height: Float,
    radius: Float,
    cardW: Float,
    cardH: Float,
    spacing: Float,
    perspective: Float,
    turn: Float,
    rotation: Float,
    tilt: Float,
    corner: Float,
    centerScale: Float,
    edgeFade: Float,
    edgeBlur: Float,
) {
    if (width <= 0f || height <= 0f) return
    val count = items.size
    val half = count / 2f

    // The whole spiral is taken down together until it fits the frame, so a
    // narrow phone shows the same arrangement rather than a cropped one.
    val fit = min(1f, min(width / (cardW * 2.8f), height / (cardH * 2.35f)))
    val swing = min(radius, max(72f, width * 0.36f)) * fit
    val fadeFrom = (1f - edgeFade).clamped(0f, 0.98f)

    val placed = ArrayList<SpiralCard>(count)
    for (index in items.indices) {
        val offset = wrapHalf(index - state.progress, count.toFloat())
        val edge = min(abs(offset) / max(half, 1f), 1f)
        val alpha = 1f - smoothstepAt(fadeFrom, 1f, edge)
        if (alpha <= 0.004f) continue

        val focus = 1f - min(abs(offset) / max(turn * 0.65f, 1f), 1f)
        val angle = (offset * (360f / turn) + rotation) * PI_F / 180f
        val x = sin(angle) * swing
        val z = cos(angle) * swing
        // Nothing actually moves in depth; the near side of the turn is simply
        // drawn larger, which is all the perspective this needs.
        val depthScale = (perspective / max(perspective - z, 1f)).clamped(0.72f, 1.45f)
        val scale = (1f + (centerScale - 1f) * focus) * fit * depthScale

        placed.add(
            SpiralCard(
                index = index,
                x = width / 2f + x,
                y = height / 2f + offset * spacing * fit,
                scale = scale,
                alpha = alpha,
                blur = if (edgeBlur <= 0.01f) 0f else smoothstepAt(0.35f, 1f, edge),
                depth = (z / max(swing, 1f) + 1f) / 2f,
            )
        )
    }
    // Far side first, so the near side of the turn covers it.
    placed.sortBy { it.depth }

    val outline = Path()
    for (card in placed) {
        withTransform({
            translate(card.x, card.y)
            if (tilt != 0f) rotate(tilt, Offset.Zero)
            scale(card.scale, card.scale, Offset.Zero)
            translate(-cardW / 2f, -cardH / 2f)
        }) {
            outline.reset()
            outline.addRoundRect(
                RoundRect(Rect(0f, 0f, cardW, cardH), CornerRadius(corner, corner))
            )
            clipPath(outline) {
                // The blurred copy goes down whole and the sharp one over it at
                // whatever is left, so the pair reads as one card softening
                // rather than as two cards on top of each other.
                if (card.blur > 0.01f) {
                    drawFitted(soft[card.index], cardW, cardH, card.alpha, wash)
                }
                val sharp = card.alpha * (1f - card.blur)
                if (sharp > 0.004f) {
                    drawFitted(items[card.index].image, cardW, cardH, sharp, wash)
                }
            }
            drawRoundRect(
                color = Color.White.copy(alpha = 0.25f * card.alpha),
                size = Size(cardW, cardH),
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = 1f),
            )
        }
    }
}

private class SpiralCard(
    val index: Int,
    val x: Float,
    val y: Float,
    val scale: Float,
    val alpha: Float,
    val blur: Float,
    val depth: Float,
)

/** Cover-fitted into the card, centred. */
private fun DrawScope.drawFitted(
    image: ImageBitmap,
    cardW: Float,
    cardH: Float,
    alpha: Float,
    wash: ColorFilter?,
) {
    val aspect = cardW / cardH
    val imageAspect = image.width.toFloat() / max(image.height, 1)
    val srcW: Int
    val srcH: Int
    if (imageAspect > aspect) {
        srcH = image.height
        srcW = (image.height * aspect).roundToInt().coerceIn(1, image.width)
    } else {
        srcW = image.width
        srcH = (image.width / aspect).roundToInt().coerceIn(1, image.height)
    }
    drawImage(
        image = image,
        srcOffset = IntOffset((image.width - srcW) / 2, (image.height - srcH) / 2),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(cardW.roundToInt().coerceAtLeast(1), cardH.roundToInt().coerceAtLeast(1)),
        alpha = alpha,
        colorFilter = wash,
        filterQuality = FilterQuality.Medium,
    )
}
