package io.github.mejdi14.oddscroll.gallerywall

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shader

/**
 * The painting as a sheet of paper, a pixel at a time, for the mesh a
 * [PaperFold] turns over: its texture coordinates are [image]'s own pixels.
 */
internal interface PaperShader {
    /**
     * Sets the shader up for one frame and hands it back. The painting is at
     * [left], [top], [width] by [height] in [image]; the halftone has
     * [cellsX] by [cellsY] dots over it and shows [halftone] of the way,
     * inside a ring that draws in to the middle as [reveal] goes from 1 to 0.
     * [grain] is how much of the paper's fibre shows.
     */
    fun shader(
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
    ): Shader
}

/** The platform's paper, or null to turn the painting over without the halftone and grain. */
internal expect fun paperShader(): PaperShader?

/** The platform can draw a textured mesh at all, so a painting can be turned over. */
internal expect val paperFolds: Boolean
