package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap

internal actual fun rakingLightShader(image: ImageBitmap, relief: PaintingRelief): RakingLightShader? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return runCatching { AndroidLight(image, relief) }.getOrNull()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AndroidLight(image: ImageBitmap, relief: PaintingRelief) : RakingLightShader {
    private val shader = RuntimeShader(RAKING_LIGHT_SOURCE)
    private val brush = ShaderBrush(shader)

    init {
        fun texture(bitmap: Bitmap) = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            filterMode = BitmapShader.FILTER_MODE_LINEAR
        }
        val normals = Bitmap.createBitmap(relief.pixels, relief.width, relief.height, Bitmap.Config.ARGB_8888)
        shader.setInputShader("artwork", texture(image.asAndroidBitmap()))
        shader.setInputBuffer("relief", texture(normals))
        shader.setFloatUniform("artSize", image.width.toFloat(), image.height.toFloat())
        shader.setFloatUniform("mapSize", relief.width.toFloat(), relief.height.toFloat())
    }

    override fun brush(x: Float, y: Float, strength: Float): Brush {
        shader.setFloatUniform("lamp", x, y)
        shader.setFloatUniform("strength", strength)
        return brush
    }

    // Android owns the native shaders for as long as a recorded display list needs them.
    override fun close() = Unit
}
