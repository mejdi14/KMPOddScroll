package io.github.mejdi14.oddscroll.cartwheel

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.mejdi14.oddscroll.internal.PI_F
import io.github.mejdi14.oddscroll.internal.cardBitmap
import io.github.mejdi14.oddscroll.internal.clamped
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** One card on the rim. */
data class CartwheelItem(
    val image: ImageBitmap,
    /** Read out in place of the picture. */
    val label: String = "",
)

/** Which side of the frame the wheel turns about. */
enum class CartwheelHub { Left, Right }

private const val DRAG_SLOP = 2f

/** Corner rounding baked into each card, as a share of its side. */
private const val CORNER_FRACTION = 0.096f

@Composable
fun rememberCartwheelScrollState(
    dampingRatio: Float = 0.8f,
    stiffness: Float = 27f,
    throwSeconds: Float = 0.55f,
): CartwheelScrollState = remember {
    CartwheelScrollState(dampingRatio, stiffness, throwSeconds)
}.also {
    it.dampingRatio = dampingRatio
    it.stiffness = stiffness
    it.throwSeconds = throwSeconds
}

/**
 * A column of cards riding the rim of a wheel that rolls past the frame.
 *
 * The hub sits off to one side, level with the middle of the frame, and every
 * card is pinned flat to the rim a fixed step round from the one before. So a
 * card lies square only as it passes the middle, and leans further the further
 * it is from there — which is the whole effect: one turn, nothing else. No
 * card grows, fades or rounds off on the way through, and the lean is not a
 * second animation but the same angle the card is mounted at.
 *
 * The rim is wide against the frame — [rimRadius] runs to a little over three
 * card widths — so what is on screen is a shallow arc rather than a visible
 * circle, and the cards read as a column that has been bent.
 *
 * Letting go of a drag hands the wheel to a spring aimed on by the parting
 * speed, so a throw coasts a long way, drifts a touch past where it lands, and
 * eases back. Nothing snaps: the wheel rests wherever it stops.
 *
 * @param items cards around the rim, in order; the run repeats, so it has no end.
 * @param cardSize side of a card.
 * @param rimRadius hub to rim; larger flattens the arc.
 * @param spacing gap along the rim between one card and the next.
 * @param hub which side of the frame the wheel turns about.
 * @param dampingRatio of the spring a throw settles on, 1 to stop it bouncing.
 * @param stiffness of that spring; larger settles sooner.
 * @param throwSeconds of the parting speed a throw is aimed on by.
 */
@Composable
fun CartwheelScroll(
    items: List<CartwheelItem>,
    modifier: Modifier = Modifier,
    background: Color = Color(0xFF07070A),
    cardSize: Dp = 240.dp,
    rimRadius: Dp = 752.dp,
    spacing: Dp = 43.dp,
    hub: CartwheelHub = CartwheelHub.Right,
    dampingRatio: Float = 0.8f,
    stiffness: Float = 27f,
    throwSeconds: Float = 0.55f,
    state: CartwheelScrollState = rememberCartwheelScrollState(
        dampingRatio, stiffness, throwSeconds,
    ),
) {
    if (items.isEmpty()) {
        Box(modifier)
        return
    }

    val density = LocalDensity.current.density
    val cards = remember(items) { items.map { cardBitmap(it.image, 1f, CORNER_FRACTION) } }

    LaunchedEffect(state, items.size) {
        var previous = 0L
        while (true) {
            withFrameNanos { frame ->
                val deltaMs = if (previous == 0L) 16.67f else (frame - previous) / 1_000_000f
                previous = frame
                state.advance(deltaMs.clamped(1f, 50f), items.size)
            }
        }
    }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state, cardSize, spacing, density) {
                    // A drag is taken along the rim, and near the middle the
                    // rim runs straight up the frame, so the finger moves the
                    // wheel one for one with what is under it.
                    val pitch = (cardSize.value + spacing.value) * density
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        state.onDragStart(down.uptimeMillis / 1000f)
                        var last = down.position.y
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val dy = change.position.y - last
                            if (abs(dy) > DRAG_SLOP) {
                                last = change.position.y
                                state.onDrag(dy / max(pitch, 1f), change.uptimeMillis / 1000f)
                                change.consume()
                            }
                        }
                        state.onDragEnd()
                    }
                }
                .pointerInput(state, cardSize, spacing, density) {
                    val pitch = (cardSize.value + spacing.value) * density
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val scroll = event.changes.firstOrNull()?.scrollDelta?.y ?: continue
                            if (scroll == 0f) continue
                            state.onWheel((-scroll * 60f / max(pitch, 1f)).coerceIn(-1.5f, 1.5f))
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            state.tick // read, so the wheel repaints every frame while it turns
            drawRect(background)
            // Everything below is authored in density-independent units, the
            // same ones the parameters are given in, and scaled once at the end.
            withTransform({ scale(density, density, Offset.Zero) }) {
                drawWheel(
                    cards = cards,
                    phase = state.phase,
                    width = size.width / density,
                    height = size.height / density,
                    side = cardSize.value,
                    radius = max(rimRadius.value, cardSize.value),
                    pitch = cardSize.value + spacing.value,
                    toTheRight = hub == CartwheelHub.Right,
                )
            }
        }
    }
}

// --- the wheel ---------------------------------------------------------------

private fun DrawScope.drawWheel(
    cards: List<ImageBitmap>,
    phase: Float,
    width: Float,
    height: Float,
    side: Float,
    radius: Float,
    pitch: Float,
    toTheRight: Boolean,
) {
    if (width <= 0f || height <= 0f) return

    // The whole wheel comes down together until a card fits across the frame,
    // so a narrow phone shows the same arrangement rather than a cropped one.
    val fit = (width / (side * 1.5f)).clamped(0.35f, 1f)
    val card = side * fit
    val rim = radius * fit
    val step = pitch * fit / rim          // radians between one card and the next

    // Hub level with the middle of the frame, one radius out from the card
    // that is passing it — so that card lies square at the centre.
    val hubX = if (toTheRight) width / 2f + rim else width / 2f - rim
    val hubY = height / 2f
    val reach = if (toTheRight) 1f else -1f

    // A card is in frame while its centre is within half a diagonal of it.
    val margin = card * 0.7072f
    val span = ceil((height / 2f + margin) / max(pitch * fit, 1f)).toInt() + 1
    val middle = cardsBelow(phase + 0.5f)
    val dst = IntSize(card.roundToInt().coerceAtLeast(1), card.roundToInt().coerceAtLeast(1))

    for (offset in -span..span) {
        val steps = middle + offset
        val angle = (steps - phase) * step
        if (abs(angle) > PI_F / 2f) continue
        val x = hubX - reach * rim * cos(angle)
        val y = hubY - rim * sin(angle)
        if (y < -margin || y > height + margin) continue
        if (x < -margin || x > width + margin) continue

        withTransform({
            translate(x, y)
            rotate(reach * angle * 180f / PI_F, Offset.Zero)
            translate(-card / 2f, -card / 2f)
        }) {
            drawImage(
                image = cards[wrapIndex(steps, cards.size)],
                dstOffset = IntOffset.Zero,
                dstSize = dst,
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}
