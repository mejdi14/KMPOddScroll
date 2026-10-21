package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.kmp_odd_scroll.LocalPalette
import io.github.mejdi14.oddscroll.terrace.TerraceItem
import io.github.mejdi14.oddscroll.terrace.TerraceScroll
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.grid_basalt
import kmpoddscroll.shared.generated.resources.grid_falls
import kmpoddscroll.shared.generated.resources.grid_forest
import kmpoddscroll.shared.generated.resources.grid_peaks
import kmpoddscroll.shared.generated.resources.grid_sounds
import kmpoddscroll.shared.generated.resources.grid_towers
import org.jetbrains.compose.resources.imageResource

/**
 * The six portrait cuts, trimmed a little at the sides to the card's 1:1.384.
 * The stack shows seven at once, so the picture at the top band comes round
 * again at the bottom one. Credits are in the README.
 */
@Composable
fun TerraceDemoScreen() {
    val palette = LocalPalette.current
    val cards = listOf(
        TerraceItem(imageResource(Res.drawable.grid_sounds), "Sounds", "Marlborough Sounds"),
        TerraceItem(imageResource(Res.drawable.grid_peaks), "Peaks", "Above the Clouds"),
        TerraceItem(imageResource(Res.drawable.grid_falls), "Falls", "Golden Falls"),
        TerraceItem(imageResource(Res.drawable.grid_basalt), "Basalt", "Basalt Columns"),
        TerraceItem(imageResource(Res.drawable.grid_forest), "Forest", "Forest Path"),
        TerraceItem(imageResource(Res.drawable.grid_towers), "Towers", "City Towers"),
    )
    val items = remember(cards) { cards }

    TerraceScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        background = palette.page,
        contentColor = palette.ink,
    )
}
