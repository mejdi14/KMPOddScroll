package com.example.kmp_odd_scroll.showcase.gallerywall

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shader

/**
 * The painting as a sheet of paper, a pixel at a time, for the mesh a
 * [PaperFold] turns over: its texture coordinates are [image]'s own pixels.
 */
internal interface PaperShader {
    /**
     * Sets the shader up for one frame and hands it back. Each side of the
     * painting is [width] by [height] in [image], which sets the fibre's
     * scale; [grain] is how much of it shows.
     */
    fun shader(
        image: ImageBitmap,
        width: Float,
        height: Float,
        grain: Float,
    ): Shader
}

/** The platform's paper, or null to turn the painting over without the grain. */
internal expect fun paperShader(): PaperShader?

/** The platform can draw a textured mesh at all, so a painting can be turned over. */
internal expect val paperFolds: Boolean
