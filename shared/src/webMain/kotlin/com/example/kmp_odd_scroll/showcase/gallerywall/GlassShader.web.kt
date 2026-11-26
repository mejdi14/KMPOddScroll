package com.example.kmp_odd_scroll.showcase.gallerywall

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader

internal actual fun glassShader(): GlassShader? = WebGlass()

private class WebGlass : GlassShader {
    private val shader = RuntimeShaderBuilder(RuntimeEffect.makeForShader(GALLERY_GLASS_SHADER))
    private val images = mutableMapOf<ImageBitmap, Shader>()

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
        edges: Float,
        rim: Float,
    ): Brush {
        shader.child("image", imageShader(image))
        shader.uniform("span", from, to)
        shader.uniform("fresnel", near, middle, far)
        shader.uniform("wall", wall.red, wall.green, wall.blue)
        shader.uniform("pane", centreX, centreY, halfWidth, halfHeight)
        shader.uniform("turn", turn)
        shader.uniform("refraction", refraction)
        shader.uniform("edges", edges)
        shader.uniform("rim", rim)
        return ShaderBrush(shader.makeShader().asComposeShader())
    }

    private fun imageShader(image: ImageBitmap): Shader = images.getOrPut(image) {
        if (images.size >= 16) images.clear()
        Image.makeFromBitmap(image.asSkiaBitmap()).makeShader(FilterTileMode.DECAL, FilterTileMode.DECAL, SamplingMode.LINEAR, null)
    }
}
