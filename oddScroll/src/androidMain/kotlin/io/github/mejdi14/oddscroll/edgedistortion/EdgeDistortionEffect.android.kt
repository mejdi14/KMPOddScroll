package io.github.mejdi14.oddscroll.edgedistortion

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The reference effect, as the reference does it: one fragment shader over the
 * whole rendered frame.
 *
 * The barrel term and the channel offsets are a straight port — `r` is the
 * max-norm so the falloff is a box tracking the frame's own borders rather than
 * a circle inscribed in them, and the three channels are sampled at
 * `warpUv + offset`, `warpUv`, `warpUv - offset`.
 *
 * Only `normalize(pos)` needed guarding: at dead centre it is a divide by zero,
 * and the NaN survives the multiply by an `edgeStrength` of zero.
 */
private const val SOURCE = """
uniform shader content;
uniform float2 uResolution;
uniform float uStrength;
uniform float uAberration;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uResolution;
    float2 pos = uv * 2.0 - 1.0;

    // Box distance to the nearest border, so the sides distort as much as the
    // corners do.
    float r = max(abs(pos.x), abs(pos.y));

    // The inner 75% stays flat; the outer quarter bends, reaching full strength
    // just past the border so it is still climbing when it gets there.
    float edgeStrength = smoothstep(0.75, 1.05, r);

    float k = uStrength * edgeStrength;
    float2 warpUv = (pos / (1.0 + k * r * r)) * 0.5 + 0.5;

    float len = length(pos);
    float2 dir = len > 0.0001 ? pos / len : float2(0.0, 0.0);
    float2 offset = dir * (edgeStrength * uAberration);

    half red   = content.eval((warpUv + offset) * uResolution).r;
    half green = content.eval(warpUv * uResolution).g;
    half blue  = content.eval((warpUv - offset) * uResolution).b;
    return half4(red, green, blue, 1.0);
}
"""

@Composable
internal actual fun Modifier.edgeDistortion(strength: Float, aberration: Float): Modifier {
    // Remembered unconditionally, so the slot table is the same shape whatever
    // the device turns out to support. AGSL arrived in 33; below that, and if
    // the shader fails to compile at all, the grid simply stays flat.
    val shader = remember {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            null
        } else {
            @Suppress("NewApi")
            runCatching { RuntimeShader(SOURCE) }.getOrNull()
        }
    }
    if (shader == null || (strength <= 0f && aberration <= 0f)) return this

    return graphicsLayer {
        if (size.width <= 0f || size.height <= 0f) return@graphicsLayer
        @Suppress("NewApi")
        run {
            shader.setFloatUniform("uResolution", size.width, size.height)
            shader.setFloatUniform("uStrength", strength)
            shader.setFloatUniform("uAberration", aberration)
            renderEffect = RenderEffect
                .createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
        }
    }
}
