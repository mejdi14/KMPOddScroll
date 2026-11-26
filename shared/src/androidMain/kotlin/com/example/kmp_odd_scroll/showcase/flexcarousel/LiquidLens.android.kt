package com.example.kmp_odd_scroll.showcase.flexcarousel

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
            runCatching { RuntimeShader(LIQUID_LENS_SHADER) }.getOrNull()
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
