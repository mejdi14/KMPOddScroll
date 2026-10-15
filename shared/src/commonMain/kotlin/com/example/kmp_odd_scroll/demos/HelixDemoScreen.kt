package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.mejdi14.oddscroll.helix.HelixItem
import io.github.mejdi14.oddscroll.helix.HelixScroll
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.helix_basalt
import kmpoddscroll.shared.generated.resources.helix_falls
import kmpoddscroll.shared.generated.resources.helix_forest
import kmpoddscroll.shared.generated.resources.helix_peaks
import kmpoddscroll.shared.generated.resources.helix_sounds
import kmpoddscroll.shared.generated.resources.helix_towers
import org.jetbrains.compose.resources.imageResource

/**
 * Photographs from Unsplash, bundled as Compose resources rather than fetched,
 * so the sample has no network call and no image-loading dependency. They are
 * cut to 1200x600 to match the helix's default 2:1 card, so nothing is cropped
 * again at draw time. Credits are in the README.
 */
@Composable
fun HelixDemoScreen() {
    val sounds = imageResource(Res.drawable.helix_sounds)
    val falls = imageResource(Res.drawable.helix_falls)
    val peaks = imageResource(Res.drawable.helix_peaks)
    val forest = imageResource(Res.drawable.helix_forest)
    val basalt = imageResource(Res.drawable.helix_basalt)
    val towers = imageResource(Res.drawable.helix_towers)

    val items = remember(sounds, falls, peaks, forest, basalt, towers) {
        listOf(
            HelixItem(sounds, "Marlborough Sounds"),
            HelixItem(falls, "Golden Falls"),
            HelixItem(peaks, "Above the Clouds"),
            HelixItem(forest, "Forest Path"),
            HelixItem(basalt, "Basalt Columns"),
            HelixItem(towers, "City Towers"),
        )
    }

    HelixScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        brand = "Studio",
    )
}
