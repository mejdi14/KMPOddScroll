package com.example.kmp_odd_scroll

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The demo app's two looks. The gallery and every demo read the one in force
 * from [LocalPalette], so a style is handed [page] and [ink] rather than
 * choosing a colour of its own, and flipping the theme on the home screen
 * carries into whichever demo is opened next.
 *
 * [tile] is the surface the gallery rows sit on, [muted] the secondary text.
 */
@Immutable
data class OddScrollPalette(
    val dark: Boolean,
    val page: Color,
    val tile: Color,
    val ink: Color,
    val muted: Color,
)

val DarkPalette = OddScrollPalette(
    dark = true,
    page = Color(0xFF07070A),
    tile = Color(0xFF0E0F13),
    ink = Color(0xFFF3F0E9),
    muted = Color(0xFF8A8A95),
)

val LightPalette = OddScrollPalette(
    dark = false,
    page = Color(0xFFF6F6F1),
    tile = Color(0xFFFFFFFF),
    ink = Color(0xFF141414),
    muted = Color(0xFF6B6B73),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }
