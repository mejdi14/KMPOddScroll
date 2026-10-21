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
import com.example.kmp_odd_scroll.LocalPalette
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
    val palette = LocalPalette.current
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
                    "short curved strokes, every one of them left visible, with " +
                    "the sky worked in the same hand so that field and air read " +
                    "as a single surface rather than as one thing behind " +
                    "another. The greens are stacked rather than blended, and " +
                    "the whole field leans the same way, as though a wind had " +
                    "just crossed it. Nothing in the picture is still.",
            ),
            FlexCarouselItem(
                farmhouse,
                "Farmhouse in Provence",
                "Vincent van Gogh, Provence. A low rubble wall runs back from " +
                    "the near corner to a gate and a farmhouse, with poppies at " +
                    "the foot of it and a figure walking out through standing " +
                    "wheat. The sky is turquoise rather than blue, which throws " +
                    "the ochre of the field forward and makes a hot afternoon " +
                    "out of what is otherwise a plain row of buildings.",
            ),
            FlexCarouselItem(
                night,
                "Night with her Train of Stars",
                "Edward Robert Hughes. Night crosses overhead with a finger to " +
                    "her lips, dark wings spread the full width of the picture, " +
                    "carrying sleeping children and trailing a scatter of birds " +
                    "and poppies behind her. Stars gather in the cloud to one " +
                    "side. Almost all of it is one colour, and the drawing is " +
                    "left soft enough that the figures seem to be forming out " +
                    "of the sky rather than passing across it.",
            ),
            FlexCarouselItem(
                cowherd,
                "The Cowherd",
                "A woman stands with her cow on a pale chalk road, below a " +
                    "hillside terraced into long bands of light. A cart waits " +
                    "further up the track and a white house sits in the fold of " +
                    "the hill. Almost the whole of it is painted in greens kept " +
                    "deliberately close together, so that what separates the " +
                    "near hedge from the far slope is the direction of the " +
                    "brush rather than any change of colour.",
            ),
            FlexCarouselItem(
                village,
                "Harbour Village",
                "Houses stacked up the cliff face, cattle lying in a blue " +
                    "field, boats and fish in the water running below them. " +
                    "Everything is seen at once and nothing is in perspective — " +
                    "the roofs, the hillside and the sea are all turned flat to " +
                    "face the viewer. It is a map of a place as much as a view " +
                    "of one, and it holds together because the colour, not the " +
                    "depth, is doing the work.",
            ),
            FlexCarouselItem(
                cypresses,
                "Wheat Field with Cypresses",
                "Vincent van Gogh. Cypresses stand almost black against rolling " +
                    "white cloud, with ripe wheat worked in heavy strokes below " +
                    "them and blue hills folded along the horizon behind. The " +
                    "paint is laid on thickly enough to throw its own shadow, so " +
                    "the surface catches the light as the field in it would " +
                    "have done. Seen close, every mark is a separate decision.",
            ),
        )
    }

    // The band takes the carousel's own page colour, so it leaves no seam.
    BoxWithConstraints(Modifier.fillMaxSize().background(palette.page)) {
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
            background = palette.page,
            contentColor = palette.ink,
            fit = FlexFit.Landscape,
        )
    }
}
