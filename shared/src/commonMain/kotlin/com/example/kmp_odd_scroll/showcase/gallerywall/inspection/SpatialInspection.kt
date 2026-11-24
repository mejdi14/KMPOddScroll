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
import kotlin.math.ceil
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
            val local = (point - pose.origin) / pose.scale
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

    private data class Pose(val origin: Offset, val scale: Float) {
        fun map(p: Offset) = origin + p * scale
        fun toward(other: Pose, t: Float) = Pose(origin + (other.origin - origin) * t, scale + (other.scale - scale) * t)
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
        val start = fitted(grid, frame)
        // Use the breadth of the available stage for a portrait hung in a landscape window.
        val margin = min(scope.size.width, scope.size.height) * 0.035f
        val stage = Rect(min(margin, frame.left), frame.top, max(scope.size.width - margin, frame.right), frame.bottom)
        val beside = stage.width > stage.height * 1.15f
        val gap = min(stage.width, stage.height) * 0.048f
        val selected = controls.selected.takeIf { it in specimens.indices }
        val focus = if (selected == null) 0f else ease(controls.focus)
        val sourceArea: Rect
        val collection: Rect
        if (beside) {
            val split = stage.left + stage.width * mix(0.55f, 0.30f, focus)
            sourceArea = Rect(stage.left, stage.top, split - gap / 2f, stage.bottom)
            collection = Rect(split + gap / 2f, stage.top, stage.right, stage.bottom)
        } else {
            val split = stage.top + stage.height * mix(0.53f, 0.27f, focus)
            sourceArea = Rect(stage.left, stage.top, stage.right, split - gap / 2f)
            collection = Rect(stage.left, split + gap / 2f, stage.right, stage.bottom)
        }
        sourcePose = start.toward(fitted(grid, sourceArea), separate)
        lastFrame = frame
        lastSelected = controls.selected
        val trace = ease(p / 0.48f)
        scope.at(sourcePose) {
            drawSource(1f - focus * 0.20f)
            for ((i, s) in specimens.withIndex()) {
                val ink = if (i == selected) Color(0xffffe6b0) else Color(0xfff4e7ce)
                drawPath(s.region.outline, Color(0xff292925), alpha = separate * 0.61f)
                drawTrace(s, trace, ink, sourcePose.scale)
            }
        }

        val focusTray = if (specimens.size == 1) collection else Rect(
            collection.left, collection.top, collection.right, collection.top + collection.height * 0.76f
        )
        val otherTray = Rect(collection.left, focusTray.bottom, collection.right, collection.bottom)
        val normalColumns = if (beside) ceil(kotlin.math.sqrt(specimens.size * collection.width / collection.height)).toInt()
            .coerceIn(1, min(3, specimens.size)) else min(3, specimens.size)
        val normalRows = (specimens.size + normalColumns - 1) / normalColumns
        for ((i, s) in specimens.withIndex()) {
            val cell = cellBounds(collection, i, normalColumns, normalRows)
            val normal = fitted(s.region.bounds, cell.inset(0.12f))
            val focused = when {
                selected == null -> normal
                i == selected -> fitted(s.region.bounds, focusTray.inset(0.22f - controls.amount * 0.18f))
                else -> {
                    val k = if (i > selected) i - 1 else i
                    val thumb = cellBounds(otherTray, k, max(1, specimens.size - 1), 1)
                    fitted(s.region.bounds, thumb.inset(0.13f))
                }
            }
            val to = normal.toward(focused, focus)
            s.pose = sourcePose.toward(to, separate)
            if (separate <= 0f) continue
            val from = sourcePose.map(s.region.anchor)
            val target = s.pose.map(s.region.anchor)
            val lineAlpha = (if (selected == null || i == selected) 0.59f else 0.16f) * separate
            val lead = if (beside) Offset((from.x + target.x) / 2f, from.y) else Offset(from.x, (from.y + target.y) / 2f)
            val tail = if (beside) Offset(lead.x, target.y) else Offset(target.x, lead.y)
            val path = s.segment.apply {
                reset()
                moveTo(from.x, from.y)
                cubicTo(lead.x, lead.y, tail.x, tail.y, target.x, target.y)
            }
            scope.drawPath(path, Color(0xffb5a07b), alpha = lineAlpha, style = Stroke(scope.density * 0.9f))
            scope.drawCircle(Color(0xfff5e5c7), radius = scope.density * 2.2f, center = from, alpha = lineAlpha)
        }
        for ((i, s) in specimens.withIndex()) if (i != selected) {
            drawSpecimen(scope, s, separate, 1f - focus * 0.46f)
        }
        if (selected != null) drawSpecimen(scope, specimens[selected], separate, 1f)
    }

    private fun drawSpecimen(scope: DrawScope, s: Specimen, separate: Float, alpha: Float) {
        if (separate <= 0f) return
        scope.at(s.pose) {
            clipPath(s.region.outline) { drawSource(alpha) }
            drawPath(s.region.outline, Color(0xffe4d3af), alpha * separate * 0.72f,
                style = Stroke(scope.density * 0.8f / s.pose.scale))
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
        scale(pose.scale, pose.scale, Offset.Zero)
    }, block)

    private fun fitted(from: Rect, into: Rect): Pose {
        val scale = min(into.width / max(1f, from.width), into.height / max(1f, from.height)).coerceAtLeast(0.001f)
        return Pose(into.center - from.center * scale, scale)
    }

    private fun cellBounds(area: Rect, i: Int, columns: Int, rows: Int): Rect {
        val w = area.width / columns
        val h = area.height / rows
        val x = area.left + (i % columns) * w
        val y = area.top + (i / columns) * h
        return Rect(x, y, x + w, y + h)
    }

    private fun Rect.inset(fraction: Float): Rect {
        val dx = width * fraction
        val dy = height * fraction
        return Rect(left + dx, top + dy, right - dx, bottom - dy)
    }
}
