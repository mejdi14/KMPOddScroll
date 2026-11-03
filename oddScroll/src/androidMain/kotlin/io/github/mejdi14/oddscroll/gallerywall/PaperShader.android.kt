package io.github.mejdi14.oddscroll.gallerywall

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

/**
 * The paper fold study's fragment shader, carried over to AGSL: rotated
 * halftone dots, larger where the painting is darker, over a warm paper,
 * inside a ring that draws in to the middle; then the paper's fibre and the
 * streaks of its making. The face the sheet shows is worked out on the mesh,
 * so this has no front and back of its own.
 */
private const val SOURCE = """
uniform shader image;
uniform float4 sheet;
uniform float2 cells;
uniform float halftone;
uniform float reveal;
uniform float grain;

const float3 PAPER = float3(0.95, 0.92, 0.88);
const float3 WARM = float3(1.02, 0.98, 0.92);

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

half4 main(float2 xy) {
    float4 color = float4(image.eval(xy));
    float2 uv = (xy - sheet.xy) / sheet.zw;
    float luma = dot(color.rgb, float3(0.2126, 0.7152, 0.0722));

    float a = 0.35;
    float2 at = (uv - 0.5) * cells;
    float2 cell = float2(cos(a) * at.x + sin(a) * at.y, -sin(a) * at.x + cos(a) * at.y) + 0.5 * cells;
    float dotDistance = length(fract(cell) - 0.5);
    float dotRadius = mix(0.26, 0.68, 1.0 - luma);
    float dotMask = 1.0 - smoothstep(dotRadius - 0.02, dotRadius, dotDistance);

    float eased = reveal < 0.5 ? 2.0 * reveal * reveal : 1.0 - 2.0 * (1.0 - reveal) * (1.0 - reveal);
    float radius = eased * 1.4;
    float noise = (hash((uv - 0.5) * 60.0) - 0.5) * 0.04;
    float ring = 1.0 - smoothstep(radius - 0.4, radius, distance(uv, float2(0.5)) + noise);
    float blend = halftone * ring;

    float3 paper = mix(color.rgb, PAPER, 0.85);
    float3 dots = mix(paper, color.rgb, dotMask);
    float3 result = mix(color.rgb, dots, blend);

    float fibre = (hash(uv * 900.0) - 0.5) * 0.06 + (hash(uv * 240.0 + 5.7) - 0.5) * 0.035;
    float streak = (hash(float2(floor(uv.y * 600.0), 3.1)) - 0.5) * 0.02;
    result += (fibre + streak) * grain;
    result = mix(result, result * WARM, blend * 0.4);

    return half4(half3(clamp(result, 0.0, color.a)), half(color.a));
}
"""

/**
 * AGSL arrived in 33; below that, and if the shader fails to compile at all,
 * the painting turns over plainly.
 */
internal actual fun paperShader(): PaperShader? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return runCatching { AgslPaper() }.getOrNull()
}

/** Hardware-accelerated canvases draw a mesh from 29; below that it draws nothing. */
internal actual val paperFolds: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslPaper : PaperShader {
    private val shader = RuntimeShader(SOURCE)
    private var imageFor: ImageBitmap? = null

    override fun shader(
        image: ImageBitmap,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        cellsX: Float,
        cellsY: Float,
        halftone: Float,
        reveal: Float,
        grain: Float,
    ): Shader {
        if (imageFor !== image) {
            imageFor = image
            shader.setInputShader(
                "image",
                BitmapShader(image.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    filterMode = BitmapShader.FILTER_MODE_LINEAR
                },
            )
        }
        shader.setFloatUniform("sheet", left, top, width, height)
        shader.setFloatUniform("cells", cellsX, cellsY)
        shader.setFloatUniform("halftone", halftone)
        shader.setFloatUniform("reveal", reveal)
        shader.setFloatUniform("grain", grain)
        return shader
    }
}
