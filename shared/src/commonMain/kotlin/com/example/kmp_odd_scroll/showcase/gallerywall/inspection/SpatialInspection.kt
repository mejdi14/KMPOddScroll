package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Cutouts sample the original painting through its registered subject contours. */
internal class SpatialInspection(
    private val image: ImageBitmap,
    private val a: PaintingAnalysis,
    private val objectsMode: Boolean,
) : InspectionEffect {
    private val grid = Rect(0f, 0f, a.width.toFloat(), a.height.toFloat())
    private val originalSize = IntSize(image.width, image.height)
    private val gridSize = IntSize(a.width, a.height)
    private val matrix = Matrix()
    private val edgeTint = ColorFilter.tint(Color(0xff302823))
    private val pieces = if (objectsMode) emptyList() else a.slices.map { slice ->
        DepthPiece(slice.depth, croppedTexture(slice.bounds, slice.outline, slice.underpaint), slice.bounds)
    }
    private val specimens = if (objectsMode) a.objects.map { Specimen(it) } else emptyList()
    private val bySourceX = specimens.indices.sortedBy { specimens[it].region.anchor.x }
    private val leftCount = (bySourceX.size + 1) / 2 -
        if (bySourceX.size % 2 == 1 && specimens[bySourceX[bySourceX.size / 2]].region.anchor.x >= grid.center.x) 1 else 0
    private val leftSpecimens = bySourceX.take(leftCount)
    private val rightSpecimens = bySourceX.drop(leftSpecimens.size)
    private var sourcePose = Pose(Offset.Zero, 1f)
    private var lastFrame = Rect.Zero
    private var lastSelected = -1

    override fun draw(scope: DrawScope, frame: Rect, progress: Float, controls: InspectionControls) {
        if (frame.width <= 0f || frame.height <= 0f) return
        if (objectsMode) drawObjects(scope, frame, progress, controls) else drawLayers(scope, frame, progress, controls)
    }

    /** Both ends of a leader are interactive. A tap in a cutout's transparent interior passes through. */
    override fun hit(frame: Rect, point: Offset, controls: InspectionControls): Int? {
        if (!objectsMode || frame != lastFrame || controls.selected != lastSelected) return null
        fun contains(index: Int, pose: Pose): Boolean {
            val local = pose.unmap(point)
            return specimens[index].region.contains(local)
        }
        if (lastSelected in specimens.indices && contains(lastSelected, specimens[lastSelected].pose)) return lastSelected
        for (i in specimens.indices.reversed()) if (contains(i, specimens[i].pose)) return i
        for (i in specimens.indices.reversed()) if (contains(i, sourcePose)) return i
        return null
    }

    private fun drawLayers(scope: DrawScope, frame: Rect, progress: Float, controls: InspectionControls) {
        val p = progress.coerceIn(0f, 1f)
        if (p < 0.001f || pieces.isEmpty()) {
            scope.paintImage(image, frame)
            return
        }
        val yaw = controls.yaw * p + sin(p * 3.14159f) * 0.08f
        val pitch = controls.pitch * p
        val spacing = controls.amount.coerceIn(0f, 1f)
        val short = min(frame.width, frame.height)
        val separation = short * 1.25f * p * spacing
        val size = 1f - p * (0.07f + 0.14f * spacing)
        for (piece in pieces) {
            val layerZ = (piece.depth - 0.5f) * separation
            val thickness = short * 0.0055f * p
            if (p > 0.05f) {
                // A thin dark return, visible on the edges facing away from the camera.
                camera(frame, yaw, pitch, size, layerZ - thickness)
                drawPiece(scope, piece, edgeTint)
            }
            camera(frame, yaw, pitch, size, layerZ)
            drawPiece(scope, piece, null)
        }
        // At registration the original pixels also close antialiasing seams between adjoining masks.
        if (p < 0.15f) scope.paintImage(image, frame, 1f - p / 0.15f)
    }

    /** Compose uses row vectors. The last column implements division by eye distance after orbit. */
    private fun camera(frame: Rect, yaw: Float, pitch: Float, scale: Float, depth: Float) {
        val stepX = frame.width / a.width * scale
        val stepY = frame.height / a.height * scale
        val left = -frame.width * scale / 2f
        val top = -frame.height * scale / 2f
        val eye = max(frame.width, frame.height) * 2.75f
        val cy = cos(yaw)
        val sy = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)
        val x0 = left * cy + depth * sy
        val y0 = top * cp + left * sy * sp - depth * cy * sp
        val w0 = 1f - ((depth * cy - left * sy) * cp + top * sp) / eye
        val dwX = stepX * sy * cp / eye
        val dwY = -stepY * sp / eye
        val cx = frame.center.x
        val centerY = frame.center.y
        matrix.reset()
        matrix[0, 0] = stepX * cy + cx * dwX
        matrix[0, 1] = stepX * sy * sp + centerY * dwX
        matrix[0, 3] = dwX
        matrix[1, 0] = cx * dwY
        matrix[1, 1] = stepY * cp + centerY * dwY
        matrix[1, 3] = dwY
        matrix[3, 0] = x0 + cx * w0
        matrix[3, 1] = y0 + centerY * w0
        matrix[3, 3] = w0
    }

    private class DepthPiece(val depth: Float, val bitmap: ImageBitmap, val bounds: Rect)

    private fun drawPiece(scope: DrawScope, piece: DepthPiece, tint: ColorFilter?) {
        scope.withTransform({
            transform(matrix)
            translate(piece.bounds.left, piece.bounds.top)
            scale(piece.bounds.width / piece.bitmap.width, piece.bounds.height / piece.bitmap.height, Offset.Zero)
        }) {
            drawImage(piece.bitmap, Offset.Zero, colorFilter = tint)
        }
    }

    /** One compact premasked texture per plane; no clipping or mask construction in the orbit loop. */
    private fun croppedTexture(bounds: Rect, path: Path, underpaint: ImageBitmap? = null): ImageBitmap {
        val texels = min(1f, 1280f / max(image.width, image.height))
        val w = max(1, (bounds.width / a.width * image.width * texels).roundToInt())
        val h = max(1, (bounds.height / a.height * image.height * texels).roundToInt())
        val out = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(out), Size(w.toFloat(), h.toFloat())) {
            withTransform({
                scale(w / bounds.width, h / bounds.height, Offset.Zero)
                translate(-bounds.left, -bounds.top)
            }) {
                if (underpaint != null) paintImage(underpaint, grid)
                clipPath(path) { drawSource() }
            }
        }
        return out
    }

    private data class Pose(val origin: Offset, val scaleX: Float, val scaleY: Float = scaleX) {
        fun map(p: Offset) = origin + Offset(p.x * scaleX, p.y * scaleY)
        fun unmap(p: Offset) = Offset((p.x - origin.x) / scaleX, (p.y - origin.y) / scaleY)
        fun toward(other: Pose, t: Float) = Pose(
            origin + (other.origin - origin) * t,
            mix(scaleX, other.scaleX, t),
            mix(scaleY, other.scaleY, t),
        )
    }

    private class Specimen(val region: InspectionRegion) {
        var pose = Pose(Offset.Zero, 1f)
        val trace = PathMeasure().apply { setPath(region.outline, true) }
        val segment = Path()
    }

    private fun drawObjects(scope: DrawScope, frame: Rect, progress: Float, controls: InspectionControls) {
        if (specimens.isEmpty()) {
            scope.paintImage(image, frame)
            return
        }
        val p = progress.coerceIn(0f, 1f)
        val separate = ease((p - 0.17f) / 0.83f)
        sourcePose = Pose(frame.topLeft, frame.width / grid.width, frame.height / grid.height)
        lastFrame = frame
        lastSelected = controls.selected
        val selected = controls.selected.takeIf { it in specimens.indices }
        val focus = if (selected == null) 0f else ease(controls.focus)
        val trace = ease(p / 0.48f)
        scope.paintImage(image, frame)
        scope.at(sourcePose) {
            for ((i, s) in specimens.withIndex()) {
                val ink = if (i == selected) Color(0xffffe6b0) else Color(0xfff4e7ce)
                drawTrace(s, trace, ink, sourcePose.scaleX)
            }
        }

        // Keep the outer controls clear when the side space can accommodate both.
        val viewportWidth = scope.size.width
        val desiredInset = (viewportWidth * 0.025f).coerceIn(12f * scope.density, 36f * scope.density)
        val rail = (viewportWidth * 0.14f).coerceIn(102f * scope.density, 156f * scope.density)
        val sideSpace = min(frame.left, viewportWidth - frame.right).coerceAtLeast(0f)
        val inset = min(desiredInset + rail, sideSpace * 0.58f)
        val gap = min(sideSpace * 0.12f, min(frame.width, frame.height) * 0.048f)
        val left = Rect(inset, frame.top, frame.left - gap, frame.bottom)
        val right = Rect(frame.right + gap, frame.top, viewportWidth - inset, frame.bottom)
        layoutSpecimens(leftSpecimens, left, selected, focus, controls.amount, separate)
        layoutSpecimens(rightSpecimens, right, selected, focus, controls.amount, separate)

        if (separate <= 0f) return
        for ((i, s) in specimens.withIndex()) {
            val from = sourcePose.map(s.region.anchor)
            val target = s.pose.map(s.region.anchor)
            val lineAlpha = (if (selected == null || i == selected) 0.59f else 0.16f) * separate
            val bend = (from.x + target.x) / 2f
            val path = s.segment.apply {
                reset()
                moveTo(from.x, from.y)
                cubicTo(bend, from.y, bend, target.y, target.x, target.y)
            }
            scope.drawPath(path, Color(0xffb5a07b), alpha = lineAlpha, style = Stroke(scope.density * 0.9f))
            scope.drawCircle(Color(0xfff5e5c7), radius = scope.density * 2.2f, center = from, alpha = lineAlpha)
        }
        for ((i, s) in specimens.withIndex()) if (i != selected) {
            drawSpecimen(scope, s, separate, 1f - focus * 0.46f)
        }
        if (selected != null) drawSpecimen(scope, specimens[selected], separate, 1f)
    }

    private fun layoutSpecimens(
        indices: List<Int>,
        tray: Rect,
        selected: Int?,
        focus: Float,
        magnification: Float,
        separate: Float,
    ) {
        if (indices.isEmpty()) return
        val focusedRow = if (selected == null) -1 else indices.indexOf(selected)
        val focusBottom = if (indices.size == 1) tray.bottom else tray.top + tray.height * 0.72f
        val focusArea = Rect(tray.left, tray.top, tray.right, focusBottom)
        val thumbArea = Rect(tray.left, focusBottom, tray.right, tray.bottom)
        for ((row, index) in indices.withIndex()) {
            val specimen = specimens[index]
            val normal = fitted(specimen.region.bounds, rowBounds(tray, row, indices.size).inset(0.15f))
            val destination = when {
                focusedRow < 0 -> normal
                row == focusedRow -> fitted(specimen.region.bounds, focusArea.inset(0.12f - magnification * 0.08f))
                else -> {
                    val thumbRow = if (row > focusedRow) row - 1 else row
                    fitted(specimen.region.bounds, rowBounds(thumbArea, thumbRow, indices.size - 1).inset(0.15f))
                }
            }
            specimen.pose = sourcePose.toward(normal.toward(destination, focus), separate)
        }
    }

    private fun drawSpecimen(scope: DrawScope, s: Specimen, separate: Float, alpha: Float) {
        scope.at(s.pose) {
            clipPath(s.region.outline) { drawSource(alpha) }
            drawPath(s.region.outline, Color(0xffe4d3af), alpha * separate * 0.72f,
                style = Stroke(scope.density * 0.8f / s.pose.scaleX))
        }
    }

    /** The first boundary grows around each object, then its smaller interior loops resolve. */
    private fun DrawScope.drawTrace(s: Specimen, t: Float, ink: Color, scale: Float) {
        if (t <= 0f) return
        val stroke = Stroke(density * 1.25f / scale, cap = StrokeCap.Round)
        if (t < 1f) {
            s.segment.reset()
            s.trace.getSegment(0f, s.trace.length * t, s.segment, true)
            drawPath(s.segment, ink, alpha = t.coerceAtMost(0.85f), style = stroke)
        }
        val fill = ease((t - 0.74f) / 0.26f)
        if (fill > 0f) drawPath(s.region.outline, ink, alpha = 0.84f * fill, style = stroke)
    }

    private fun DrawScope.drawSource(alpha: Float = 1f) {
        drawImage(image, IntOffset.Zero, originalSize, IntOffset.Zero, gridSize,
            alpha = alpha, filterQuality = FilterQuality.Medium)
    }

    private inline fun DrawScope.at(pose: Pose, block: DrawScope.() -> Unit) = withTransform({
        translate(pose.origin.x, pose.origin.y)
        scale(pose.scaleX, pose.scaleY, Offset.Zero)
    }, block)

    private fun fitted(from: Rect, into: Rect): Pose {
        val scale = min(into.width / max(1f, from.width), into.height / max(1f, from.height)).coerceAtLeast(0.001f)
        return Pose(into.center - from.center * scale, scale)
    }

    private fun rowBounds(area: Rect, row: Int, rows: Int): Rect {
        val height = area.height / rows
        val top = area.top + row * height
        return Rect(area.left, top, area.right, top + height)
    }

    private fun Rect.inset(fraction: Float): Rect {
        val dx = width * fraction
        val dy = height * fraction
        return Rect(left + dx, top + dy, right - dx, bottom - dy)
    }
}
