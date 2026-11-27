package com.example.kmp_odd_scroll.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.kmp_odd_scroll.HOME_TITLE
import com.example.kmp_odd_scroll.ScrollDemo

private data class HomeColors(
    val background: Color,
    val foreground: Color,
    val muted: Color,
    val border: Color,
)

private val LightHome = HomeColors(
    background = Color(0xFFFFFFFF),
    foreground = Color(0xFF0A0A0A),
    muted = Color(0xFF737373),
    border = Color(0x99E5E5E5),
)

private val DarkHome = HomeColors(
    background = Color(0xFF000000),
    foreground = Color(0xFFFAFAFA),
    muted = Color(0xFFA1A1A1),
    border = Color(0x24FFFFFF),
)

private val Mono = FontFamily.Monospace
private val Sans = FontFamily.SansSerif

/**
 * The landing page: a plain typographic index of every demo. Tapping a row
 * opens that style behind the page curtain.
 *
 * [compact] fits the menu beside a demo on wide screens. [selectedRoute]
 * marks the demo currently shown there.
 */
@Composable
fun HomeScreen(
    demos: List<ScrollDemo>,
    onOpenDemo: (ScrollDemo) -> Unit,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
    selectedRoute: String? = null,
    compact: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = if (dark) DarkHome else LightHome
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val gutter = if (compact) 20.dp else 28.dp

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        userScrollEnabled = enabled,
        contentPadding = PaddingValues(
            start = gutter,
            end = gutter,
            top = insets.calculateTopPadding() + gutter,
            bottom = insets.calculateBottomPadding() + 48.dp,
        )
    ) {
        item(key = "header") {
            Header(
                count = demos.size,
                dark = dark,
                colors = colors,
                compact = compact,
                enabled = enabled,
                onToggleTheme = onToggleTheme,
            )
        }
        items(demos, key = { it.route }) { demo ->
            DemoRow(
                demo = demo,
                colors = colors,
                selected = demo.route == selectedRoute,
                compact = compact,
                enabled = enabled,
                onClick = { onOpenDemo(demo) },
            )
        }
    }
}

@Composable
private fun Header(
    count: Int,
    dark: Boolean,
    colors: HomeColors,
    compact: Boolean,
    enabled: Boolean,
    onToggleTheme: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("KMP", color = colors.muted, style = monoLabel)
                Text("/", color = colors.muted, style = monoLabel)
                Text("SCROLL", color = colors.foreground, style = monoLabel)
            }
            ThemeToggle(dark = dark, ink = colors.foreground, enabled = enabled, onClick = onToggleTheme)
        }

        Spacer(Modifier.height(if (compact) 24.dp else 32.dp))
        Text(
            text = HOME_TITLE,
            color = colors.foreground,
            fontFamily = Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 26.sp else 36.sp,
            lineHeight = if (compact) 30.sp else 40.sp,
            letterSpacing = (-0.025).em,
        )
        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
        SocialLinks(ink = colors.foreground, enabled = enabled)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Open a demo to explore its layout and gestures.",
            color = colors.muted,
            fontFamily = Sans,
            fontSize = 12.sp,
            lineHeight = 19.sp,
        )

        Spacer(Modifier.height(if (compact) 32.dp else 56.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ALL DEMOS", color = colors.muted, style = monoLabel)
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(colors.border)
            )
            Spacer(Modifier.width(12.dp))
            Text("$count", color = colors.muted, style = monoLabel.copy(letterSpacing = 0.sp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DemoRow(
    demo: ScrollDemo,
    colors: HomeColors,
    selected: Boolean,
    compact: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val titleColor by animateColorAsState(
        if (pressed) colors.muted else colors.foreground,
        tween(150),
        label = "title"
    )
    val arrowAlpha by animateFloatAsState(
        if (pressed || selected) 1f else 0f,
        tween(200),
        label = "arrow"
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
    ) {
        Row(
            modifier = Modifier.padding(vertical = if (compact) 16.dp else 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = demo.title,
                    color = titleColor,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = demo.subtitle,
                    color = colors.muted,
                    fontFamily = Sans,
                    fontSize = 14.sp,
                    lineHeight = 23.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${demo.category} · ${demo.tech}",
                    color = colors.muted,
                    style = monoLabel,
                )
            }
            ArrowUpRight(
                color = colors.foreground,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
                    .graphicsLayer { alpha = arrowAlpha },
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border)
        )
    }
}

private val monoLabel = TextStyle(
    fontFamily = Mono,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.05.em,
)

/** Thin "↗" drawn like the line icon on the reference list. */
@Composable
private fun ArrowUpRight(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 1.5f * u, cap = StrokeCap.Round)
        val head = Path().apply {
            moveTo(7f * u, 7f * u)
            lineTo(17f * u, 7f * u)
            lineTo(17f * u, 17f * u)
        }
        drawPath(head, color, style = stroke)
        drawLine(color, Offset(7f * u, 17f * u), Offset(17f * u, 7f * u), 1.5f * u, StrokeCap.Round)
    }
}
