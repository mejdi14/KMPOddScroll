package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.mejdi14.oddscroll.flexcarousel.FlexCarouselItem
import io.github.mejdi14.oddscroll.flexcarousel.FlexCarouselScroll
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.grid_basalt
import kmpoddscroll.shared.generated.resources.grid_falls
import kmpoddscroll.shared.generated.resources.grid_forest
import kmpoddscroll.shared.generated.resources.grid_peaks
import kmpoddscroll.shared.generated.resources.grid_sounds
import kmpoddscroll.shared.generated.resources.grid_towers
import kmpoddscroll.shared.generated.resources.helix_basalt
import kmpoddscroll.shared.generated.resources.helix_falls
import kmpoddscroll.shared.generated.resources.helix_forest
import kmpoddscroll.shared.generated.resources.helix_peaks
import kmpoddscroll.shared.generated.resources.helix_sounds
import kmpoddscroll.shared.generated.resources.helix_towers
import org.jetbrains.compose.resources.imageResource

/**
 * Both cuts of the Unsplash set — the 2:1 landscapes and the 4:5 portraits —
 * interleaved. The carousel sizes each card from its own picture, so a mixed
 * set is the point: it gives the row the uneven run of widths it is built
 * around, which a single aspect would hide. Credits are in the README.
 */
@Composable
fun FlexCarouselDemoScreen() {
    val wideSounds = imageResource(Res.drawable.helix_sounds)
    val tallFalls = imageResource(Res.drawable.grid_falls)
    val widePeaks = imageResource(Res.drawable.helix_peaks)
    val tallForest = imageResource(Res.drawable.grid_forest)
    val wideBasalt = imageResource(Res.drawable.helix_basalt)
    val tallTowers = imageResource(Res.drawable.grid_towers)
    val wideFalls = imageResource(Res.drawable.helix_falls)
    val tallSounds = imageResource(Res.drawable.grid_sounds)
    val wideForest = imageResource(Res.drawable.helix_forest)
    val tallPeaks = imageResource(Res.drawable.grid_peaks)
    val wideTowers = imageResource(Res.drawable.helix_towers)
    val tallBasalt = imageResource(Res.drawable.grid_basalt)

    val items = remember(wideSounds, tallFalls, widePeaks, tallForest) {
        listOf(
            FlexCarouselItem(wideSounds, "Marlborough Sounds"),
            FlexCarouselItem(tallFalls, "Golden Falls"),
            FlexCarouselItem(widePeaks, "Above the Clouds"),
            FlexCarouselItem(tallForest, "Forest Path"),
            FlexCarouselItem(wideBasalt, "Basalt Columns"),
            FlexCarouselItem(tallTowers, "City Towers"),
            FlexCarouselItem(wideFalls, "Golden Falls"),
            FlexCarouselItem(tallSounds, "Marlborough Sounds"),
            FlexCarouselItem(wideForest, "Forest Path"),
            FlexCarouselItem(tallPeaks, "Above the Clouds"),
            FlexCarouselItem(wideTowers, "City Towers"),
            FlexCarouselItem(tallBasalt, "Basalt Columns"),
        )
    }

    FlexCarouselScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
    )
}
