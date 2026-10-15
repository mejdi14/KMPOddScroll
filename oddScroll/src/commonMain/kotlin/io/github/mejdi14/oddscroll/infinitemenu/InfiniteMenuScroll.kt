package io.github.mejdi14.oddscroll.infinitemenu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mejdi14.oddscroll.internal.cardBitmap
import io.github.mejdi14.oddscroll.internal.clamped
import io.github.mejdi14.oddscroll.internal.smoothstep
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tan

/** One entry of the menu. */
data class InfiniteMenuItem(
    val image: ImageBitmap,
    val title: String = "",
    val description: String = "",
)

/** Levels the grey-and-dim ladder is cut into, so filters can be shared. */
private const val SHADES = 24

/** Below this a tile has turned too far away to be worth drawing. */
private const val BACK_CUTOFF = 0.004f

private const val CLICK_SLOP = 8f

@Composable
fun rememberInfiniteMenuScrollState(
    tileCount: Int,
    itemCount: Int,
): InfiniteMenuScrollState = remember(tileCount, itemCount) {
    InfiniteMenuScrollState(tileCount.coerceIn(6, 400), itemCount.coerceAtLeast(1))
}

/**
 * A sphere of tiles, dragged from any direction, that brings whichever tile
 * ends up nearest the front round to face you.
 *
 * Tiles are laid out by walking the golden angle down the axis, which spreads
 * them about as evenly as a sphere allows with no seam, no cluster at the poles
 * and no preferred direction — all of which matter when the thing can be pushed
 * any way at all. Orientation is a quaternion for the same reason: a diagonal
 * drag is one turn about one axis, not a pair of angles that would lock when
 * the sphere rolls over its pole.
 *
 * Far more tiles than items are usual: the pictures repeat around the sphere,
 * which is what makes it read as a surface rather than as a list bent into a
 * ball. Tiles turning away shrink, desaturate, fade toward the page and drop
 * out; the one at the front is the only one at full size and full colour.
 *
 * The camera backs off while the sphere is handled and draws in again as it
 * settles, so a throw opens the whole of it out and resting on one tile brings
 * that tile close.
 *
 * Every part of this is geometry, so it is drawn with one image per tile and
 * needs no shader on any platform.
 *
 * @param items pictures and their captions; they repeat around the sphere.
 * @param tileCount how many tiles the sphere carries, 6 to 400.
 * @param tileSize tile size as a share of its own share of the sphere.
 * @param roundness corner rounding, 0 square to 1 circular.
 * @param zoom how much of the frame the front tile fills.
 * @param pullBack how far the camera backs off while the sphere is moving.
 * @param stretch how far tiles smear along their travel while it spins.
 * @param inertia how long a throw runs for, 0 to 1.
 * @param autoplay seconds at rest before stepping on. 0 never does.
 * @param grayscale drain the colour from everything but the front tile.
 * @param dim how far the tiles behind are taken toward [background].
 * @param captions draw the front tile's title and description.
 */
@Composable
fun InfiniteMenuScroll(
    items: List<InfiniteMenuItem>,
    modifier: Modifier = Modifier,
    background: Color = Color(0xFF060607),
    contentColor: Color = Color(0xFFF2F0EC),
    tileCount: Int = 160,
    tileSize: Float = 0.63f,
    roundness: Float = 0.2f,
    zoom: Float = 1.65f,
    pullBack: Float = 1f,
    stretch: Float = 0.6f,
    inertia: Float = 0.6f,
    autoplay: Float = 3f,
    grayscale: Boolean = true,
    dim: Float = 0.5f,
    captions: Boolean = true,
    onItemClick: ((Int) -> Unit)? = null,
    state: InfiniteMenuScrollState =
        rememberInfiniteMenuScrollState(tileCount, items.size),
) {
    if (items.isEmpty()) {
        Box(modifier)
        return
    }

    val tiles = remember(items, roundness) {
        // Square, because a tile is a square patch of the sphere, and rounded
        // here rather than clipped per frame: the rounding travels with the
        // picture through the perspective instead of being re-cut in screen
        // space every time the tile turns.
        items.map { cardBitmap(it.image, 1f, (roundness.clamped(0f, 1f)) * 0.5f) }
    }
    val shades = remember(grayscale, dim, background) {
        buildShades(grayscale, dim.clamped(0f, 1f), background)
    }
    val settings = remember(tileCount, tileSize, zoom, pullBack, inertia, autoplay) {
        MenuSettings(
            tileCount = tileCount.coerceIn(6, 400),
            tileSize = tileSize.coerceIn(0.2f, 1.2f),
            zoom = zoom.coerceIn(0.2f, 4f),
            pullBack = pullBack.coerceIn(0f, 3f),
            inertia = inertia.coerceIn(0f, 1f),
            autoplaySeconds = max(0f, autoplay),
        )
    }

    LaunchedEffect(state, settings) {
        var previous = 0L
        while (true) {
            withFrameNanos { frame ->
                val deltaMs =
                    if (previous == 0L) 16.67f else (frame - previous) / 1_000_000f
                previous = frame
                state.advance(deltaMs.clamped(1f, 50f), settings)
            }
        }
    }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state, onItemClick) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        state.onDragStart()
                        var last = down.position
                        var travel = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) break
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: break
                            if (!change.pressed) break
                            val dx = change.position.x - last.x
                            val dy = change.position.y - last.y
                            last = change.position
                            travel += hypot(dx, dy)
                            if (travel > CLICK_SLOP) {
                                state.onDrag(dx, dy)
                                change.consume()
                            }
                        }
                        state.onDragEnd()
                        if (travel <= CLICK_SLOP) {
                            val hit = state.pick(down.position)
                            if (hit >= 0) {
                                if (hit == state.front) {
                                    onItemClick?.invoke(state.active)
                                } else {
                                    state.goTo(hit)
                                }
                            }
                        }
                    }
                }
        ) {
            state.tick // read, so the sphere repaints every frame while it turns
            state.viewportWidth = size.width
            state.viewportHeight = size.height
            drawRect(background)
            drawSphere(tiles, items.size, state, settings, shades, stretch)
        }

        if (captions) {
            val item = items[state.active.coerceIn(items.indices)]
            Caption(item.title, item.description, contentColor, state.settle)
        }
    }
}

