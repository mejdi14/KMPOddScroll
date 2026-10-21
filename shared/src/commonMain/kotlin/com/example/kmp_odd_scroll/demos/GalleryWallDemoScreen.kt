package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.kmp_odd_scroll.LocalPalette
import io.github.mejdi14.oddscroll.gallerywall.GalleryWallItem
import io.github.mejdi14.oddscroll.gallerywall.GalleryWallScroll
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.art_cowherd
import kmpoddscroll.shared.generated.resources.art_cypresses
import kmpoddscroll.shared.generated.resources.art_farmhouse
import kmpoddscroll.shared.generated.resources.art_night
import kmpoddscroll.shared.generated.resources.art_village
import kmpoddscroll.shared.generated.resources.art_wheatfield
import kmpoddscroll.shared.generated.resources.grid_basalt
import kmpoddscroll.shared.generated.resources.grid_falls
import kmpoddscroll.shared.generated.resources.grid_forest
import kmpoddscroll.shared.generated.resources.wide_peaks
import kmpoddscroll.shared.generated.resources.wide_sounds
import kmpoddscroll.shared.generated.resources.wide_towers
import org.jetbrains.compose.resources.imageResource

/**
 * The museum scans hung between photographs cut three ways — portrait, 4:3 and
 * 2:1 — at sizes spread over the 0.72 to 1 the reference's paintings come in,
 * so no two neighbours make the same shape on the wall. Credits are in the
 * README.
 */
@Composable
fun GalleryWallDemoScreen() {
    val palette = LocalPalette.current
    val wheatfield = imageResource(Res.drawable.art_wheatfield)
    val night = imageResource(Res.drawable.art_night)
    val farmhouse = imageResource(Res.drawable.art_farmhouse)
    val village = imageResource(Res.drawable.art_village)
    val cowherd = imageResource(Res.drawable.art_cowherd)
    val cypresses = imageResource(Res.drawable.art_cypresses)
    val falls = imageResource(Res.drawable.grid_falls)
    val forest = imageResource(Res.drawable.grid_forest)
    val basalt = imageResource(Res.drawable.grid_basalt)
    val sounds = imageResource(Res.drawable.wide_sounds)
    val peaks = imageResource(Res.drawable.wide_peaks)
    val towers = imageResource(Res.drawable.wide_towers)

    val items = remember(wheatfield, night, farmhouse, village, cowherd, cypresses, falls, forest, basalt, sounds, peaks, towers) {
        listOf(
            GalleryWallItem(cypresses, "Wheat Field with Cypresses", "Vincent van Gogh", "1889", "Oil on canvas", scale = 0.86f),
            GalleryWallItem(falls, "Golden Falls", "Ingrid Eich", medium = "Photograph", scale = 1f),
            GalleryWallItem(night, "Night with her Train of Stars", "Edward Robert Hughes", "1912", "Watercolour", scale = 0.94f),
            GalleryWallItem(sounds, "Marlborough Sounds", "Samuel Harrington", medium = "Photograph", scale = 0.78f),
            GalleryWallItem(farmhouse, "Farmhouse in Provence", "Vincent van Gogh", "1888", "Oil on canvas", scale = 0.81f),
            GalleryWallItem(forest, "Forest Path", "Wolfgang Hasselmann", medium = "Photograph", scale = 0.92f),
            GalleryWallItem(village, "Harbour Village", "Birmingham Museums Trust", scale = 0.74f),
            GalleryWallItem(peaks, "Above the Clouds", "Maciej Marko", medium = "Photograph", scale = 0.84f),
            GalleryWallItem(wheatfield, "Green Wheat Fields", "Vincent van Gogh", medium = "Oil on canvas", scale = 0.97f),
            GalleryWallItem(basalt, "Basalt Columns", "Adrien Olichon", medium = "Photograph", scale = 0.88f),
            GalleryWallItem(cowherd, "The Cowherd", "The Metropolitan Museum of Art", scale = 0.79f),
            GalleryWallItem(towers, "City Towers", "Heye Jensen", medium = "Photograph", scale = 0.76f),
        )
    }

    GalleryWallScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        background = palette.page,
        contentColor = palette.ink,
    )
}
