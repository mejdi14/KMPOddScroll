package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import com.example.kmp_odd_scroll.showcase.gallerywall.supportsHardwareMeshes
import kotlin.math.min
import kotlin.math.roundToInt

internal fun InspectionEffect.withMeshFallback(): InspectionEffect =
    if (supportsHardwareMeshes) this else SoftwareMeshEffect(this)

/** Android 24–28 supports drawVertices on a bitmap-backed canvas. Keep one surface per active mode. */
private class SoftwareMeshEffect(private val effect: InspectionEffect) : InspectionEffect {
    private val raster = CanvasDrawScope()
    private var surface: ImageBitmap? = null
    private var canvas: Canvas? = null

    override fun draw(scope: DrawScope, frame: Rect, progress: Float, controls: InspectionControls) {
        val size = scope.size
        if (size.width <= 0f || size.height <= 0f) return
        // Limit the per-frame CPU fill and texture upload on older, high-density screens.
        val reduction = min(1f, 960f / size.maxDimension)
        val width = (size.width * reduction).roundToInt().coerceAtLeast(1)
        val height = (size.height * reduction).roundToInt().coerceAtLeast(1)
        val old = surface
        val bitmap = if (old != null && old.width == width && old.height == height) old else {
            ImageBitmap(width, height).also {
                surface = it
                canvas = Canvas(it)
            }
        }
        raster.draw(scope, scope.layoutDirection, checkNotNull(canvas), size) {
            // Keep layout, hit tests and camera projection in the same screen-space coordinates.
            scale(width / size.width, height / size.height, Offset.Zero) {
                drawRect(Color.Transparent, blendMode = BlendMode.Clear)
                effect.draw(this, frame, progress, controls)
            }
        }
        scope.paintImage(bitmap, Rect(Offset.Zero, size), quality = FilterQuality.Low)
    }

    override fun hit(frame: Rect, point: Offset, controls: InspectionControls) = effect.hit(frame, point, controls)

    override fun close() {
        surface = null
        canvas = null
        effect.close()
    }
}