// --- the sphere --------------------------------------------------------------

private fun DrawScope.drawSphere(
    tiles: List<ImageBitmap>,
    itemCount: Int,
    state: InfiniteMenuScrollState,
    settings: MenuSettings,
    shades: List<ColorFilter?>,
    stretch: Float,
) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f || state.camera <= 0f) return

    val focal = 1f / tan(SPHERE_FOV / 2f)
    val aspect = width / height
    val tileRadius = tileRadiusOf(settings.tileCount, settings.tileSize)
    val count = state.tileCount

    val centre = FloatArray(3)
    val order = ArrayList<TileDraw>(count)

    for (i in 0 until count) {
        state.orientation.rotate(
            state.tiles[i * 3], state.tiles[i * 3 + 1], state.tiles[i * 3 + 2], centre,
        )
        val length = sqrt(centre[0] * centre[0] + centre[1] * centre[1] + centre[2] * centre[2])
        if (length <= 1e-6f) continue
        val cx = centre[0] / length
        val cy = centre[1] / length
        val cz = centre[2] / length

        // The back of the sphere is not drawn at all. These tiles are behind
        // the ones in front of them and would only be paid for to be thrown
        // away by the fade.
        val alpha = smoothstep(0.02f, 0.4f, cz) * state.reveal
        if (alpha < BACK_CUTOFF) continue

        order.add(TileDraw(i, cx, cy, cz, alpha))
    }
    // Back to front, so the nearest tiles cover the ones behind them.
    order.sortBy { it.z }

    val matrix = Matrix()
    state.visible.clear()

    for (tile in order) {
        // A frame for the tile on the sphere's surface: one axis along the
        // surface, one up it, both square to the direction it faces.
        val refY = if (abs(tile.y) > 0.999f) 0f else 1f
        val refZ = if (abs(tile.y) > 0.999f) 1f else 0f
        var tx = refY * tile.z - refZ * tile.y
        var ty = refZ * tile.x - 0f * tile.z
        var tz = 0f * tile.y - refY * tile.x
        val tLength = sqrt(tx * tx + ty * ty + tz * tz)
        if (tLength <= 1e-6f) continue
        tx /= tLength; ty /= tLength; tz /= tLength
        val bx = tile.y * tz - tile.z * ty
        val by = tile.z * tx - tile.x * tz
        val bz = tile.x * ty - tile.y * tx

        // Tiles turning away are drawn smaller as well as fainter, so the
        // sphere reads as a surface curving off rather than as a flat scatter.
        val scale = tileRadius * (0.5f + 0.5f * max(tile.z, 0f))

        // Smear along the way the tile is travelling while the sphere spins.
        val mx = state.axisY * tile.z - state.axisZ * tile.y
        val my = state.axisZ * tile.x - state.axisX * tile.z
        val mz = state.axisX * tile.y - state.axisY * tile.x
        val sweep = sqrt(mx * mx + my * my + mz * mz)
        val smear = if (sweep > 1e-4f) {
            minOf(0.9f, state.speed * stretch * 0.12f) * sweep
        } else {
            0f
        }

        val topLeft = project(
            tile, tx, ty, tz, bx, by, bz, -1f, 1f, scale,
            mx, my, mz, sweep, smear, state.camera, focal, aspect, width, height,
        )
        val topRight = project(
            tile, tx, ty, tz, bx, by, bz, 1f, 1f, scale,
            mx, my, mz, sweep, smear, state.camera, focal, aspect, width, height,
        )
        val bottomLeft = project(
            tile, tx, ty, tz, bx, by, bz, -1f, -1f, scale,
            mx, my, mz, sweep, smear, state.camera, focal, aspect, width, height,
        )
        if (topLeft == null || topRight == null || bottomLeft == null) continue

        val image = tiles[tile.index % itemCount]
        val srcW = image.width
        val srcH = image.height
        val colX = topRight.x - topLeft.x
        val colY = topRight.y - topLeft.y
        val rowX = bottomLeft.x - topLeft.x
        val rowY = bottomLeft.y - topLeft.y
        if (abs(colX) + abs(colY) < 0.3f) continue

        matrix.reset()
        matrix[0, 0] = colX / srcW
        matrix[0, 1] = colY / srcW
        matrix[1, 0] = rowX / srcH
        matrix[1, 1] = rowY / srcH
        matrix[3, 0] = topLeft.x
        matrix[3, 1] = topLeft.y

        // Everything but the tile at the front loses its colour and is taken
        // toward the page, on a ladder of shared filters so a hundred-odd tiles
        // do not each build their own.
        val focus = smoothstep(0.93f, 0.997f, tile.z)
        val shade = shades[((1f - focus) * (SHADES - 1)).toInt().coerceIn(0, SHADES - 1)]

        withTransform({ transform(matrix) }) {
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(srcW, srcH),
                alpha = tile.alpha.coerceAtMost(1f),
                colorFilter = shade,
                filterQuality = FilterQuality.Medium,
            )
        }

        val midX = topLeft.x + (colX + rowX) / 2f
        val midY = topLeft.y + (colY + rowY) / 2f
        state.visible.add(
            InfiniteMenuScrollState.VisibleTile(
                tile.index, midX, midY, hypot(colX, colY) / 2f, tile.z,
            )
        )
    }
}

