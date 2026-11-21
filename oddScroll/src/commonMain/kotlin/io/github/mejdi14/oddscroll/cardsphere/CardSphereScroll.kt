package io.github.mejdi14.oddscroll.cardsphere

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.mejdi14.oddscroll.internal.clamped
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Degrees per radian, as the reference writes it. */
private const val DEG = 57.3f

/** How far outside the frame a card may stray before it stops being drawn. */
private const val CULL = 40f

/** Below this a card has faded out and is not worth the layer. */
private const val FADED = 0.03f

private const val TAP_SLOP = 6f

/**
 * Creates and remembers a [CardSphereScrollState].
 *
 * @param itemCount how many items the field will be given.
 * @param initialIndex the item that starts at the front.
 */
@Composable
fun rememberCardSphereScrollState(
    itemCount: Int,
    initialIndex: Int = 0,
): CardSphereScrollState =
    remember(itemCount) { CardSphereScrollState(itemCount, initialIndex) }

/**
 * A field of flat graphic cards on a transparent sphere, turned by dragging,
 * flicking or scrolling, which coasts and then glides one big card to the
 * front.
 *
 * Each card carries one image (a plate), dealt so that every plate gets an
 * equal share of the field and no two neighbours draw the same one. Nothing is
 * shaded and nothing is blurred — all of the depth is in the projection.
 *
 * Eight of the cards are heroes, carried at roughly four times the area of the
 * rest, and only they are candidates for the front. The field around them is
 * generated from a fixed seed, so it is the same arrangement every time without
 * seventy positions being written out by hand.
 *
 * A flick is thrown rather than tracked: the spin is run forward before the
 * hero is chosen, so the field lands on the card it was aimed at rather than
 * the one that happened to be passing.
 *
 * @param items the plates to deal around the field; the eight heroes take
 *   the first eight.
 * @param modifier applied to the field.
 * @param background the colour behind the cards.
 * @param billboard share of the full facing angle a card turns through as it
 *   passes round the sphere; 0 keeps every card flat to the screen.
 * @param cardPerspective depth of a card's own tilt, in multiples of its width.
 *   Lower is a stronger keystone.
 * @param coastDecay fraction of the spin kept every sixtieth of a second after
 *   a flick. Closer to 1 coasts longer.
 * @param snapSpeed spin, in radians a second, under which the field commits to
 *   a card and glides it to the front.
 * @param userScrollEnabled false ignores drag, flick and wheel; the field can
 *   still be moved through [state].
 * @param onItemClick called with the index of the item on the tapped card.
 *   When null a tap glides the nearest hero card to the front.
 * @param state drives and reports the field; see [CardSphereScrollState].
 */
@Composable
fun CardSphereScroll(
    items: List<ImageBitmap>,
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    billboard: Float = 0.35f,
    cardPerspective: Float = 2.6f,
    coastDecay: Float = 0.88f,
    snapSpeed: Float = 1.6f,
    userScrollEnabled: Boolean = true,
    onItemClick: ((index: Int) -> Unit)? = null,
    state: CardSphereScrollState = rememberCardSphereScrollState(items.size),
) {
    if (items.isEmpty()) {
        Box(modifier)
        return
    }
    val density = LocalDensity.current.density

    SideEffect {
        state.coastDecay = coastDecay
        state.snapSpeed = snapSpeed
    }

    LaunchedEffect(state) {
        var previous = 0L
        while (true) {
            withFrameNanos { frame ->
                val deltaMs =
                    if (previous == 0L) 16.67f else (frame - previous) / 1_000_000f
                previous = frame
                state.advance(frame / 1_000_000f, deltaMs.clamped(1f, 50f))
            }
        }
    }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state, userScrollEnabled, onItemClick) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (userScrollEnabled) state.onDragStart()
                        var last = down.position
                        var travel = 0f
                        var sampleAt = down.uptimeMillis
                        var sampleX = down.position.x
                        var sampleY = down.position.y
                        var vx = 0f
                        var vy = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) break
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: break
                            if (!change.pressed) break
                            val dx = change.position.x - last.x
                            val dy = change.position.y - last.y
                            last = change.position
                            travel += abs(dx) + abs(dy)
                            if (travel > TAP_SLOP && userScrollEnabled) {
                                state.onDrag(dx / density, dy / density)
                                change.consume()
                            }
                            // Velocity over the last stretch rather than the
                            // last frame, so one stuttery sample cannot decide
                            // where a throw lands.
                            val span = (change.uptimeMillis - sampleAt).toFloat()
                            if (span > 40f) {
                                vx = (change.position.x - sampleX) / span * 1000f / density
                                vy = (change.position.y - sampleY) / span * 1000f / density
                                sampleAt = change.uptimeMillis
                                sampleX = change.position.x
                                sampleY = change.position.y
                            }
                        }
                        if (userScrollEnabled) state.onDragEnd()
                        if (travel > TAP_SLOP) {
                            if (userScrollEnabled) state.flick(vx, vy)
                        } else {
                            val picked = state.pick(down.position, density)
                            if (onItemClick != null) {
                                if (picked >= 0) onItemClick(state.cards[picked].flower)
                            } else {
                                state.glideToHero(picked)
                            }
                        }
                    }
                }
                .pointerInput(state, userScrollEnabled) {
                    if (!userScrollEnabled) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val scroll = event.changes.firstOrNull()?.scrollDelta
                                ?: continue
                            if (scroll.x == 0f && scroll.y == 0f) continue
                            val dx = -scroll.x * 60f
                            val dy = -scroll.y * 60f
                            state.onDrag(dx * 0.6f, dy * 0.6f)
                            state.flick(dx * 9f, dy * 9f)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            state.tick // read, so the field repaints every frame while it turns
            drawRect(background)
            drawField(state, items, density, billboard, cardPerspective)
        }
    }
}

