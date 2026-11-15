package io.github.mejdi14.oddscroll.gallerywall

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.mejdi14.oddscroll.internal.clamped
import io.github.mejdi14.oddscroll.internal.lerp
import io.github.mejdi14.oddscroll.internal.smoothstep
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/*
 * Entering a painting that has been rebuilt as a place: the coast, the meadow
 * and the figures laid out in the world as meshes, each textured with the
 * painting's own paint where it shows and with paint carried on from round it
 * where it does not, and seen through a camera fitted to the painting — so
 * from where it was painted the scene is the painting, and from anywhere else
 * it is the place.
 *
 * The step in: the room goes dark, the painting gives way to the scene behind
 * it with nothing moving, the scene opens out past the frame, and the eye then
 * walks a set route in. A drag turns the head within set bounds, a scroll walks
 * on or back along the route, and the sea's brushwork drifts once in.
 *
 * Each layer is one textured triangle mesh, drawn back to front, its arrays
 * written in place each frame.
 */

/**
 * A painting rebuilt as a place to walk into, for [GalleryWallItem.scene]:
 * the camera it was painted from, a route in, and textured meshes laid out
 * back to front.
 *
 * [geometry] is the scene file, little-endian: `PSC1`; the horizontal field of
 * view, the entry tilt, the turn either way and the tilt down and up, in
 * degrees; the route's point count and its points; then the layer count and
 * for each its kind (0 sky directions, 1 solid, 2 card, 3 water), its index
 * into [textures], its vertex and index counts, the positions (x, y, z with y
 * up and -z ahead), the texture coordinates in texture pixels, a weight per
 * vertex and the triangle indices, padded to four bytes.
 */
class GalleryWallScene(geometry: ByteArray, internal val textures: List<ImageBitmap>) {
    internal val hFov: Float
    internal val tilt: Float
    internal val yawMax: Float
    internal val pitchDown: Float
    internal val pitchUp: Float
    internal val route: FloatArray
    internal val layers: List<SceneLayer>

    init {
        val r = LittleEndian(geometry)
        require(r.int() == 0x31435350) { "Not a scene file" } // "PSC1"
        hFov = r.float().radians()
        tilt = r.float().radians()
        yawMax = r.float().radians()
        pitchDown = r.float().radians()
        pitchUp = r.float().radians()
        route = FloatArray(3 * r.int()) { r.float() }
        layers = List(r.int()) {
            val kind = r.int()
            val texture = r.int()
            val n = r.int()
            val ni = r.int()
            val positions = FloatArray(3 * n) { r.float() }
            val uv = FloatArray(2 * n) { r.float() }
            val weight = FloatArray(n) { r.float() }
            val indices = ShortArray(ni) { r.short() }
            if (ni % 2 == 1) r.short()
            SceneLayer(kind, texture, positions, uv, weight, indices)
        }
    }
}

internal class SceneLayer(
    val kind: Int,
    val texture: Int,
    val positions: FloatArray,
    val uv: FloatArray,
    val weight: FloatArray,
    val indices: ShortArray,
)

private const val SKY = 0
private const val SOLID = 1
private const val WATER = 3

/** Nothing nearer the eye than this is drawn, in metres. */
private const val NEAR_CLIP = 0.05f

/** Where on the way in, linear in time, the painting gives way to the scene, and the scene opens past the frame. */
private const val REVEAL_FROM = 0.08f
private const val REVEAL_TO = 0.22f
private const val OPEN_FROM = 0.25f
private const val OPEN_TO = 0.75f

/** The sea's brushwork: how much of it over the painted sea, and how far it drifts, in texture pixels. */
private const val WATER_STRENGTH = 0.3f
private const val WATER_DRIFT = 5f

private val OPEN_EASE = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)

/** Draws a [GalleryWallScene], one mesh per layer. */
internal class EnterPainting {
    private var forScene: GalleryWallScene? = null
    private var meshes: List<Mesh> = emptyList()
    private val eye = FloatArray(3)

