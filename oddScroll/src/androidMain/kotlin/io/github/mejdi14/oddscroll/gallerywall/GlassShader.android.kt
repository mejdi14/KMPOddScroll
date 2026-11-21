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

/*
 * The refraction, dispersion and highlight below are adapted from Kyant's
 * AndroidLiquidGlass (Copyright 2025 Kyant, Apache License 2.0), by way of
 * https://sitong-zhang.github.io/apple-liquid-glass-guide/ — its Card recipe
 * with the refraction band widened. Changes: one pass in the painting's own
 * pixels rather than a render effect over a backdrop layer, so the pane goes
 * into perspective with the painting; square corners, the bend still turning
 * round them; the light split at rest as well as on the move; the room thrown
 * back on top.
 */

private const val ROUNDED_RECT = """
float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0) + 1e-5);
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}
"""

/**
 * The painting bent in to [bent], split along the bend into seven colours, most
 * towards the corners: [shift] is the bend itself, [p] the pixel from the
 * pane's middle.
 */
private const val BEND = """
float4 bend(float2 bent, float2 shift, float2 p, float2 halfSize) {
    float2 dispersed = shift * ((p.x * p.y) / (halfSize.x * halfSize.y));
    half4 color = half4(0.0);

    half4 red = image.eval(bent + dispersed);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = image.eval(bent + dispersed * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = image.eval(bent + dispersed * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = image.eval(bent);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = image.eval(bent - dispersed * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = image.eval(bent - dispersed * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = image.eval(bent - dispersed);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return float4(color);
}
"""

/**
 * Liquid glass, a pixel at a time, in the painting's own pixels.
 *
 * - Round the square pane's edge, a band [refraction] wide bends the painting
 *   in towards the middle along a quarter circle, by as much as the band is
 *   wide at the very edge, and splits it into colours. The bend starts turning
 *   round each corner [turn] from it, so it does not crease along the
 *   diagonals. Where it reaches past the painting it finds the bare wall.
 * - All of it through 1.5x vibrancy, then a tenth of white.
 * - Over that, the room it throws back, running between the three amounts
 *   worked out for the piece.
 */
private const val SOURCE = """
uniform shader image;
uniform float2 span;
uniform float3 fresnel;
uniform float3 wall;
uniform float4 pane;
uniform float turn;
uniform float refraction;

$ROUNDED_RECT
$BEND

half4 main(float2 xy) {
    float2 p = xy - pane.xy;
    float2 halfSize = pane.zw;
    float sd = sdRoundedRect(p, halfSize, 0.0);
    float cover = clamp(0.5 - sd, 0.0, 1.0);
    if (cover <= 0.0) return half4(0.0);

    float2 grad = normalize(gradSdRoundedRect(p, halfSize, turn));

    float4 color;
    if (-sd >= refraction) {
        color = float4(image.eval(xy));
    } else {
        float d = circleMap(1.0 - -min(sd, 0.0) / refraction) * -refraction;
        color = bend(xy + d * grad, d * grad, p, halfSize);
    }
    color += float4(wall, 1.0) * (1.0 - color.a);

    float lum = dot(color.rgb, float3(0.213, 0.715, 0.072));
    color.rgb = clamp(mix(float3(lum), color.rgb, 1.5), 0.0, color.a);
    color = color * 0.9 + float4(0.1);

    float t = clamp((xy.x - span.x) / max(span.y - span.x, 1.0), 0.0, 1.0);
    float room = t < 0.5 ? mix(fresnel.x, fresnel.y, t * 2.0) : mix(fresnel.y, fresnel.z, t * 2.0 - 1.0);
    color = float4(wall * room, room) + color * (1.0 - room);

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
        turn: Float,
        refraction: Float,
    ): Brush {
        shader.setInputShader("image", imageShader(image))
        shader.setFloatUniform("span", from, to)
        shader.setFloatUniform("fresnel", near, middle, far)
        shader.setFloatUniform("wall", wall.red, wall.green, wall.blue)
        shader.setFloatUniform("pane", centreX, centreY, halfWidth, halfHeight)
        shader.setFloatUniform("turn", turn)
        shader.setFloatUniform("refraction", refraction)
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
