package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import io.github.mejdi14.oddscroll.cdrack.CdRackItem
import io.github.mejdi14.oddscroll.cdrack.CdRackScroll
import io.github.mejdi14.oddscroll.cdrack.rememberCdRackScrollState
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
 * discs and dealt alternately so no two neighbours look alike, on the white
 * studio wall the case's reference is set on. Twelve is about what the row
 * holds in view, so it only repeats off screen. Tap one to take it out; the
 * system's back puts it back before it leaves the demo.
 * Credits are in the README.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CdRackDemoScreen() {
    val state = rememberCdRackScrollState()
    BackHandler(enabled = state.inspecting) { state.leave() }
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
            CdRackItem(sounds, "Marlborough Sounds", "Samuel Harrington"),
            CdRackItem(wheatfield, "Green Wheat Fields", "Vincent van Gogh"),
            CdRackItem(peaks, "Above the Clouds", "Maciej Marko"),
            CdRackItem(night, "Night with her Train of Stars", "Edward Robert Hughes"),
            CdRackItem(falls, "Golden Falls", "Ingrid Eich"),
            CdRackItem(farmhouse, "Farmhouse in Provence", "Vincent van Gogh"),
            CdRackItem(basalt, "Basalt Columns", "Adrien Olichon"),
            CdRackItem(village, "Harbour Village", "Birmingham Museums Trust"),
            CdRackItem(forest, "Forest Path", "Wolfgang Hasselmann"),
            CdRackItem(cowherd, "The Cowherd", "The Metropolitan Museum of Art"),
            CdRackItem(towers, "City Towers", "Heye Jensen"),
            CdRackItem(cypresses, "Wheat Field with Cypresses", "Vincent van Gogh"),
        )
    }

    CdRackScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        state = state,
    )
}
