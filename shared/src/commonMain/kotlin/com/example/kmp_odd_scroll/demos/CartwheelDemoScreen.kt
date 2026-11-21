package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.kmp_odd_scroll.LocalPalette
import io.github.mejdi14.oddscroll.cartwheel.CartwheelHub
import io.github.mejdi14.oddscroll.cartwheel.CartwheelItem
import io.github.mejdi14.oddscroll.cartwheel.CartwheelScroll
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.grid_basalt
import kmpoddscroll.shared.generated.resources.grid_falls
import kmpoddscroll.shared.generated.resources.grid_forest
import kmpoddscroll.shared.generated.resources.grid_peaks
import kmpoddscroll.shared.generated.resources.grid_sounds
import kmpoddscroll.shared.generated.resources.grid_towers
import org.jetbrains.compose.resources.imageResource

/**
 * The six portrait cuts, cropped square by the style itself. Six is enough
 * that the three or four cards the arc holds are never the same picture twice
 * over, and the run repeats, so the wheel has no end. Credits are in the
 * README.
 */
@Composable
fun CartwheelDemoScreen() {
    val palette = LocalPalette.current
    val cards = listOf(
        CartwheelItem(imageResource(Res.drawable.grid_sounds), "Marlborough Sounds"),
        CartwheelItem(imageResource(Res.drawable.grid_peaks), "Above the Clouds"),
        CartwheelItem(imageResource(Res.drawable.grid_falls), "Golden Falls"),
        CartwheelItem(imageResource(Res.drawable.grid_basalt), "Basalt Columns"),
        CartwheelItem(imageResource(Res.drawable.grid_forest), "Forest Path"),
        CartwheelItem(imageResource(Res.drawable.grid_towers), "City Towers"),
    )
    val items = remember(cards) { cards }

    CartwheelScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        background = palette.page,
        hub = CartwheelHub.Right,
    )
}
