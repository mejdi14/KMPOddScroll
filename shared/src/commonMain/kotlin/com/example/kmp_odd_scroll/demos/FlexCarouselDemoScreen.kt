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
import kmpoddscroll.shared.generated.resources.wide_basalt
import kmpoddscroll.shared.generated.resources.wide_falls
import kmpoddscroll.shared.generated.resources.wide_forest
import kmpoddscroll.shared.generated.resources.wide_peaks
import kmpoddscroll.shared.generated.resources.wide_sounds
import kmpoddscroll.shared.generated.resources.wide_towers
import org.jetbrains.compose.resources.imageResource

/** The carousel's own page colour, so the band it sits in leaves no seam. */
private val Page = Color(0xFF060607)

/** How much of a portrait screen the row is given. */
private const val PORTRAIT_BAND = 0.62f

/**
 * Landscape cards, all one size.
 *
 * [FlexFit.Landscape] holds every card to 4:3 whatever its picture measures,
 * and the pictures handed to it are the 2:1 cuts — so the fit takes a third off
 * the sides of a landscape photograph rather than most of the height off a
 * portrait one. Credits are in the README.
 */
@Composable
fun FlexCarouselDemoScreen() {
    val sounds = imageResource(Res.drawable.wide_sounds)
    val falls = imageResource(Res.drawable.wide_falls)
    val peaks = imageResource(Res.drawable.wide_peaks)
    val forest = imageResource(Res.drawable.wide_forest)
    val basalt = imageResource(Res.drawable.wide_basalt)
    val towers = imageResource(Res.drawable.wide_towers)

    val items = remember(sounds, falls, peaks, forest, basalt, towers) {
        listOf(
            FlexCarouselItem(sounds, "Marlborough Sounds"),
            FlexCarouselItem(falls, "Golden Falls"),
            FlexCarouselItem(peaks, "Above the Clouds"),
            FlexCarouselItem(forest, "Forest Path"),
            FlexCarouselItem(basalt, "Basalt Columns"),
            FlexCarouselItem(towers, "City Towers"),
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
