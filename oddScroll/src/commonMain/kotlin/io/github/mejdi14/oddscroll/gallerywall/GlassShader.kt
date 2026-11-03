package io.github.mejdi14.oddscroll.gallerywall

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/**
 * A pane of liquid glass as a per-pixel brush, drawn over one piece of a
 * painting in that piece's own pixels — so the glass goes into perspective
 * with it, and its edges bend the very painting they lie on. Not a pass over
 * the finished frame: the glass needs the painting's coordinates, which only
 * the piece being drawn has.
 */
internal interface GlassShader {
    /**
     * Sets the brush up for one piece and hands it back. Everything is in the
     * pixels of [image], the bare painting under the glass. The piece runs from
     * column [from] to [to], and [near], [middle] and [far] are how much of the
     * [wall] the glass throws back at its two ends and halfway. The pane is
     * centred on [centreX], [centreY], [halfWidth] by [halfHeight], its corners
     * rounded [corner], with a band [refraction] wide round its edge that bends
     * the painting under it. [edges] is how much the lit rim along the edge
     * shows, and [rim] is how wide it is; [dispersion] splits the bend into
     * colours, for while the glass is being dragged.
     */
    fun brush(
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
        corner: Float,
        refraction: Float,
        edges: Float,
        rim: Float,
        dispersion: Boolean,
    ): Brush
}

/** The platform's glass, or null to draw it plainly with gradients. */
internal expect fun glassShader(): GlassShader?
