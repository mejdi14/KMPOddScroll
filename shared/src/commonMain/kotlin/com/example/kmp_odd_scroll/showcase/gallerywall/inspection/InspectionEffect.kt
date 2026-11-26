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
import kotlin.math.max
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

/** The most of a stage of [size] the painting may cover. */
private const val FIT = 0.93f

/** The tools that orbit the painting stand it this much further back, to leave room to turn it. */
private const val ORBIT_BACK = 0.80f / FIT

/**
 * Where the painting lies in a stage of [size]: where it [hung] on the wall,
 * in the stage's coordinates, so the study opens on it as it is, without a
 * zoom. Only if that is more than the stage can hold is it brought down to
 * fit, and then moved in from the edges.
 */
internal fun restingFrame(size: Size, image: ImageBitmap, hung: Rect): Rect {
    val fit = min(size.width / image.width, size.height / image.height) * FIT
    val w0 = if (hung.isEmpty) image.width * fit else hung.width
    val h0 = if (hung.isEmpty) image.height * fit else hung.height
    val k = min(1f, min(image.width * fit / w0, image.height * fit / h0))
    val halfW = w0 * k * 0.5f
    val halfH = h0 * k * 0.5f
    val centre = if (hung.isEmpty) Offset(size.width * 0.5f, size.height * 0.5f) else hung.center
    val x = centre.x.coerceIn(halfW, max(halfW, size.width - halfW))
    val y = centre.y.coerceIn(halfH, max(halfH, size.height - halfH))
    return Rect(x - halfW, y - halfH, x + halfW, y + halfH)
}

/** The painting at [rest] as tool [mode] holds it. */
internal fun toolFrame(rest: Rect, mode: InspectionMode?): Rect {
    val k = when (mode) {
        InspectionMode.Explode, InspectionMode.PointCloud -> ORBIT_BACK
        else -> return rest
    }
    val halfW = rest.width * k * 0.5f
    val halfH = rest.height * k * 0.5f
    return Rect(rest.center.x - halfW, rest.center.y - halfH, rest.center.x + halfW, rest.center.y + halfH)
}

internal fun mix(from: Float, to: Float, t: Float) = from + (to - from) * t

internal fun ease(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
