package com.example.kmp_odd_scroll.showcase.gallerywall

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
    private val shader = RuntimeShader(GALLERY_GLASS_SHADER)
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
        edges: Float,
        rim: Float,
    ): Brush {
        shader.setInputShader("image", imageShader(image))
        shader.setFloatUniform("span", from, to)
        shader.setFloatUniform("fresnel", near, middle, far)
        shader.setFloatUniform("wall", wall.red, wall.green, wall.blue)
        shader.setFloatUniform("pane", centreX, centreY, halfWidth, halfHeight)
        shader.setFloatUniform("turn", turn)
        shader.setFloatUniform("refraction", refraction)
        shader.setFloatUniform("edges", edges)
        shader.setFloatUniform("rim", rim)
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
