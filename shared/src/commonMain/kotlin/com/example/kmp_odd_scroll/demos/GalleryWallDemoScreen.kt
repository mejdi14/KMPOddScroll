package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.example.kmp_odd_scroll.LocalPalette
import io.github.mejdi14.oddscroll.gallerywall.GalleryWallFrame
import io.github.mejdi14.oddscroll.gallerywall.GalleryWallItem
import io.github.mejdi14.oddscroll.gallerywall.GalleryWallScroll
import io.github.mejdi14.oddscroll.gallerywall.rememberGalleryWallScrollState
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.museum_almond
import kmpoddscroll.shared.generated.resources.museum_armchair
import kmpoddscroll.shared.generated.resources.museum_boating
import kmpoddscroll.shared.generated.resources.museum_boating_party
import kmpoddscroll.shared.generated.resources.museum_bridge
import kmpoddscroll.shared.generated.resources.museum_cliff_walk
import kmpoddscroll.shared.generated.resources.museum_fan
import kmpoddscroll.shared.generated.resources.museum_june
import kmpoddscroll.shared.generated.resources.museum_kiss
import kmpoddscroll.shared.generated.resources.museum_poppies
import kmpoddscroll.shared.generated.resources.museum_roses
import kmpoddscroll.shared.generated.resources.museum_seashore
import kmpoddscroll.shared.generated.resources.museum_sunflowers
import kmpoddscroll.shared.generated.resources.museum_venus
import org.jetbrains.compose.resources.imageResource

/**
 * Fourteen bright museum paintings, every one cut 3:2 so they hang at the same
 * height and a width of one and a half of it, ordered so that no painting sits
 * beside one of its own colours — yellow, blue, orange, green, pink, teal, gold
 * and so on round the room, each in a walnut frame behind glass. Credits are
 * in the README.
 *
 * Back steps away from a painting that has been walked up to before it leaves
 * the room.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GalleryWallDemoScreen() {
    val palette = LocalPalette.current
    val state = rememberGalleryWallScrollState()
    BackHandler(enabled = state.inspecting) { state.leave() }

    val sunflowers = imageResource(Res.drawable.museum_sunflowers)
    val boating = imageResource(Res.drawable.museum_boating)
    val june = imageResource(Res.drawable.museum_june)
    val bridge = imageResource(Res.drawable.museum_bridge)
    val roses = imageResource(Res.drawable.museum_roses)
    val armchair = imageResource(Res.drawable.museum_armchair)
    val kiss = imageResource(Res.drawable.museum_kiss)
    val almond = imageResource(Res.drawable.museum_almond)
    val boatingparty = imageResource(Res.drawable.museum_boating_party)
    val cliffwalk = imageResource(Res.drawable.museum_cliff_walk)
    val venus = imageResource(Res.drawable.museum_venus)
    val poppies = imageResource(Res.drawable.museum_poppies)
    val fan = imageResource(Res.drawable.museum_fan)
    val seashore = imageResource(Res.drawable.museum_seashore)

    val items = remember(
        sunflowers, boating, june, bridge, roses, armchair, kiss,
        almond, boatingparty, cliffwalk, venus, poppies, fan, seashore,
    ) {
        listOf(
            GalleryWallItem(sunflowers, "Sunflowers", "Vincent van Gogh", "1888–1889", "Oil on canvas"),
            GalleryWallItem(boating, "Boating", "Édouard Manet", "1874", "Oil on canvas"),
            GalleryWallItem(june, "Flaming June", "Frederic Leighton", "1895", "Oil on canvas"),
            GalleryWallItem(bridge, "Water Lilies and Japanese Bridge", "Claude Monet", "1899", "Oil on canvas"),
            GalleryWallItem(roses, "The Roses of Heliogabalus", "Lawrence Alma-Tadema", "1888", "Oil on canvas"),
            GalleryWallItem(armchair, "Little Girl in a Blue Armchair", "Mary Cassatt", "1878", "Oil on canvas"),
            GalleryWallItem(kiss, "The Kiss", "Gustav Klimt", "1907–1908", "Oil and gold leaf on canvas"),
            GalleryWallItem(almond, "Almond Blossoms", "Vincent van Gogh", "1890", "Oil on canvas"),
            GalleryWallItem(boatingparty, "Luncheon of the Boating Party", "Pierre-Auguste Renoir", "1881", "Oil on canvas"),
            GalleryWallItem(cliffwalk, "The Cliff Walk at Pourville", "Claude Monet", "1882", "Oil on canvas"),
            GalleryWallItem(venus, "The Birth of Venus", "Sandro Botticelli", "c. 1485", "Tempera on canvas"),
            GalleryWallItem(poppies, "Poppy Field", "Claude Monet", "1873", "Oil on canvas"),
            GalleryWallItem(fan, "Lady with a Fan", "Gustav Klimt", "1917–1918", "Oil on canvas"),
            GalleryWallItem(seashore, "Strolling along the Seashore", "Joaquín Sorolla", "1909", "Oil on canvas"),
        )
    }

    GalleryWallScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        background = palette.page,
        contentColor = palette.ink,
        frame = GalleryWallFrame(carving = 1f),
        state = state,
    )
}
