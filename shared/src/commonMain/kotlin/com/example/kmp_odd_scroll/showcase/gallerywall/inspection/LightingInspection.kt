package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A tangent-space normal (R/G) and surface height (B) at each analysis sample. */
internal class PaintingRelief(analysis: PaintingAnalysis) {
    val width = analysis.width
    val height = analysis.height
    val pixels = IntArray(width * height)

    init {
        val depth = analysis.depth
        val fine = FloatArray(depth.size) { i ->
            val c = analysis.pixels[i]
            (((c ushr 16 and 255) * 0.2126f + (c ushr 8 and 255) * 0.7152f + (c and 255) * 0.0722f) / 255f)
        }
        fun index(x: Int, y: Int) = y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)
        val scale = max(width, height) * 0.20f
        for (y in 0 until height) for (x in 0 until width) {
            val i = y * width + x
            // The wider difference follows the form. The narrow one keeps its silhouette and pigment ridges.
            fun slope(dx: Int, dy: Int): Float {
                val l = index(x - dx, y - dy)
                val r = index(x + dx, y + dy)
                val wide = depth[index(x + 3 * dx, y + 3 * dy)] - depth[index(x - 3 * dx, y - 3 * dy)]
                return (scale * ((depth[r] - depth[l]) * 0.28f + wide * 0.075f) + 0.38f * (fine[r] - fine[l]))
            }
            val dx = slope(1, 0)
            val dy = slope(0, 1)
            val length = sqrt(1f + dx * dx + dy * dy)
            val r = ((0.5f - 0.5f * dx / length) * 255f).roundToInt()
            val g = ((0.5f - 0.5f * dy / length) * 255f).roundToInt()
            val b = (depth[i].coerceIn(0f, 1f) * 255f).roundToInt()
            pixels[i] = (255 shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}

internal interface RakingLightShader {
    fun brush(x: Float, y: Float, strength: Float): Brush
    fun close()
}

internal expect fun rakingLightShader(image: ImageBitmap, relief: PaintingRelief): RakingLightShader?

// Light and height use the same coordinate system, in units of the painting's long side.
internal const val RAKING_LIGHT_SOURCE = """
uniform shader artwork;
uniform shader relief;
uniform float2 artSize;
uniform float2 mapSize;
uniform float2 lamp;
uniform float strength;

float luminance(float2 xy) {
    return dot(artwork.eval(xy).rgb, half3(0.213, 0.715, 0.072));
}

half4 main(float2 xy) {
    half4 original = artwork.eval(xy);
    float2 uv = xy / artSize;
    float3 map = relief.eval(uv * mapSize).rgb;
    float2 nxy = map.rg * 2.0 - 1.0;
    float3 n = float3(nxy, sqrt(max(0.012, 1.0 - dot(nxy, nxy))));
    // Fine-scale pigment relief comes from the original image at native resolution.
    float2 dx = float2(1.4, 0.0);
    float2 dy = float2(0.0, 1.4);
    n.xy -= 0.65 * float2(luminance(xy + dx) - luminance(xy - dx),
                          luminance(xy + dy) - luminance(xy - dy));
    n = normalize(n);
    float2 delta = (lamp - uv) * artSize / max(artSize.x, artSize.y);
    float dz = 0.48 - map.b * 0.20;
    float3 l = normalize(float3(delta, dz));
    float spread = 1.0 / (1.0 + 2.4 * dot(delta, delta));
    float shadow = 0.0;
    for (int i = 1; i <= 7; ++i) {
        float t = float(i * i) * 0.009;
        float obstruction = (relief.eval(mix(uv, lamp, t) * mapSize).b - map.b) * 0.20 - t * dz;
        shadow = max(shadow, smoothstep(0.002, 0.022 + t * 0.04, obstruction));
    }
    float direct = max(0.0, dot(n, l)) * spread * (1.0 - 0.77 * shadow);
    float3 h = normalize(l + float3(0.0, 0.0, 1.0));
    float glint = pow(max(0.0, dot(n, h)), 24.0) * spread * (1.0 - shadow);
    float3 pigment = original.rgb / max(float(original.a), 0.001);
    float3 exposure = float3(0.38, 0.44, 0.53) + direct * float3(1.57, 1.42, 1.19);
    float3 lit = pigment * exposure + (1.0 - pigment) * glint * float3(0.145, 0.122, 0.089);
    return half4(mix(pigment, clamp(lit, 0.0, 1.0), strength) * original.a, original.a);
}
"""

internal class LightingInspection(private val image: ImageBitmap, a: PaintingAnalysis) : InspectionEffect {
    private val shader = rakingLightShader(image, a.relief)
    private val fallback = if (shader == null) ReliefMesh(image, a.relief) else null

    override fun draw(scope: DrawScope, frame: Rect, progress: Float, controls: InspectionControls) {
        val strength = progress * controls.amount
        scope.withTransform({
            translate(frame.left, frame.top)
            scale(frame.width / image.width, frame.height / image.height, Offset.Zero)
        }) {
            if (shader != null) {
                val brush = shader.brush(controls.lightX, controls.lightY, strength)
                drawRect(brush, Offset.Zero, Size(image.width.toFloat(), image.height.toFloat()))
            } else {
                drawImage(image)
                fallback?.draw(this, controls.lightX, controls.lightY, strength)
            }
        }
    }

    override fun close() { shader?.close() }
}

/** Older Android uses a single interpolated mesh of warm light and cool shade over the full-resolution art. */
private class ReliefMesh(image: ImageBitmap, private val relief: PaintingRelief) {
    private val cols = (relief.width / 3).coerceAtLeast(2)
    private val rows = (relief.height / 3).coerceAtLeast(2)
    private val positions = List(cols * rows) { i ->
        Offset((i % cols) * image.width.toFloat() / (cols - 1), (i / cols) * image.height.toFloat() / (rows - 1))
    }
    private val vertices = Vertices(VertexMode.Triangles, positions, positions, List(positions.size) { Color.White },
        buildList {
            for (y in 0 until rows - 1) for (x in 0 until cols - 1) {
                val i = y * cols + x
                add(i); add(i + 1); add(i + cols)
                add(i + cols); add(i + 1); add(i + cols + 1)
            }
        })
    private val aspectX = image.width.toFloat() / max(image.width, image.height)
    private val aspectY = image.height.toFloat() / max(image.width, image.height)
    private val paint = Paint().apply { color = Color.White }

    fun draw(scope: DrawScope, lightX: Float, lightY: Float, strength: Float) {
        for (i in positions.indices) {
            val u = (i % cols).toFloat() / (cols - 1)
            val v = (i / cols).toFloat() / (rows - 1)
            val shade = relief.pixels[(v * (relief.height - 1)).roundToInt() * relief.width + (u * (relief.width - 1)).roundToInt()]
            val nx = (shade ushr 16 and 255) / 127.5f - 1f
            val ny = (shade ushr 8 and 255) / 127.5f - 1f
            val nz = sqrt(max(0.012f, 1f - nx * nx - ny * ny))
            val dx = (lightX - u) * aspectX
            val dy = (lightY - v) * aspectY
            val dz = 0.48f - (shade and 255) * (0.20f / 255f)
            val distance = dx * dx + dy * dy
            val cosine = max(0f, nx * dx + ny * dy + nz * dz) / sqrt(distance + dz * dz)
            val value = 1.72f * cosine / (1f + 2.4f * distance) - 0.69f
            val tint = if (value > 0f) 0xFFE0AA else 0x0B1824
            val opacity = if (value > 0f) value * 0.37f else -value * 0.86f
            vertices.colors[i] = ((opacity * strength * 255f).roundToInt() shl 24) or tint
        }
        scope.drawIntoCanvas { it.drawVertices(vertices, BlendMode.Modulate, paint) }
    }
}
