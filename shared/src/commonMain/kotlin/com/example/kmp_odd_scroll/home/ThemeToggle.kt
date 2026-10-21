package com.example.kmp_odd_scroll.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val RAYS = 8

/**
 * A round button that shows the look it switches to: a sun while the page is
 * dark, a crescent while it is light. Drawn rather than loaded, so the gallery
 * needs neither an icon set nor a resource for it.
 */
@Composable
fun ThemeToggle(
    dark: Boolean,
    surface: Color,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = if (dark) "Switch to light theme" else "Switch to dark theme"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val c = center
            val u = size.minDimension
            if (dark) {
                drawCircle(ink, radius = u * 0.2f, center = c)
                repeat(RAYS) { i ->
                    val a = i * 2 * PI.toFloat() / RAYS
                    val dir = Offset(cos(a), sin(a))
                    drawLine(
                        color = ink,
                        start = c + dir * (u * 0.32f),
                        end = c + dir * (u * 0.46f),
                        strokeWidth = u * 0.09f,
                        cap = StrokeCap.Round,
                    )
                }
            } else {
                val disc = Path().apply { addOval(Rect(c, u * 0.4f)) }
                val bite = Path().apply {
                    addOval(Rect(Offset(c.x + u * 0.22f, c.y - u * 0.16f), u * 0.34f))
                }
                drawPath(Path.combine(PathOperation.Difference, disc, bite), ink)
            }
        }
    }
}
