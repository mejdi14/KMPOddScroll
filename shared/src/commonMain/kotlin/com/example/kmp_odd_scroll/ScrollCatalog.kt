package com.example.kmp_odd_scroll

import androidx.compose.runtime.Composable
import com.example.kmp_odd_scroll.demos.EdgeDistortionDemoScreen
import com.example.kmp_odd_scroll.demos.FlexCarouselDemoScreen
import com.example.kmp_odd_scroll.demos.HelixDemoScreen

/**
 * One entry per scroll style. Drives both the home gallery and the navigation
 * state, so adding a style is a single addition here plus its screen.
 *
 * [category] is the family the style belongs to; the gallery shows it beside
 * the title as "Category · Title".
 */
data class ScrollDemo(
    val route: String,
    val category: String,
    val title: String,
    val subtitle: String,
    val screen: @Composable () -> Unit,
)

val scrollDemos: List<ScrollDemo> = listOf(
    ScrollDemo(
        route = "helix",
        category = "Spatial",
        title = "Helix",
        subtitle = "A spiral column of cards, turned by dragging or scrolling",
        screen = { HelixDemoScreen() },
    ),
    ScrollDemo(
        route = "edge_distortion",
        category = "Infinite",
        title = "Edge Distortion",
        subtitle = "An endless card lattice that bends away at the borders",
        screen = { EdgeDistortionDemoScreen() },
    ),
    ScrollDemo(
        route = "flex_carousel",
        category = "Optical",
        title = "Flex Carousel",
        subtitle = "An infinite picture row flowing through invisible liquid glass",
        screen = { FlexCarouselDemoScreen() },
    ),
)
