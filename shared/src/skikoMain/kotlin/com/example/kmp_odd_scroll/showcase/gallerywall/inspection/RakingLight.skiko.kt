package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import org.jetbrains.skia.impl.use

internal actual fun rakingLightShader(image: ImageBitmap, relief: PaintingRelief): RakingLightShader? =
    runCatching { SkiaLight(image, relief) }.getOrNull()

private class SkiaLight(image: ImageBitmap, relief: PaintingRelief) : RakingLightShader {
    private val builder = RuntimeEffect.makeForShader(RAKING_LIGHT_SOURCE).use { RuntimeShaderBuilder(it) }
    private var last: Shader? = null
    private var cachedBrush: Brush? = null
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var lastStrength = Float.NaN

    init {
        fun bind(name: String, source: Image) = source.use {
            it.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR, null).use { shader ->
                builder.child(name, shader)
            }
        }
        bind("artwork", Image.makeFromBitmap(image.asSkiaBitmap()))
        val bytes = ByteArray(relief.pixels.size * 4)
        for (i in relief.pixels.indices) {
            val argb = relief.pixels[i]
            bytes[4 * i] = (argb ushr 16).toByte()
            bytes[4 * i + 1] = (argb ushr 8).toByte()
            bytes[4 * i + 2] = argb.toByte()
            bytes[4 * i + 3] = 255.toByte()
        }
        val info = ImageInfo(relief.width, relief.height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
        bind("relief", Image.makeRaster(info, bytes, relief.width * 4))
        builder.uniform("artSize", image.width.toFloat(), image.height.toFloat())
        builder.uniform("mapSize", relief.width.toFloat(), relief.height.toFloat())
    }

    override fun brush(x: Float, y: Float, strength: Float): Brush {
        cachedBrush?.let { if (x == lastX && y == lastY && strength == lastStrength) return it }
        builder.uniform("lamp", x, y)
        builder.uniform("strength", strength)
        last?.close()
        val shader = builder.makeShader()
        last = shader
        lastX = x
        lastY = y
        lastStrength = strength
        return ShaderBrush(shader.asComposeShader()).also { cachedBrush = it }
    }

    override fun close() {
        cachedBrush = null
        last?.close()
        last = null
        builder.close()
    }
}
