package io.github.mejdi14.oddscroll.cdrack

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap

/**
 * The case lit a pixel at a time, after the reference's own shaders: the
 * studio it reflects, the rods that bend the page and the room through
 * themselves, and the rainbow the disc's tracks throw. Each brush is drawn in
 * the piece's own coordinates under that piece's projection, so it goes into
 * perspective with it, and is handed the camera in those same coordinates.
 */
internal interface CaseShader {
    /**
     * The glass of one layer, drawn over its [height] map in that map's own
     * pixels. [left], [top] and [mmPerPx] put a pixel back in millimetres;
     * [z] is the layer's depth; [eye] is the camera and [toWorld] the frame's
     * axes, column-major, both as [Pose] gives them.
     */
    fun glass(
        height: ImageBitmap,
        left: Float,
        top: Float,
        mmPerPx: Float,
        z: Float,
        eye: FloatArray,
        toWorld: FloatArray,
        thickness: Float,
        overDisc: Boolean,
    ): Brush

    /**
     * The disc, both faces, drawn in its own millimetres about its centre;
     * [art] is the square print laid across its whole diameter.
     */
    fun disc(art: ImageBitmap, eye: FloatArray, toWorld: FloatArray): Brush
}

/** The platform's shading, or null to draw the case from its baked look. */
internal expect fun caseShader(): CaseShader?