    private class Mesh(val layer: SceneLayer, val vertices: Vertices, val paint: Paint) {
        val z = FloatArray(layer.weight.size)
    }

    fun prepare(scene: GalleryWallScene) {
        if (scene === forScene) return
        forScene = scene
        meshes = scene.layers.mapNotNull { layer ->
            val texture = scene.textures.getOrNull(layer.texture) ?: return@mapNotNull null
            val n = layer.weight.size
            val uv = layer.uv
            val vertices = Vertices(
                VertexMode.Triangles,
                List(n) { Offset.Zero },
                List(n) { Offset(uv[2 * it], uv[2 * it + 1]) },
                List(n) { Color.White },
                layer.indices.map { it.toInt() },
            )
            val tile = if (layer.kind == WATER) TileMode.Repeated else TileMode.Clamp
            val paint = Paint().apply {
                filterQuality = FilterQuality.Medium
                shader = ImageShader(texture, tile, tile)
            }
            Mesh(layer, vertices, paint)
        }
    }

    /**
     * The scene, its painting lying on screen from [left], [top] to [right],
     * [bottom]: [progress] of the way stepped in, linear in time; [route] of
     * the way along the walk in, linear; the head turned [yaw] and tilted
     * [pitch] from the entry pose, in radians; [time] seconds inside.
     */
    fun draw(
        scope: DrawScope,
        scene: GalleryWallScene,
        image: ImageBitmap,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        progress: Float,
        route: Float,
        yaw: Float,
        pitch: Float,
        time: Float,
    ) {
        val e = progress.clamped(0f, 1f)
        val reveal = smoothstep(REVEAL_FROM, REVEAL_TO, e)
        val open = OPEN_EASE.transform(smoothstep(OPEN_FROM, OPEN_TO, e))
        // Nothing moves until the painting has given way to the scene.
        val free = smoothstep(REVEAL_TO, OPEN_TO, e)

        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val focal = (right - left) / 2f / tan(scene.hFov / 2f)
        path(scene.route, smoothstep(0f, 1f, route.clamped(0f, 1f)), eye)
        val turn = yaw * free
        val tilt = scene.tilt + pitch * free
        val cy0 = cos(turn)
        val sy0 = sin(turn)
        val cp = cos(tilt)
        val sp = sin(tilt)
        // Forward, right and up, with y up and -z ahead at no turn.
        val fx = sy0 * cp; val fy = sp; val fz = -cy0 * cp
        val rx = cy0; val rz = sy0
        val ux = -sy0 * sp; val uy = cp; val uz = cy0 * sp
        val water = WATER_STRENGTH * smoothstep(0f, 0.3f, route)

        val w = scope.size.width
        val h = scope.size.height
        scope.clipRect(lerp(left, 0f, open), lerp(top, 0f, open), lerp(right, w, open), lerp(bottom, h, open)) {
            for (mesh in meshes) {
                val layer = mesh.layer
                if (layer.kind == WATER && water <= 0.001f) continue
                project(mesh, focal, cx, cy, fx, fy, fz, rx, rz, ux, uy, uz, water, time)
                drawIntoCanvas { it.drawVertices(mesh.vertices, BlendMode.Modulate, mesh.paint) }
            }
            // The painting itself over the scene until it gives way.
            if (reveal < 1f) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize((right - left).roundToInt(), (bottom - top).roundToInt()),
                    alpha = 1f - reveal,
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
    }

    private fun project(
        mesh: Mesh,
        focal: Float,
        cx: Float,
        cy: Float,
        fx: Float, fy: Float, fz: Float,
        rx: Float, rz: Float,
        ux: Float, uy: Float, uz: Float,
        water: Float,
        time: Float,
    ) {
        val layer = mesh.layer
        val p = layer.positions
        val out = mesh.vertices.positions
        val colors = mesh.vertices.colors
        val z = mesh.z
        val sky = layer.kind == SKY
        val ex = if (sky) 0f else eye[0]
        val ey = if (sky) 0f else eye[1]
        val ez = if (sky) 0f else eye[2]
        val clip = if (sky) 1e-3f else NEAR_CLIP
        for (n in z.indices) {
            val dx = p[3 * n] - ex
            val dy = p[3 * n + 1] - ey
            val dz = p[3 * n + 2] - ez
            val zc = dx * fx + dy * fy + dz * fz
            z[n] = zc
            val zz = if (zc < clip) clip else zc
            out[2 * n] = cx + focal * (dx * rx + dz * rz) / zz
            out[2 * n + 1] = cy - focal * (dx * ux + dy * uy + dz * uz) / zz
        }
        if (layer.kind == WATER) {
            // A small drift of the brushwork over the fixed sea, faded at the shore and the horizon.
            val uv = layer.uv
            val tex = mesh.vertices.textureCoordinates
            for (n in z.indices) {
                val u = uv[2 * n]
                val v = uv[2 * n + 1]
                tex[2 * n] = u + WATER_DRIFT * (sin(v * 0.021f + time * 0.7f) + 0.6f * sin(u * 0.013f - time * 0.45f))
                tex[2 * n + 1] = v + WATER_DRIFT * 0.6f * sin(u * 0.017f + time * 0.6f)
                colors[n] = alphaWhite(layer.weight[n] * water)
            }
        }

        // Triangles behind the eye, and the backs of solid ones, are left out.
        val src = layer.indices
        val dst = mesh.vertices.indices
        val solid = layer.kind == SOLID
        var t = 0
        while (t < src.size) {
            val a = src[t].toInt()
            val b = src[t + 1].toInt()
            val c = src[t + 2].toInt()
            var drop = z[a] < clip || z[b] < clip || z[c] < clip
            if (!drop && solid) {
                val area = (out[2 * b] - out[2 * a]) * (out[2 * c + 1] - out[2 * a + 1]) -
                    (out[2 * c] - out[2 * a]) * (out[2 * b + 1] - out[2 * a + 1])
                drop = area > 0f
            }
            dst[t] = if (drop) 0 else src[t]
            dst[t + 1] = if (drop) 0 else src[t + 1]
            dst[t + 2] = if (drop) 0 else src[t + 2]
            t += 3
        }
    }
}

/** The point [t] of the way along a Catmull-Rom route through [points], x, y, z into [out]. */
private fun path(points: FloatArray, t: Float, out: FloatArray) {
    val count = points.size / 3
    if (count == 0) return
    if (count == 1) { points.copyInto(out, 0, 0, 3); return }
    val segments = count - 1
    val s = t.clamped(0f, 1f) * segments
    val i = s.toInt().coerceAtMost(segments - 1)
    val f = s - i
    val i0 = (i - 1).coerceAtLeast(0)
    val i3 = (i + 2).coerceAtMost(segments)
    for (k in 0..2) {
        val p0 = points[3 * i0 + k]
        val p1 = points[3 * i + k]
        val p2 = points[3 * (i + 1) + k]
        val p3 = points[3 * i3 + k]
        out[k] = 0.5f * (2f * p1 + (-p0 + p2) * f + (2f * p0 - 5f * p1 + 4f * p2 - p3) * f * f +
            (-p0 + 3f * p1 - 3f * p2 + p3) * f * f * f)
    }
}

private fun alphaWhite(alpha: Float): Int = ((alpha.clamped(0f, 1f) * 255f).roundToInt() shl 24) or 0xFFFFFF

private fun Float.radians(): Float = this * (PI.toFloat() / 180f)

private class LittleEndian(private val bytes: ByteArray) {
    private var at = 0

    fun int(): Int {
        val v = (bytes[at].toInt() and 0xFF) or
            ((bytes[at + 1].toInt() and 0xFF) shl 8) or
            ((bytes[at + 2].toInt() and 0xFF) shl 16) or
            ((bytes[at + 3].toInt() and 0xFF) shl 24)
        at += 4
        return v
    }

    fun float(): Float = Float.fromBits(int())

    fun short(): Short {
        val v = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
        at += 2
        return v.toShort()
    }
}
