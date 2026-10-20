package io.github.mejdi14.oddscroll.flexcarousel

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow

/**
 * The glass, as one shader pass over the finished row.
 *
 * A superellipse is placed over the frame — `roundness` picks the exponent, so
 * it runs from an ellipse to a hard rectangle — and the signed distance to its
 * edge is divided by the length of the field's gradient, which turns an
 * arbitrary exponent into something measured in pixels. That distance drives a
 * band either side of the edge: a smootherstep `ramp` across it carries the
 * row along, and the bell-shaped `slope`, which is the ramp's own derivative,
 * carries the twist and the colour split. Using the derivative for the second
 * pair is what keeps them inside the first — the colour never fringes anywhere
 * the row is not already being dragged.
 *
 * Two things could not come over from the WebGL original. It samples with
 * `textureGrad`, feeding it screen-space derivatives so the glass does not
 * alias where it compresses hardest; AGSL has neither `dFdx` nor `textureGrad`,
 * so the sample is plain. And its scene lives in a render target it can read
 * outside its bounds; here the shader reads the layer, which is the frame
 * itself, so the drag is clamped to it.
 */
private const val SOURCE = """
uniform shader content;
uniform float2 uResolution;
uniform float2 uCenter;
uniform float2 uHalf;
uniform float uAngle;
uniform float uExponent;
uniform float uInner;
uniform float uOuter;
uniform float uFlow;
uniform float uCurl;
uniform float uDispersion;
uniform float uStrength;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uResolution;

    // Flipped to y-up, so the geometry below is the original's arithmetic
    // unchanged; the drift is flipped back before it is used to sample.
    float2 rel = float2(fragCoord.x - uCenter.x, uCenter.y - fragCoord.y);
    float ca = cos(uAngle);
    float sa = sin(uAngle);
    float2 local = float2(ca * rel.x + sa * rel.y, -sa * rel.x + ca * rel.y);

    float2 k = max(abs(local) / uHalf, float2(1e-5));
    float nd = pow(pow(k.x, uExponent) + pow(k.y, uExponent), 1.0 / uExponent);
    float2 grad = pow(k, float2(uExponent - 1.0)) * sign(local) / uHalf * pow(nd, 1.0 - uExponent);
    float glen = max(length(grad), 1e-6);
    float edge = (nd - 1.0) / glen;
    float2 outward = grad / glen;
    float2 normal = float2(ca * outward.x - sa * outward.y, sa * outward.x + ca * outward.y);
    float2 along = float2(-normal.y, normal.x);

    float t = clamp((edge + uInner) / (uInner + uOuter), 0.0, 1.0);
    float ramp = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    float slope = 16.0 * t * t * (1.0 - t) * (1.0 - t);

    float reachX = rel.x / (uResolution.x * 0.5);
    float side = smoothstep(0.02, 0.3, abs(reachX)) * (uCurl == 0.0 ? sign(reachX) : uCurl);
    float lift = ramp * side * uFlow * uStrength;
    float2 swirl = along * along.y * side * slope * uFlow * uStrength * 0.35;
    float2 drift = float2(0.0, -lift) - swirl;

    float2 shifted = uv + float2(drift.x, -drift.y) / uResolution;
    float4 color = float4(content.eval(shifted * uResolution));

    float2 spread = float2(0.0, -(side * slope * uFlow * uStrength)) / uResolution * uDispersion;
    float spreadPx = length(spread * uResolution);
    if (color.a > 0.002 && spreadPx > 0.25) {
        float3 base = color.rgb / color.a;
        float3 sumColor = float3(0.0);
        float3 sumWeight = float3(0.0);
        for (int i = 0; i < 12; i++) {
            float s = (float(i) + 0.5) / 12.0;
            float4 c = float4(content.eval((shifted + spread * (s - 0.5)) * uResolution));
            float3 w = max(float3(1.0) - abs(float3(s) - float3(0.15, 0.5, 0.85)) * 2.6, float3(0.0)) * c.a;
            sumColor += c.rgb * (w / max(c.a, 0.002));
            sumWeight += w;
        }
        float3 split = mix(base, sumColor / max(sumWeight, float3(1e-4)), clamp(sumWeight * 2.0, 0.0, 1.0));
        color.rgb = mix(color.rgb, clamp(split, 0.0, 1.0) * color.a, smoothstep(0.25, 1.5, spreadPx));
    }
    return half4(color);
}
"""

@Composable
internal actual fun Modifier.liquidLens(lens: FlexLens, strength: () -> Float): Modifier {
    // Remembered unconditionally, so the slot table is the same shape whatever
    // the device turns out to support. AGSL arrived in 33; below that, and if
    // the shader fails to compile at all, the row runs undistorted.
    val shader = remember {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            null
        } else {
            @Suppress("NewApi")
            runCatching { RuntimeShader(SOURCE) }.getOrNull()
        }
    }
    if (shader == null || lens.bend <= 0f) return this

    return graphicsLayer {
        val w = size.width
        val h = size.height
        // Read in here, not in composition: it moves every frame while a card
        // opens, and reading it out there would rebuild the carousel each time.
        val amount = strength()
        if (w <= 0f || h <= 0f || amount <= 0.001f) {
            renderEffect = null
            return@graphicsLayer
        }

        // The width is measured against the frame's width, so the two vertical
        // edges of the lens always land inside it and the row runs out through
        // them. The height is measured against the frame's LONG side: on a
        // landscape frame that is the width, which is what the reference does,
        // but on a portrait one measuring against the width would leave the
        // lens shorter than the frame and drag a band straight across the
        // middle of the picture instead of down its sides.
        val halfW = max(lens.width * w / 2f, 1f)
        val halfH = max(lens.height * max(w, h) / 2f, 1f)
        val inner = max(4f, lens.reach * (halfW + halfH) * 0.5f)

        @Suppress("NewApi")
        run {
            shader.setFloatUniform("uResolution", w, h)
            shader.setFloatUniform("uCenter", w / 2f, h / 2f)
            shader.setFloatUniform("uHalf", halfW, halfH)
            shader.setFloatUniform("uAngle", (lens.tilt * PI / 180.0).toFloat())
            shader.setFloatUniform(
                "uExponent",
                2f + (1f - lens.roundness.coerceIn(0f, 1f)).toDouble().pow(1.5).toFloat() * 10f,
            )
            shader.setFloatUniform("uInner", inner)
            shader.setFloatUniform("uOuter", inner * 1.6f)
            shader.setFloatUniform("uFlow", lens.bend * (halfW + halfH) * 0.45f)
            shader.setFloatUniform(
                "uCurl",
                when (lens.curl) {
                    FlexCurl.Rise -> 1f
                    FlexCurl.Fall -> -1f
                    FlexCurl.Twist -> 0f
                },
            )
            shader.setFloatUniform("uDispersion", lens.dispersion * 0.12f)
            shader.setFloatUniform("uStrength", amount)
            renderEffect = RenderEffect
                .createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
        }
    }
}
