package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.mejdi14.oddscroll.edgedistortion.EdgeDistortionScroll
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.helix_basalt
import kmpoddscroll.shared.generated.resources.helix_falls
import kmpoddscroll.shared.generated.resources.helix_forest
import kmpoddscroll.shared.generated.resources.helix_peaks
import kmpoddscroll.shared.generated.resources.helix_sounds
import kmpoddscroll.shared.generated.resources.helix_towers
import org.jetbrains.compose.resources.imageResource

/**
 * The same Unsplash set as the helix demo, cut to portrait cards by the
 * gallery's own cover-fit. Credits are in the README.
 */
@Composable
fun EdgeDistortionDemoScreen() {
    val sounds = imageResource(Res.drawable.helix_sounds)
    val falls = imageResource(Res.drawable.helix_falls)
    val peaks = imageResource(Res.drawable.helix_peaks)
    val forest = imageResource(Res.drawable.helix_forest)
    val basalt = imageResource(Res.drawable.helix_basalt)
    val towers = imageResource(Res.drawable.helix_towers)

    val images = remember(sounds, falls, peaks, forest, basalt, towers) {
        listOf(sounds, falls, peaks, forest, basalt, towers)
    }

    EdgeDistortionScroll(
        images = images,
        modifier = Modifier.fillMaxSize(),
    )
}
