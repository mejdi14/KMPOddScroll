package io.github.mejdi14.oddscroll.gallerywall

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

/**
 * The paper fold study's fibre, carried over to AGSL: the paper's grain and
 * the streaks of its making over the painting as it turns. The face the sheet
 * shows is worked out on the mesh, so this has no front and back of its own.
 */
private const val SOURCE = """
uniform shader image;
uniform float2 sheet;
uniform float grain;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

half4 main(float2 xy) {
    float4 color = float4(image.eval(xy));
    float2 uv = xy / sheet;

    float fibre = (hash(uv * 900.0) - 0.5) * 0.06 + (hash(uv * 240.0 + 5.7) - 0.5) * 0.035;
    float streak = (hash(float2(floor(uv.y * 600.0), 3.1)) - 0.5) * 0.02;
    float3 result = color.rgb + (fibre + streak) * grain;

    return half4(half3(clamp(result, 0.0, color.a)), half(color.a));
}
"""

/**
 * AGSL arrived in 33; below that, and if the shader fails to compile at all,
 * the painting turns over without its grain.
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
        width: Float,
        height: Float,
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
        shader.setFloatUniform("sheet", width, height)
        shader.setFloatUniform("grain", grain)
        return shader
    }
}
