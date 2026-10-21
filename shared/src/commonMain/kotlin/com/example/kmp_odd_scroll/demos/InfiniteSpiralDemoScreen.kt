package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.kmp_odd_scroll.LocalPalette
import io.github.mejdi14.oddscroll.infinitespiral.InfiniteSpiralItem
import io.github.mejdi14.oddscroll.infinitespiral.InfiniteSpiralScroll
import io.github.mejdi14.oddscroll.infinitespiral.SpiralMotion
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.grid_basalt
import kmpoddscroll.shared.generated.resources.grid_falls
import kmpoddscroll.shared.generated.resources.grid_forest
import kmpoddscroll.shared.generated.resources.grid_peaks
import kmpoddscroll.shared.generated.resources.grid_sounds
import kmpoddscroll.shared.generated.resources.grid_towers
import kmpoddscroll.shared.generated.resources.wide_basalt
import kmpoddscroll.shared.generated.resources.wide_falls
import kmpoddscroll.shared.generated.resources.wide_forest
import kmpoddscroll.shared.generated.resources.wide_peaks
import kmpoddscroll.shared.generated.resources.wide_sounds
import kmpoddscroll.shared.generated.resources.wide_towers
import org.jetbrains.compose.resources.imageResource

/**
 * Both cuts of the Unsplash set, twelve cards wound onto the spiral — enough
 * for a turn and a half to be in frame at the default seven to a turn, so the
 * run reads as a helix rather than as a short arc. Credits are in the README.
 */
@Composable
fun InfiniteSpiralDemoScreen() {
    val palette = LocalPalette.current
    val cards = listOf(
        InfiniteSpiralItem(imageResource(Res.drawable.grid_sounds), "Marlborough Sounds"),
        InfiniteSpiralItem(imageResource(Res.drawable.wide_falls), "Golden Falls"),
        InfiniteSpiralItem(imageResource(Res.drawable.grid_peaks), "Above the Clouds"),
        InfiniteSpiralItem(imageResource(Res.drawable.wide_forest), "Forest Path"),
        InfiniteSpiralItem(imageResource(Res.drawable.grid_basalt), "Basalt Columns"),
        InfiniteSpiralItem(imageResource(Res.drawable.wide_towers), "City Towers"),
        InfiniteSpiralItem(imageResource(Res.drawable.grid_falls), "Golden Falls"),
        InfiniteSpiralItem(imageResource(Res.drawable.wide_sounds), "Marlborough Sounds"),
        InfiniteSpiralItem(imageResource(Res.drawable.grid_forest), "Forest Path"),
        InfiniteSpiralItem(imageResource(Res.drawable.wide_peaks), "Above the Clouds"),
        InfiniteSpiralItem(imageResource(Res.drawable.grid_towers), "City Towers"),
        InfiniteSpiralItem(imageResource(Res.drawable.wide_basalt), "Basalt Columns"),
    )
    val items = remember(cards) { cards }

    InfiniteSpiralScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        background = palette.page,
        motion = SpiralMotion.Both,
    )
}
