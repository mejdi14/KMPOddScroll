package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.kmp_odd_scroll.LocalPalette
import io.github.mejdi14.oddscroll.cardsphere.CardSphereScroll
import io.github.mejdi14.oddscroll.cardsphere.CardSphereTheme
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.flower_alyssum
import kmpoddscroll.shared.generated.resources.flower_anemone
import kmpoddscroll.shared.generated.resources.flower_aster
import kmpoddscroll.shared.generated.resources.flower_astilbe
import kmpoddscroll.shared.generated.resources.flower_bellflower
import kmpoddscroll.shared.generated.resources.flower_borage
import kmpoddscroll.shared.generated.resources.flower_carnation
import kmpoddscroll.shared.generated.resources.flower_clover
import kmpoddscroll.shared.generated.resources.flower_coneflower
import kmpoddscroll.shared.generated.resources.flower_daisy
import kmpoddscroll.shared.generated.resources.flower_dogwood
import kmpoddscroll.shared.generated.resources.flower_flax
import kmpoddscroll.shared.generated.resources.flower_forgetmenot
import kmpoddscroll.shared.generated.resources.flower_marigold
import kmpoddscroll.shared.generated.resources.flower_muscari
import kmpoddscroll.shared.generated.resources.flower_pansy
import kmpoddscroll.shared.generated.resources.flower_poppy
import kmpoddscroll.shared.generated.resources.flower_snowberry
import kmpoddscroll.shared.generated.resources.flower_spirea
import kmpoddscroll.shared.generated.resources.flower_sunflower
import kmpoddscroll.shared.generated.resources.flower_tansy
import org.jetbrains.compose.resources.imageResource

/**
 * Twenty-one watercolour flowers, one to a card.
 *
 * They were cut out of two collection sheets: each flower was found as its own
 * run of ink on the paper, its neighbours painted back out, and the result
 * framed to the card's 4:5 — so every card carries one flower and the paper it
 * was painted on. Credits are in the README.
 *
 * The page follows the app's theme, so the style's own double tap flip is off:
 * it would only leave the page disagreeing with the rest of the app. These are
 * cream sheets, and on the light page they lose some of their edges.
 */
@Composable
fun CardSphereDemoScreen() {
    val palette = LocalPalette.current
    val flowers = listOf(
        imageResource(Res.drawable.flower_poppy),
        imageResource(Res.drawable.flower_pansy),
        imageResource(Res.drawable.flower_carnation),
        imageResource(Res.drawable.flower_aster),
        imageResource(Res.drawable.flower_sunflower),
        imageResource(Res.drawable.flower_anemone),
        imageResource(Res.drawable.flower_muscari),
        imageResource(Res.drawable.flower_coneflower),
        imageResource(Res.drawable.flower_astilbe),
        imageResource(Res.drawable.flower_tansy),
        imageResource(Res.drawable.flower_bellflower),
        imageResource(Res.drawable.flower_daisy),
        imageResource(Res.drawable.flower_clover),
        imageResource(Res.drawable.flower_flax),
        imageResource(Res.drawable.flower_dogwood),
        imageResource(Res.drawable.flower_spirea),
        imageResource(Res.drawable.flower_borage),
        imageResource(Res.drawable.flower_forgetmenot),
        imageResource(Res.drawable.flower_alyssum),
        imageResource(Res.drawable.flower_marigold),
        imageResource(Res.drawable.flower_snowberry),
    )
    val plates = remember(flowers) { flowers }

    CardSphereScroll(
        flowers = plates,
        modifier = Modifier.fillMaxSize(),
        theme = if (palette.dark) CardSphereTheme.Dark else CardSphereTheme.Light,
        allowThemeToggle = false,
    )
}
