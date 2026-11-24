package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

internal class MicroscopeInspection(private val image: ImageBitmap) : InspectionEffect {
    companion object {
        fun zoom(amount: Float) = 20f.pow(amount)
    }

    private var navigator = Rect.Zero

    override fun draw(scope: DrawScope, frame: Rect, progress: Float, controls: InspectionControls) = with(scope) {
        val z = zoom(controls.amount * progress)
        val half = 0.5f / z
        val cx = controls.centerX.coerceIn(half, 1f - half)
        val cy = controls.centerY.coerceIn(half, 1f - half)
        val left = frame.center.x - frame.width * z * cx
        val top = frame.center.y - frame.height * z * cy
        clipRect(frame.left, frame.top, frame.right, frame.bottom) {
            val quality = if (frame.width * z > image.width * 2) FilterQuality.None else FilterQuality.Medium
            paintImage(image, Rect(left, top, left + frame.width * z, top + frame.height * z), quality = quality)
        }

        val opacity = ease((z - 1f) * 2f)
        if (opacity <= 0f) {
            navigator = Rect.Zero
            return@with
        }
        val width = min(112.dp.toPx(), frame.width * 0.24f)
        val height = min(width * image.height / image.width, frame.height * 0.25f)
        val margin = 10.dp.toPx()
        val right = frame.right - margin
        val bottom = frame.bottom - margin
        navigator = Rect(right - height * image.width / image.height, bottom - height, right, bottom)
        val n = navigator
        drawRect(Color(0xFF242423), n.topLeft - Offset(3f, 3f), Size(n.width + 6f, n.height + 6f), opacity)
        paintImage(image, n, opacity)
        val viewport = Rect(n.left + (cx - half) * n.width, n.top + (cy - half) * n.height,
            n.left + (cx + half) * n.width, n.top + (cy + half) * n.height)
        // Four mattes keep the sampled viewport true to the original painting.
        val shade = Color.Black.copy(alpha = opacity * 0.46f)
        fun matte(l: Float, t: Float, r: Float, b: Float) {
            if (r > l && b > t) drawRect(shade, Offset(l, t), Size(r - l, b - t))
        }
        matte(n.left, n.top, n.right, viewport.top)
        matte(n.left, viewport.bottom, n.right, n.bottom)
        matte(n.left, viewport.top, viewport.left, viewport.bottom)
        matte(viewport.right, viewport.top, n.right, viewport.bottom)
        drawRect(Color.White, viewport.topLeft, viewport.size, opacity, style = Stroke(1.dp.toPx()))
    }

    fun navigate(point: Offset, controls: InspectionControls): Boolean {
        if (!navigator.contains(point)) return false
        controls.centerX = (point.x - navigator.left) / navigator.width
        controls.centerY = (point.y - navigator.top) / navigator.height
        return true
    }

    fun transform(frame: Rect, point: Offset, pan: Offset, factor: Float, c: InspectionControls) {
        if (frame.isEmpty || factor <= 0f) return
        val z0 = zoom(c.amount)
        val z1 = (z0 * factor).coerceIn(1f, 20f)
        val x = (point.x - frame.center.x) / frame.width
        val y = (point.y - frame.center.y) / frame.height
        val half = 0.5f / z1
        val oldHalf = 0.5f / z0
        c.centerX = (c.centerX.coerceIn(oldHalf, 1f - oldHalf) +
            x * (1f / z0 - 1f / z1) - pan.x / (frame.width * z1)).coerceIn(half, 1f - half)
        c.centerY = (c.centerY.coerceIn(oldHalf, 1f - oldHalf) +
            y * (1f / z0 - 1f / z1) - pan.y / (frame.height * z1)).coerceIn(half, 1f - half)
        c.amount = ln(z1) / ln(20f)
    }
}
