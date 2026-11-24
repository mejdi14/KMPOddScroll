package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.min

internal enum class InspectionMode(val label: String, val gesture: String) {
    Explode("Explode", "Drag to orbit"),
    Lighting("Lighting", "Move the light across the painting"),
    Microscope("Microscope", "Drag to pan · pinch or scroll to zoom"),
    PointCloud("Point Cloud", "Drag to orbit"),
    Blueprint("Blueprint", "Retrace to follow the contours"),
    Objects("Objects", "Select a cutout to examine it"),
    Colors("Colors", "Select a pigment to locate it"),
}

internal class InspectionControls(mode: InspectionMode?) {
    var yaw by mutableFloatStateOf(if (mode == InspectionMode.PointCloud) -0.38f else 0.38f)
    var pitch by mutableFloatStateOf(-0.14f)
    var amount by mutableFloatStateOf(when (mode) {
        InspectionMode.Microscope -> 0.34f
        InspectionMode.Blueprint -> 0.76f
        else -> 0.70f
    })
    var density by mutableFloatStateOf(0.7f)
    var grain by mutableFloatStateOf(0.52f)
    var lightX by mutableFloatStateOf(0.25f)
    var lightY by mutableFloatStateOf(0.28f)
    var centerX by mutableFloatStateOf(0.5f)
    var centerY by mutableFloatStateOf(0.5f)
    var selected by mutableIntStateOf(-1)
    var focus by mutableFloatStateOf(0f)
}

internal interface InspectionEffect {
    fun draw(scope: DrawScope, frame: Rect, progress: Float, controls: InspectionControls)
    fun hit(frame: Rect, point: Offset, controls: InspectionControls): Int? = null
    fun close() {}
}

internal fun DrawScope.paintImage(
    image: ImageBitmap,
    frame: Rect,
    alpha: Float = 1f,
    quality: FilterQuality = FilterQuality.Medium,
) {
    withTransform({
        translate(frame.left, frame.top)
        scale(frame.width / image.width, frame.height / image.height, Offset.Zero)
    }) {
        drawImage(image, alpha = alpha.coerceIn(0f, 1f), filterQuality = quality)
    }
}

internal fun artFrame(size: Size, image: ImageBitmap, mode: InspectionMode?): Rect {
    val margin = when (mode) {
        InspectionMode.Explode, InspectionMode.PointCloud -> 0.80f
        else -> 0.93f
    }
    val scale = min(size.width / image.width, size.height / image.height) * margin
    val halfW = image.width * scale * 0.5f
    val halfH = image.height * scale * 0.5f
    return Rect(size.width * 0.5f - halfW, size.height * 0.5f - halfH, size.width * 0.5f + halfW, size.height * 0.5f + halfH)
}

internal fun mix(from: Float, to: Float, t: Float) = from + (to - from) * t

internal fun ease(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