// --- the field ---------------------------------------------------------------

private fun DrawScope.drawField(
    state: CardSphereScrollState,
    items: List<ImageBitmap>,
    density: Float,
    billboard: Float,
    cardPerspective: Float,
) {
    // Everything below is in density-independent units. The cards are authored
    // at fixed sizes, so the layout has to be reasoned about at one scale and
    // only turned into device pixels at the very end.
    val w = size.width / density
    val h = size.height / density
    if (w <= 0f || h <= 0f) return

    val unit = min(w, h * 0.8f) / 720f
    val focal = 502f * unit
    // The sphere is stretched into an ellipsoid so the cards reach every edge —
    // taller on a phone, wider on a desktop — rather than leaving the corners
    // of a wide frame empty.
    val stretchX = max(1f, (w / 2f) / (focal * 0.577f) * 0.97f)
    val stretchY = max(1f, (h / 2f) / (focal * 0.577f) * 0.97f)
    state.unit = unit
    state.focal = focal
    state.stretchX = stretchX
    state.stretchY = stretchY

    val turned = FloatArray(3)
    val placed = ArrayList<Placed>(state.cards.size)
    state.visible.clear()

    for ((index, card) in state.cards.withIndex()) {
        state.rotation.rotate(card.x, card.y, card.z, turned)
        val depth = CAMERA - turned[2]
        if (depth <= 0.05f) continue
        val s = 1f / depth

        val sx = w / 2f + turned[0] * focal * s * stretchX
        val sy = h / 2f - turned[1] * focal * s * stretchY

        // Heroes are only big as they come round to the front; away from it
        // they sit at the same half size as everything else, which is what
        // keeps one card reading as the subject.
        val k = ((turned[2] - 0.2f) / 0.55f).clamped(0f, 1f)
        val grow = if (card.hero) 0.5f + 0.5f * (k * k * (3f - 2f * k)) else 0.5f
        val scale = s * unit * grow

        val alpha = if (card.hero) {
            ((turned[2] + 0.9f) / 0.3f).clamped(0f, 1f)
        } else {
            ((turned[2] + 0.8f) / 0.35f).clamped(0f, 1f)
        }
        if (alpha < FADED) continue

        val halfW = card.w * scale
        val halfH = card.h * scale
        if (sx + halfW < -CULL || sx - halfW > w + CULL ||
            sy + halfH < -CULL || sy - halfH > h + CULL
        ) continue

        val faceY = atan2(turned[0], max(0.2f, turned[2])) * DEG * billboard
        val faceX = atan2(turned[1], max(0.2f, turned[2])) * DEG * billboard
        placed.add(Placed(index, card, sx, sy, scale, faceX, faceY, alpha, depth))

        state.visible.add(
            CardSphereScrollState.VisibleCard(index, sx, sy, card.w * scale / 2f, card.h * scale / 2f, depth)
        )
    }

    // Far cards first. The reference sorts by a z-index taken from the same
    // depth, which comes to the same ordering.
    placed.sortByDescending { it.depth }

    val matrix = Matrix()
    for (p in placed) {
        val card = p.card
        val origin = corner(p, 0f, 0f, density, cardPerspective) ?: continue
        val across = corner(p, card.w, 0f, density, cardPerspective) ?: continue
        val down = corner(p, 0f, card.h, density, cardPerspective) ?: continue

        matrix.reset()
        matrix[0, 0] = (across.x - origin.x) / card.w
        matrix[0, 1] = (across.y - origin.y) / card.w
        matrix[1, 0] = (down.x - origin.x) / card.h
        matrix[1, 1] = (down.y - origin.y) / card.h
        matrix[3, 0] = origin.x
        matrix[3, 1] = origin.y

        val plate = items[card.flower % items.size]

        if (p.alpha > 0.995f) {
            withTransform({ transform(matrix) }) { drawPlate(card, plate) }
        } else {
            // Only the cards on the far side need a layer to fade through, and
            // they are a handful, so it is cheaper than threading an alpha
            // through every piece of artwork.
            drawIntoCanvas { canvas ->
                canvas.saveLayer(
                    Rect(0f, 0f, size.width, size.height),
                    Paint().apply { alpha = p.alpha },
                )
                withTransform({ transform(matrix) }) { drawPlate(card, plate) }
                canvas.restore()
            }
        }
    }
}

