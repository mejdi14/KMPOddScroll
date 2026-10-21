package io.github.mejdi14.oddscroll.gallerywall

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap

/**
 * Liquid glass, a pixel at a time, in the painting's own pixels.
 *
 * - Across the pane it throws back the room, running between the three amounts
 *   worked out for the piece.
 * - Its rounded edge is a bevel that bends what lies under it: the painting is
 *   pulled out towards the edge, most at the very edge and with a ripple and a
 *   swirl along it, and split a little into red, green and blue. Where the bend
 *   reaches past the painting it finds the bare wall.
 * - Along the edge, a lit rim facing the light from the upper left, a glow
 *   inside it, and a little shade where the edge turns side on.
 *
 * The bevel is 1.4 corner radii deep and bends by up to 0.65 of one.
 */
private const val SOURCE = """
uniform shader image;
uniform float2 span;
uniform float3 fresnel;
uniform float3 wall;
uniform float4 pane;
uniform float radius;
uniform float edges;
uniform float rim;

float sdRoundedRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - (halfSize - float2(r));
    return length(max(q, 0.0)) - r + min(max(q.x, q.y), 0.0);
}

float2 gradSdRoundedRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - (halfSize - float2(r));
    if (q.x >= 0.0 || q.y >= 0.0) {
        return sign(p) * normalize(max(q, 0.0) + 1e-5);
    }
    float gx = step(q.y, q.x);
    return sign(p) * float2(gx, 1.0 - gx);
}

half4 main(float2 xy) {
    float2 p = xy - pane.xy;
    float sd = sdRoundedRect(p, pane.zw, radius);
    float cover = clamp(0.5 - sd, 0.0, 1.0);
    if (cover <= 0.0) return half4(0.0);
    float d = max(-sd, 0.0);

    float t = clamp((xy.x - span.x) / max(span.y - span.x, 1.0), 0.0, 1.0);
    float room = t < 0.5 ? mix(fresnel.x, fresnel.y, t * 2.0) : mix(fresnel.y, fresnel.z, t * 2.0 - 1.0);
    float4 color = float4(wall * room, room);
    if (edges <= 0.0) return half4(color * cover);

    float2 n = gradSdRoundedRect(p, pane.zw, min(radius * 1.5, min(pane.z, pane.w)));

    float bevel = radius * 1.4;
    if (d < bevel) {
        float k = d / bevel;
        float amount = radius * 0.65 * pow(1.0 - k, 1.3) * (1.0 + 0.45 * sin(k * 13.0));
        float2 base = (-n + float2(-n.y, n.x) * 0.4 * sin(k * 9.0 + 1.2)) * amount;
        float disp = 0.3 * (1.0 - k);
        float4 r = float4(image.eval(xy + base * (1.0 + disp)));
        float4 g = float4(image.eval(xy + base));
        float4 b = float4(image.eval(xy + base * (1.0 - disp)));
        float3 bent = float3(r.r, g.g, b.b) + wall * (1.0 - g.a);
        float weight = pow(1.0 - k, 0.55) * edges;
        color = float4(bent * weight, weight) + color * (1.0 - weight);
    }

    float band = radius * 0.8;
    if (d < band) {
        float fres = pow(1.0 - d / band, 3.0);
        float facing = dot(n, normalize(float2(-0.55, -0.85)));
        float lip = (1.0 - smoothstep(0.0, rim, d)) * smoothstep(0.0, 0.6, d);
        float strength = 0.3 + 0.7 * max(facing, 0.0) + 0.45 * max(-facing, 0.0);
        float light = clamp(lip * strength + fres * (0.22 + 0.3 * max(facing, 0.0)), 0.0, 1.0) * edges;
        float a = clamp(light + fres * 0.26 * (1.0 - abs(facing)) * edges, 0.0, 1.0);
        color = float4(float3(light), a) + color * (1.0 - a);
    }

    return half4(color * cover);
}
"""

/**
 * AGSL arrived in 33; below that, and if the shader fails to compile at all,
 * the glass is drawn plainly in common code.
 */
internal actual fun glassShader(): GlassShader? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return runCatching { AgslGlass() }.getOrNull()
}

/** Painting bitmaps as shaders the glass can read, kept while they are in use. */
private const val IMAGES_KEPT = 16

/**
 * One shader for every pane. Setting a uniform drops the shader's native
 * instance, so each draw takes its own snapshot and the pieces drawn before it
 * keep theirs.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslGlass : GlassShader {
    private val shader = RuntimeShader(SOURCE)
    private val brush = ShaderBrush(shader)
    private val images = HashMap<ImageBitmap, BitmapShader>()

    override fun brush(
        image: ImageBitmap,
        from: Float,
        to: Float,
        near: Float,
        middle: Float,
        far: Float,
        wall: Color,
        centreX: Float,
        centreY: Float,
        halfWidth: Float,
        halfHeight: Float,
        radius: Float,
        edges: Float,
        rim: Float,
    ): Brush {
        shader.setInputShader("image", imageShader(image))
        shader.setFloatUniform("span", from, to)
        shader.setFloatUniform("fresnel", near, middle, far)
        shader.setFloatUniform("wall", wall.red, wall.green, wall.blue)
        shader.setFloatUniform("pane", centreX, centreY, halfWidth, halfHeight)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("edges", edges)
        shader.setFloatUniform("rim", rim)
        return brush
    }

    /** Clear past the bitmap's edges, so the bend finds nothing there rather than its last row smeared. */
    private fun imageShader(image: ImageBitmap): BitmapShader = images.getOrPut(image) {
        if (images.size >= IMAGES_KEPT) images.clear()
        BitmapShader(image.asAndroidBitmap(), Shader.TileMode.DECAL, Shader.TileMode.DECAL).apply {
            filterMode = BitmapShader.FILTER_MODE_LINEAR
        }
    }
}