private class TileDraw(
    val index: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val alpha: Float,
)

/**
 * One corner of a tile, pushed out to the sphere's surface and projected.
 *
 * The corner is laid out in the tile's own surface frame, smeared along the
 * sphere's travel, then normalised back onto the sphere — so a tile is a patch
 * of the surface rather than a flat card pinned to it, and the row of them
 * curves away correctly at the edge of the frame.
 */
private fun project(
    tile: TileDraw,
    tx: Float, ty: Float, tz: Float,
    bx: Float, by: Float, bz: Float,
    cornerX: Float, cornerY: Float,
    scale: Float,
    mx: Float, my: Float, mz: Float,
    sweep: Float,
    smear: Float,
    camera: Float,
    focal: Float,
    aspect: Float,
    width: Float,
    height: Float,
): Offset? {
    var ox = (tx * cornerX + bx * cornerY) * scale
    var oy = (ty * cornerX + by * cornerY) * scale
    var oz = (tz * cornerX + bz * cornerY) * scale

    if (smear > 0f && sweep > 1e-4f) {
        val dx = mx / sweep
        val dy = my / sweep
        val dz = mz / sweep
        val along = ox * dx + oy * dy + oz * dz
        ox += dx * along * smear
        oy += dy * along * smear
        oz += dz * along * smear
    }

    var wx = tile.x * SPHERE_RADIUS + ox
    var wy = tile.y * SPHERE_RADIUS + oy
    var wz = tile.z * SPHERE_RADIUS + oz
    val length = sqrt(wx * wx + wy * wy + wz * wz)
    if (length <= 1e-6f) return null
    val lift = SPHERE_RADIUS * (1f + 0.003f * tile.z) / length
    wx *= lift; wy *= lift; wz *= lift

    val depth = camera - wz
    if (depth <= 0.01f) return null
    return Offset(
        ((wx * focal / aspect / depth) * 0.5f + 0.5f) * width,
        (0.5f - (wy * focal / depth) * 0.5f) * height,
    )
}

/**
 * The grey-and-dim ladder. Saturation and the pull toward the page fold into
 * one matrix, and the whole thing is cut into a fixed number of steps so tiles
 * at similar depths share a filter instead of allocating one each per frame.
 */
private fun buildShades(
    grayscale: Boolean,
    dim: Float,
    background: Color,
): List<ColorFilter?> = List(SHADES) { step ->
    val away = step / (SHADES - 1f)
    val grey = if (grayscale) away else 0f
    val toward = dim * away
    if (grey <= 0.001f && toward <= 0.001f) return@List null

    val s = 1f - grey
    val lr = 0.2126f
    val lg = 0.7152f
    val lb = 0.0722f
    val keep = 1f - toward
    ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                (lr + s * (1 - lr)) * keep, (lg * (1 - s)) * keep, (lb * (1 - s)) * keep, 0f,
                background.red * toward * 255f,
                (lr * (1 - s)) * keep, (lg + s * (1 - lg)) * keep, (lb * (1 - s)) * keep, 0f,
                background.green * toward * 255f,
                (lr * (1 - s)) * keep, (lg * (1 - s)) * keep, (lb + s * (1 - lb)) * keep, 0f,
                background.blue * toward * 255f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
}

// --- caption -----------------------------------------------------------------

@Composable
private fun BoxScope.Caption(
    title: String,
    description: String,
    contentColor: Color,
    settle: Float,
) {
    if (title.isEmpty() && description.isEmpty()) return
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 34.dp)
            .alpha(settle.clamped(0f, 1f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (title.isNotEmpty()) {
            BasicText(
                text = title,
                style = TextStyle(
                    color = contentColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        if (description.isNotEmpty()) {
            BasicText(
                text = description,
                modifier = Modifier.padding(top = 6.dp),
                style = TextStyle(
                    color = contentColor.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
