package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.mejdi14.oddscroll.edgedistortion.EdgeDistortionScroll
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.grid_basalt
import kmpoddscroll.shared.generated.resources.grid_falls
import kmpoddscroll.shared.generated.resources.grid_forest
import kmpoddscroll.shared.generated.resources.grid_peaks
import kmpoddscroll.shared.generated.resources.grid_sounds
import kmpoddscroll.shared.generated.resources.grid_towers
import org.jetbrains.compose.resources.imageResource

/**
 * Portrait crops of the same Unsplash set the helix uses, cut 800x1000 to suit
 * the gallery's 0.8 card rather than being squeezed out of a landscape frame.
 * Credits are in the README.
 */
@Composable
fun EdgeDistortionDemoScreen() {
    val sounds = imageResource(Res.drawable.grid_sounds)
    val falls = imageResource(Res.drawable.grid_falls)
    val peaks = imageResource(Res.drawable.grid_peaks)
    val forest = imageResource(Res.drawable.grid_forest)
    val basalt = imageResource(Res.drawable.grid_basalt)
    val towers = imageResource(Res.drawable.grid_towers)

    val images = remember(sounds, falls, peaks, forest, basalt, towers) {
        listOf(sounds, falls, peaks, forest, basalt, towers)
    }

    EdgeDistortionScroll(
        images = images,
        modifier = Modifier.fillMaxSize(),
    )
}