private class Placed(
    val index: Int,
    val card: SphereCard,
    val sx: Float,
    val sy: Float,
    val scale: Float,
    val faceX: Float,
    val faceY: Float,
    val alpha: Float,
    val depth: Float,
)

/**
 * Where a point of a card lands on screen.
 *
 * The card is tipped about its own two axes and then seen through its own
 * shallow perspective, which is what gives it a near edge and a far one rather
 * than merely a squash. Three corners are enough to carry that into a plain
 * transform; the fourth would only differ by the keystone, which over a card
 * this size is a fraction of a pixel of error against the cost of subdividing.
 */
private fun corner(
    p: Placed,
    px: Float,
    py: Float,
    density: Float,
    cardPerspective: Float,
): Offset? {
    val card = p.card
    var x = px - card.w / 2f
    var y = py - card.h / 2f

    val rz = p.card.rz * 0.017453293f
    val cz = cos(rz)
    val sz = sin(rz)
    val x1 = x * cz - y * sz
    val y1 = x * sz + y * cz

    val ry = p.faceY * 0.017453293f
    val x2 = x1 * cos(ry)
    val z2 = -x1 * sin(ry)

    val rx = p.faceX * 0.017453293f
    val y3 = y1 * cos(rx) - z2 * sin(rx)
    val z3 = y1 * sin(rx) + z2 * cos(rx)

    val perspective = card.w * cardPerspective
    val near = perspective - z3
    if (near <= 1f) return null
    val m = perspective / near

    x = x2 * m * p.scale + p.sx
    y = y3 * m * p.scale + p.sy
    return Offset(x * density, y * density)
}


/**
 * One plate, cover-fitted into its card and clipped to the card's corners.
 *
 * The cards are cut to the plates' own proportions, so in practice the fit
 * takes nothing off — the crop is here for a set that does not match rather
 * than because this one needs it.
 */
private fun DrawScope.drawPlate(card: SphereCard, plate: ImageBitmap) {
    val radius = max(4f, card.w * 0.03f)
    val rounded = Path().apply {
        addRoundRect(RoundRect(Rect(0f, 0f, card.w, card.h), CornerRadius(radius, radius)))
    }
    clipPath(rounded) {
        val cardAspect = card.w / card.h
        val plateAspect = plate.width.toFloat() / max(plate.height, 1)
        val srcW: Int
        val srcH: Int
        if (plateAspect > cardAspect) {
            srcH = plate.height
            srcW = (plate.height * cardAspect).roundToInt().coerceIn(1, plate.width)
        } else {
            srcW = plate.width
            srcH = (plate.width / cardAspect).roundToInt().coerceIn(1, plate.height)
        }
        drawImage(
            image = plate,
            srcOffset = IntOffset((plate.width - srcW) / 2, (plate.height - srcH) / 2),
            srcSize = IntSize(srcW, srcH),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(card.w.roundToInt(), card.h.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
}
