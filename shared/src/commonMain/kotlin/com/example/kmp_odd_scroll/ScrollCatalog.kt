package com.example.kmp_odd_scroll

import androidx.compose.runtime.Composable
import com.example.kmp_odd_scroll.demos.CardSphereDemoScreen
import com.example.kmp_odd_scroll.demos.EdgeDistortionDemoScreen
import com.example.kmp_odd_scroll.demos.FlexCarouselDemoScreen
import com.example.kmp_odd_scroll.demos.GalleryWallDemoScreen
import com.example.kmp_odd_scroll.demos.InfiniteMenuDemoScreen
import com.example.kmp_odd_scroll.demos.InfiniteSpiralDemoScreen
import com.example.kmp_odd_scroll.demos.LiftStackDemoScreen
import com.example.kmp_odd_scroll.demos.TapeRackDemoScreen
import com.example.kmp_odd_scroll.demos.TerraceDemoScreen

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
        route = "infinite_spiral",
        category = "Spatial",
        title = "Infinite Spiral",
        subtitle = "A column of cards wound onto a spiral, turning endlessly",
        screen = { InfiniteSpiralDemoScreen() },
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
    ScrollDemo(
        route = "infinite_menu",
        category = "Spherical",
        title = "Infinite Menu",
        subtitle = "A sphere of tiles, dragged any way, that faces up the nearest",
        screen = { InfiniteMenuDemoScreen() },
    ),
    ScrollDemo(
        route = "card_sphere",
        category = "Graphic",
        title = "Card Sphere",
        subtitle = "A field of drawn cards that coasts and settles one at the front",
        screen = { CardSphereDemoScreen() },
    ),
    ScrollDemo(
        route = "lift_stack",
        category = "Isometric",
        title = "Lift Stack",
        subtitle = "A deck of pictures that runs up through itself and stands up the one it stops past",
        screen = { LiftStackDemoScreen() },
    ),
    ScrollDemo(
        route = "tape_rack",
        category = "Oblique",
        title = "Tape Rack",
        subtitle = "A row of clear CD cases on edge that slides along itself and raises the one in the slot",
        screen = { TapeRackDemoScreen() },
    ),
    ScrollDemo(
        route = "gallery_wall",
        category = "Architectural",
        title = "Gallery Wall",
        subtitle = "Paintings hung round the inside of a room, sliding along its walls past you",
        screen = { GalleryWallDemoScreen() },
    ),
    ScrollDemo(
        route = "terrace",
        category = "Stepped",
        title = "Terrace",
        subtitle = "A deck stacked back both ways from the front card, its neighbours stepping out as narrowing bands",
        screen = { TerraceDemoScreen() },
    ),
)
