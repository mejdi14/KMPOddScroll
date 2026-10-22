package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.kmp_odd_scroll.LocalPalette
import io.github.mejdi14.oddscroll.taperack.TapeRackItem
import io.github.mejdi14.oddscroll.taperack.TapeRackScroll
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.art_cowherd
import kmpoddscroll.shared.generated.resources.art_cypresses
import kmpoddscroll.shared.generated.resources.art_farmhouse
import kmpoddscroll.shared.generated.resources.art_night
import kmpoddscroll.shared.generated.resources.art_village
import kmpoddscroll.shared.generated.resources.art_wheatfield
import kmpoddscroll.shared.generated.resources.wide_basalt
import kmpoddscroll.shared.generated.resources.wide_falls
import kmpoddscroll.shared.generated.resources.wide_forest
import kmpoddscroll.shared.generated.resources.wide_peaks
import kmpoddscroll.shared.generated.resources.wide_sounds
import kmpoddscroll.shared.generated.resources.wide_towers
import org.jetbrains.compose.resources.imageResource

/**
 * Twelve CD cases, the wide photographs and the museum scans printed on their
 * discs and dealt alternately so no two neighbours look alike, on the app's page colour (near-black is the
 * one the reference is set on).
 * Twelve is about what the row holds in view, so it only repeats off screen.
 * Credits are in the README.
 */
@Composable
fun TapeRackDemoScreen() {
    val palette = LocalPalette.current
    val sounds = imageResource(Res.drawable.wide_sounds)
    val peaks = imageResource(Res.drawable.wide_peaks)
    val falls = imageResource(Res.drawable.wide_falls)
    val basalt = imageResource(Res.drawable.wide_basalt)
    val forest = imageResource(Res.drawable.wide_forest)
    val towers = imageResource(Res.drawable.wide_towers)
    val wheatfield = imageResource(Res.drawable.art_wheatfield)
    val night = imageResource(Res.drawable.art_night)
    val farmhouse = imageResource(Res.drawable.art_farmhouse)
    val village = imageResource(Res.drawable.art_village)
    val cowherd = imageResource(Res.drawable.art_cowherd)
    val cypresses = imageResource(Res.drawable.art_cypresses)

    val items = remember(sounds, peaks, falls, basalt, forest, towers, wheatfield, night, farmhouse, village, cowherd, cypresses) {
        listOf(
            TapeRackItem(sounds, "Marlborough Sounds", "Samuel Harrington"),
            TapeRackItem(wheatfield, "Green Wheat Fields", "Vincent van Gogh"),
            TapeRackItem(peaks, "Above the Clouds", "Maciej Marko"),
            TapeRackItem(night, "Night with her Train of Stars", "Edward Robert Hughes"),
            TapeRackItem(falls, "Golden Falls", "Ingrid Eich"),
            TapeRackItem(farmhouse, "Farmhouse in Provence", "Vincent van Gogh"),
            TapeRackItem(basalt, "Basalt Columns", "Adrien Olichon"),
            TapeRackItem(village, "Harbour Village", "Birmingham Museums Trust"),
            TapeRackItem(forest, "Forest Path", "Wolfgang Hasselmann"),
            TapeRackItem(cowherd, "The Cowherd", "The Metropolitan Museum of Art"),
            TapeRackItem(towers, "City Towers", "Heye Jensen"),
            TapeRackItem(cypresses, "Wheat Field with Cypresses", "Vincent van Gogh"),
        )
    }

    TapeRackScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        background = palette.page,
        contentColor = palette.ink,
    )
}
