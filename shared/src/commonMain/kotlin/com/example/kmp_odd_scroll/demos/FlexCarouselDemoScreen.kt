package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.mejdi14.oddscroll.flexcarousel.FlexCarouselItem
import io.github.mejdi14.oddscroll.flexcarousel.FlexCarouselScroll
import io.github.mejdi14.oddscroll.flexcarousel.FlexFit
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.grid_basalt
import kmpoddscroll.shared.generated.resources.grid_falls
import kmpoddscroll.shared.generated.resources.grid_forest
import kmpoddscroll.shared.generated.resources.grid_peaks
import kmpoddscroll.shared.generated.resources.grid_sounds
import kmpoddscroll.shared.generated.resources.grid_towers
import org.jetbrains.compose.resources.imageResource

/**
 * The portrait cut of the Unsplash set, every card drawn at one size.
 *
 * [FlexFit.Portrait] holds all six to 3:4 whatever their pictures measure, and
 * the pictures are already that shape, so nothing is cropped twice. Credits are
 * in the README.
 */
@Composable
fun FlexCarouselDemoScreen() {
    val sounds = imageResource(Res.drawable.grid_sounds)
    val falls = imageResource(Res.drawable.grid_falls)
    val peaks = imageResource(Res.drawable.grid_peaks)
    val forest = imageResource(Res.drawable.grid_forest)
    val basalt = imageResource(Res.drawable.grid_basalt)
    val towers = imageResource(Res.drawable.grid_towers)

    val items = remember(sounds, falls, peaks, forest, basalt, towers) {
        listOf(
            FlexCarouselItem(sounds, "Marlborough Sounds"),
            FlexCarouselItem(falls, "Golden Falls"),
            FlexCarouselItem(peaks, "Above the Clouds"),
            FlexCarouselItem(forest, "Forest Path"),
            FlexCarouselItem(basalt, "Basalt Columns"),
            FlexCarouselItem(towers, "City Towers"),
        )
    }

    FlexCarouselScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        fit = FlexFit.Portrait,
    )
}
