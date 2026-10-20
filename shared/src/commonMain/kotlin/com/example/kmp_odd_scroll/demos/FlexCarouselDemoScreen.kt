package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.mejdi14.oddscroll.flexcarousel.FlexCarouselItem
import io.github.mejdi14.oddscroll.flexcarousel.FlexCarouselScroll
import io.github.mejdi14.oddscroll.flexcarousel.FlexFit
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.art_cowherd
import kmpoddscroll.shared.generated.resources.art_cypresses
import kmpoddscroll.shared.generated.resources.art_farmhouse
import kmpoddscroll.shared.generated.resources.art_night
import kmpoddscroll.shared.generated.resources.art_village
import kmpoddscroll.shared.generated.resources.art_wheatfield
import org.jetbrains.compose.resources.imageResource

/** The carousel's own page colour, so the band it sits in leaves no seam. */
private val Page = Color(0xFF060607)

/** How much of a portrait screen the row is given. */
private const val PORTRAIT_BAND = 0.62f

/**
 * Landscape cards, all one size, carrying museum scans of paintings.
 *
 * [FlexFit.Landscape] holds every card to 4:3 whatever its picture measures,
 * and the scans are cut to that already, so the fit takes nothing off. Credits
 * are in the README.
 */
@Composable
fun FlexCarouselDemoScreen() {
    val wheatfield = imageResource(Res.drawable.art_wheatfield)
    val farmhouse = imageResource(Res.drawable.art_farmhouse)
    val night = imageResource(Res.drawable.art_night)
    val cowherd = imageResource(Res.drawable.art_cowherd)
    val village = imageResource(Res.drawable.art_village)
    val cypresses = imageResource(Res.drawable.art_cypresses)

    // Ordered so no two neighbours share a palette — the green wheat does not
    // sit next to the green pasture, and the deep blue falls between them.
    val items = remember(wheatfield, farmhouse, night, cowherd, village, cypresses) {
        listOf(
            FlexCarouselItem(
                wheatfield,
                "Green Wheat Fields",
                "Vincent van Gogh, Auvers-sur-Oise. Young wheat laid down in " +
                    "short curved strokes, every one of them visible, with the " +
                    "sky worked in the same hand so that field and air read as " +
                    "one surface. Nothing in it is still.",
            ),
            FlexCarouselItem(
                farmhouse,
                "Farmhouse in Provence",
                "Vincent van Gogh, Provence. A low rubble wall leads back to a " +
                    "gate and a farmhouse, with poppies at the near corner and " +
                    "a figure walking out through standing wheat. The sky above " +
                    "it is turquoise rather than blue.",
            ),
            FlexCarouselItem(
                night,
                "Night with her Train of Stars",
                "Edward Robert Hughes. Night crosses overhead with a finger to " +
                    "her lips, dark wings spread the width of the picture, " +
                    "carrying sleeping children and trailing a scatter of birds " +
                    "and poppies. Stars gather in the cloud behind her.",
            ),
            FlexCarouselItem(
                cowherd,
                "The Cowherd",
                "A woman stands with her cow on a pale road, below a hillside " +
                    "terraced into long bands of light. A cart waits further up " +
                    "the track, and the whole of it is painted in greens so " +
                    "close together that the depth comes from touch alone.",
            ),
            FlexCarouselItem(
                village,
                "Harbour Village",
                "Houses stacked up the cliff face, cattle lying in a blue " +
                    "field, and fish in the water running below them. Everything " +
                    "is seen at once and nothing is in perspective, which is the " +
                    "point of it.",
            ),
            FlexCarouselItem(
                cypresses,
                "Wheat Field with Cypresses",
                "Vincent van Gogh. Cypresses stand almost black against rolling " +
                    "white cloud, with ripe wheat worked in heavy strokes below " +
                    "and the blue hills behind. The paint is thick enough to " +
                    "throw its own shadow.",
            ),
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Page)) {
        // Card size is a share of whatever height the row is given, so the
        // band is what makes the cards smaller — there is nothing to shrink
        // twice. A landscape screen is short enough already and keeps the lot,
        // which is why it is untouched by this.
        val portrait = maxHeight > maxWidth
        FlexCarouselScroll(
            items = items,
            modifier = if (portrait) {
                Modifier.align(Alignment.Center).fillMaxWidth().fillMaxHeight(PORTRAIT_BAND)
            } else {
                Modifier.fillMaxSize()
            },
            background = Page,
            fit = FlexFit.Landscape,
        )
    }
}
