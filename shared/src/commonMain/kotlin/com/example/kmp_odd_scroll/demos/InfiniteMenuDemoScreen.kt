package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.mejdi14.oddscroll.infinitemenu.InfiniteMenuItem
import io.github.mejdi14.oddscroll.infinitemenu.InfiniteMenuScroll
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.grid_basalt
import kmpoddscroll.shared.generated.resources.grid_falls
import kmpoddscroll.shared.generated.resources.grid_forest
import kmpoddscroll.shared.generated.resources.grid_peaks
import kmpoddscroll.shared.generated.resources.grid_sounds
import kmpoddscroll.shared.generated.resources.grid_towers
import org.jetbrains.compose.resources.imageResource

/**
 * The Unsplash set, repeated around the sphere. Tiles are square patches of the
 * surface, so the portrait cut is the one to hand them — a landscape frame
 * would lose most of its width to the crop. Credits are in the README.
 */
@Composable
fun InfiniteMenuDemoScreen() {
    val sounds = imageResource(Res.drawable.grid_sounds)
    val falls = imageResource(Res.drawable.grid_falls)
    val peaks = imageResource(Res.drawable.grid_peaks)
    val forest = imageResource(Res.drawable.grid_forest)
    val basalt = imageResource(Res.drawable.grid_basalt)
    val towers = imageResource(Res.drawable.grid_towers)

    val items = remember(sounds, falls, peaks, forest, basalt, towers) {
        listOf(
            InfiniteMenuItem(sounds, "Marlborough Sounds", "Aerial, forested hills"),
            InfiniteMenuItem(falls, "Golden Falls", "Mist at low sun"),
            InfiniteMenuItem(peaks, "Above the Clouds", "Peaks over a low layer"),
            InfiniteMenuItem(forest, "Forest Path", "Autumn woodland"),
            InfiniteMenuItem(basalt, "Basalt Columns", "Hexagonal rock, water pools"),
            InfiniteMenuItem(towers, "City Towers", "Silhouettes, orange sky"),
        )
    }

    InfiniteMenuScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        // The sphere only turns when it is turned. Left to itself the reference
        // steps on every few seconds, which fights whoever is reading a tile.
        autoplay = 0f,
    )
}
