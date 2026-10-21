package io.github.mejdi14.oddscroll.gallerywall

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A pane of glass as a per-pixel brush, drawn over one piece of a frame's
 * opening in that piece's own pixels — so whatever lies on the glass is fixed
 * to it and goes into perspective with it. Not a pass over the finished frame:
 * the glass needs its own coordinates, which only the piece being drawn has.
 */
internal interface GlassShader {
    /**
     * Sets the brush up for one piece and hands it back. [originX], [originY]
     * is the opening's top left in the piece's pixels, [paneWidth] by
     * [paneHeight] its size and [unit] the pixels to a card height; [span] is
     * the piece's width. [near], [middle] and [far] are how much of the [wall]
     * the glass throws back at the piece's two ends and halfway. The doorway's
     * glow sits at [doorX], [doorY] with half-sizes [doorWidth] and
     * [doorHeight], in card heights from the opening's top left, [glow] strong.
     * [seed] gives each pane its own marks.
     */
    fun brush(
        originX: Float,
        originY: Float,
        paneWidth: Float,
        paneHeight: Float,
        unit: Float,
        span: Float,
        near: Float,
        middle: Float,
        far: Float,
        wall: Color,
        doorX: Float,
        doorY: Float,
        doorWidth: Float,
        doorHeight: Float,
        glow: Float,
        seed: Int,
    ): Brush
}

/** The platform's glass, or null to draw it plainly with gradients. */
internal expect fun glassShader(): GlassShader?
