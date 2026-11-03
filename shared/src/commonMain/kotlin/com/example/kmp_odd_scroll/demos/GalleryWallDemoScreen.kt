package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import com.example.kmp_odd_scroll.LocalPalette
import io.github.mejdi14.oddscroll.gallerywall.GalleryWallFrame
import io.github.mejdi14.oddscroll.gallerywall.GalleryWallItem
import io.github.mejdi14.oddscroll.gallerywall.GalleryWallOrnament
import io.github.mejdi14.oddscroll.gallerywall.GalleryWallOrnament.Acanthus
import io.github.mejdi14.oddscroll.gallerywall.GalleryWallOrnament.Classical
import io.github.mejdi14.oddscroll.gallerywall.GalleryWallOrnament.Gadroon
import io.github.mejdi14.oddscroll.gallerywall.GalleryWallOrnament.Shell
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
 * and so on round the room, each in a frame of its own behind glass: gilt in
 * yellow, pale, red, green or antique gold, or carved walnut, mahogany, oak,
 * rosewood or ebonised wood, and carved shell, acanthus, gadroon or classical,
 * no two neighbours alike. Credits are in the README.
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
            GalleryWallItem(sunflowers, "Sunflowers", "Vincent van Gogh", "1888–1889", "Oil on canvas", frame = gilt(YELLOW_GOLD, Shell)),
            GalleryWallItem(boating, "Boating", "Édouard Manet", "1874", "Oil on canvas", frame = wood(WALNUT, Acanthus, width = 0.095f)),
            GalleryWallItem(june, "Flaming June", "Frederic Leighton", "1895", "Oil on canvas", frame = gilt(RED_GOLD, Gadroon, width = 0.105f)),
            GalleryWallItem(bridge, "Water Lilies and Japanese Bridge", "Claude Monet", "1899", "Oil on canvas", frame = gilt(PALE_GOLD, Acanthus, width = 0.09f)),
            GalleryWallItem(roses, "The Roses of Heliogabalus", "Lawrence Alma-Tadema", "1888", "Oil on canvas", frame = wood(MAHOGANY, Shell, width = 0.11f)),
            GalleryWallItem(armchair, "Little Girl in a Blue Armchair", "Mary Cassatt", "1878", "Oil on canvas", frame = gilt(GREEN_GOLD, Gadroon, width = 0.095f)),
            GalleryWallItem(kiss, "The Kiss", "Gustav Klimt", "1907–1908", "Oil and gold leaf on canvas", frame = wood(EBONISED, Acanthus)),
            GalleryWallItem(almond, "Almond Blossoms", "Vincent van Gogh", "1890", "Oil on canvas", frame = gilt(ANTIQUE_GOLD, Shell)),
            GalleryWallItem(boatingparty, "Luncheon of the Boating Party", "Pierre-Auguste Renoir", "1881", "Oil on canvas", frame = wood(WALNUT, Gadroon)),
            GalleryWallItem(cliffwalk, "The Cliff Walk at Pourville", "Claude Monet", "1882", "Oil on canvas", frame = wood(OAK, Classical, width = 0.07f)),
            GalleryWallItem(venus, "The Birth of Venus", "Sandro Botticelli", "c. 1485", "Tempera on canvas", frame = gilt(PALE_GOLD, Shell, width = 0.115f)),
            GalleryWallItem(poppies, "Poppy Field", "Claude Monet", "1873", "Oil on canvas", frame = gilt(RED_GOLD, Acanthus, width = 0.095f)),
            GalleryWallItem(fan, "Lady with a Fan", "Gustav Klimt", "1917–1918", "Oil on canvas", frame = wood(ROSEWOOD, Gadroon)),
            GalleryWallItem(seashore, "Strolling along the Seashore", "Joaquín Sorolla", "1909", "Oil on canvas", frame = gilt(YELLOW_GOLD, Classical, width = 0.075f)),
        )
    }

    GalleryWallScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        background = palette.page,
        contentColor = palette.ink,
        frame = gilt(YELLOW_GOLD, Shell),
        state = state,
    )
}

private val YELLOW_GOLD = Color(0xFFD0A13A)
private val PALE_GOLD = Color(0xFFDCC07A)
private val RED_GOLD = Color(0xFFC0823A)
private val GREEN_GOLD = Color(0xFFB4A04E)
private val ANTIQUE_GOLD = Color(0xFFA67C2E)

private val WALNUT = Color(0xFF7B4F2C)
private val MAHOGANY = Color(0xFF6E2E1E)
private val OAK = Color(0xFFA57A48)
private val ROSEWOOD = Color(0xFF4E2A22)
private val EBONISED = Color(0xFF2A211C)

/** Gold leaf over a deep moulding, its gold smooth, no grain under it. */
private fun gilt(gold: Color, ornament: GalleryWallOrnament, width: Float = 0.1f) = GalleryWallFrame(
    width = width,
    color = gold,
    depth = 0.06f,
    grain = 0f,
    recess = 0.02f,
    carving = 1f,
    ornament = ornament,
    gilt = true,
)

/** The same carving in bare, waxed wood, the grain showing. */
private fun wood(color: Color, ornament: GalleryWallOrnament, width: Float = 0.1f) = GalleryWallFrame(
    width = width,
    color = color,
    depth = 0.06f,
    recess = 0.02f,
    carving = 1f,
    ornament = ornament,
)
