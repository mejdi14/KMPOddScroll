package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.kmp_odd_scroll.LocalPalette
import io.github.mejdi14.oddscroll.liftstack.LiftStackItem
import io.github.mejdi14.oddscroll.liftstack.LiftStackScroll
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.art_cowherd
import kmpoddscroll.shared.generated.resources.art_cypresses
import kmpoddscroll.shared.generated.resources.art_farmhouse
import kmpoddscroll.shared.generated.resources.art_night
import kmpoddscroll.shared.generated.resources.art_village
import kmpoddscroll.shared.generated.resources.art_wheatfield
import org.jetbrains.compose.resources.imageResource

/**
 * The museum scans again, cut square by the style, on the app's page colour —
 * pale in the light theme, which is the page the reference is set on. Opens on the reference's own run through the deck,
 * seven cards and then the lift. Credits are in the README.
 */
@Composable
fun LiftStackDemoScreen() {
    val palette = LocalPalette.current
    val wheatfield = imageResource(Res.drawable.art_wheatfield)
    val farmhouse = imageResource(Res.drawable.art_farmhouse)
    val night = imageResource(Res.drawable.art_night)
    val cowherd = imageResource(Res.drawable.art_cowherd)
    val village = imageResource(Res.drawable.art_village)
    val cypresses = imageResource(Res.drawable.art_cypresses)

    val items = remember(wheatfield, farmhouse, night, cowherd, village, cypresses) {
        listOf(
            LiftStackItem(wheatfield, "Green Wheat Fields", "Vincent van Gogh"),
            LiftStackItem(night, "Night with her Train of Stars", "Edward Robert Hughes"),
            LiftStackItem(farmhouse, "Farmhouse in Provence", "Vincent van Gogh"),
            LiftStackItem(village, "Harbour Village", "Birmingham Museums Trust"),
            LiftStackItem(cowherd, "The Cowherd", "The Metropolitan Museum of Art"),
            LiftStackItem(cypresses, "Wheat Field with Cypresses", "Vincent van Gogh"),
        )
    }

    LiftStackScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        background = palette.page,
        contentColor = palette.ink,
    )
}
