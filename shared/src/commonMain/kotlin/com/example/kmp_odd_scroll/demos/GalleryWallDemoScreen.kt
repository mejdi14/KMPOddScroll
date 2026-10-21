package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.kmp_odd_scroll.LocalPalette
import io.github.mejdi14.oddscroll.gallerywall.GalleryWallItem
import io.github.mejdi14.oddscroll.gallerywall.GalleryWallScroll
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.museum_june
import kmpoddscroll.shared.generated.resources.museum_kandinsky
import kmpoddscroll.shared.generated.resources.museum_kiss
import kmpoddscroll.shared.generated.resources.museum_luncheon
import kmpoddscroll.shared.generated.resources.museum_matthew
import kmpoddscroll.shared.generated.resources.museum_mother
import kmpoddscroll.shared.generated.resources.museum_nightwatch
import kmpoddscroll.shared.generated.resources.museum_olympia
import kmpoddscroll.shared.generated.resources.museum_scream
import kmpoddscroll.shared.generated.resources.museum_seurat
import kmpoddscroll.shared.generated.resources.museum_starry
import kmpoddscroll.shared.generated.resources.museum_venus
import kmpoddscroll.shared.generated.resources.museum_wanderer
import kmpoddscroll.shared.generated.resources.museum_wave
import org.jetbrains.compose.resources.imageResource

/**
 * Fourteen museum masterworks, every one cut 3:2 so they hang at the same size,
 * ordered so that no painting sits beside one of its own colours — blue, orange,
 * green, red, grey, gold and so on round the room. Credits are in the README.
 */
@Composable
fun GalleryWallDemoScreen() {
    val palette = LocalPalette.current
    val starry = imageResource(Res.drawable.museum_starry)
    val june = imageResource(Res.drawable.museum_june)
    val seurat = imageResource(Res.drawable.museum_seurat)
    val scream = imageResource(Res.drawable.museum_scream)
    val mother = imageResource(Res.drawable.museum_mother)
    val kiss = imageResource(Res.drawable.museum_kiss)
    val luncheon = imageResource(Res.drawable.museum_luncheon)
    val wave = imageResource(Res.drawable.museum_wave)
    val nightwatch = imageResource(Res.drawable.museum_nightwatch)
    val venus = imageResource(Res.drawable.museum_venus)
    val matthew = imageResource(Res.drawable.museum_matthew)
    val kandinsky = imageResource(Res.drawable.museum_kandinsky)
    val wanderer = imageResource(Res.drawable.museum_wanderer)
    val olympia = imageResource(Res.drawable.museum_olympia)

    val items = remember(
        starry, june, seurat, scream, mother, kiss, luncheon,
        wave, nightwatch, venus, matthew, kandinsky, wanderer, olympia,
    ) {
        listOf(
            GalleryWallItem(starry, "The Starry Night", "Vincent van Gogh", "1889", "Oil on canvas"),
            GalleryWallItem(june, "Flaming June", "Frederic Leighton", "1895", "Oil on canvas"),
            GalleryWallItem(seurat, "A Sunday Afternoon on the Island of La Grande Jatte", "Georges Seurat", "1884–1886", "Oil on canvas"),
            GalleryWallItem(scream, "The Scream", "Edvard Munch", "1893", "Tempera and crayon on cardboard"),
            GalleryWallItem(mother, "Whistler's Mother", "James McNeill Whistler", "1871", "Oil on canvas"),
            GalleryWallItem(kiss, "The Kiss", "Gustav Klimt", "1907–1908", "Oil and gold leaf on canvas"),
            GalleryWallItem(luncheon, "Le Déjeuner sur l'herbe", "Édouard Manet", "1863", "Oil on canvas"),
            GalleryWallItem(wave, "The Great Wave off Kanagawa", "Katsushika Hokusai", "c. 1831", "Woodblock print"),
            GalleryWallItem(nightwatch, "The Night Watch", "Rembrandt van Rijn", "1642", "Oil on canvas"),
            GalleryWallItem(venus, "The Birth of Venus", "Sandro Botticelli", "c. 1485", "Tempera on canvas"),
            GalleryWallItem(matthew, "The Calling of Saint Matthew", "Caravaggio", "1599–1600", "Oil on canvas"),
            GalleryWallItem(kandinsky, "Composition VII", "Wassily Kandinsky", "1913", "Oil on canvas"),
            GalleryWallItem(wanderer, "Wanderer above the Sea of Fog", "Caspar David Friedrich", "c. 1818", "Oil on canvas"),
            GalleryWallItem(olympia, "Olympia", "Édouard Manet", "1863", "Oil on canvas"),
        )
    }

    GalleryWallScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        background = palette.page,
        contentColor = palette.ink,
    )
}
