package com.example.kmp_odd_scroll.showcase.flexcarousel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow

@Composable
internal actual fun Modifier.liquidLens(lens: FlexLens, strength: () -> Float): Modifier {
    if (lens.bend <= 0f) return this
    val shader = remember { RuntimeShaderBuilder(RuntimeEffect.makeForShader(LIQUID_LENS_SHADER)) }
    return graphicsLayer {
        val w = size.width
        val h = size.height
        val amount = strength()
        if (w <= 0f || h <= 0f || amount <= 0.001f) {
            renderEffect = null
            return@graphicsLayer
        }

        val halfW = max(lens.width * w / 2f, 1f)
        val halfH = max(lens.height * max(w, h) / 2f, 1f)
        val inner = max(4f, lens.reach * (halfW + halfH) * 0.5f)
        shader.uniform("uResolution", w, h)
        shader.uniform("uCenter", w / 2f, h / 2f)
        shader.uniform("uHalf", halfW, halfH)
        shader.uniform("uAngle", (lens.tilt * PI / 180.0).toFloat())
        shader.uniform("uExponent", 2f + (1f - lens.roundness.coerceIn(0f, 1f)).pow(1.5f) * 10f)
        shader.uniform("uInner", inner)
        shader.uniform("uOuter", inner * 1.6f)
        shader.uniform("uFlow", lens.bend * (halfW + halfH) * 0.45f)
        shader.uniform("uCurl", when (lens.curl) {
            FlexCurl.Rise -> 1f
            FlexCurl.Fall -> -1f
            FlexCurl.Twist -> 0f
        })
        shader.uniform("uDispersion", lens.dispersion * 0.12f)
        shader.uniform("uStrength", amount)
        renderEffect = ImageFilter.makeRuntimeShader(shader, "content", null).asComposeRenderEffect()
    }
}
