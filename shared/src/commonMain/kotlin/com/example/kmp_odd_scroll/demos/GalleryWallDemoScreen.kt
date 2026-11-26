package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.example.kmp_odd_scroll.LocalPalette
import com.example.kmp_odd_scroll.showcase.gallerywall.GalleryWallFrame
import com.example.kmp_odd_scroll.showcase.gallerywall.GalleryWallItem
import com.example.kmp_odd_scroll.showcase.gallerywall.GalleryWallOrnament
import com.example.kmp_odd_scroll.showcase.gallerywall.GalleryWallOrnament.Acanthus
import com.example.kmp_odd_scroll.showcase.gallerywall.GalleryWallOrnament.Classical
import com.example.kmp_odd_scroll.showcase.gallerywall.GalleryWallOrnament.Gadroon
import com.example.kmp_odd_scroll.showcase.gallerywall.GalleryWallOrnament.Shell
import com.example.kmp_odd_scroll.showcase.gallerywall.GalleryWallScene
import com.example.kmp_odd_scroll.showcase.gallerywall.GalleryWallScroll
import com.example.kmp_odd_scroll.showcase.gallerywall.rememberGalleryWallScrollState
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.allDrawableResources
import kmpoddscroll.shared.generated.resources.museum_almond_subjects
import kmpoddscroll.shared.generated.resources.museum_armchair_subjects
import kmpoddscroll.shared.generated.resources.museum_automat_subjects
import kmpoddscroll.shared.generated.resources.museum_boating_party_subjects
import kmpoddscroll.shared.generated.resources.museum_boating_subjects
import kmpoddscroll.shared.generated.resources.museum_bridge_subjects
import kmpoddscroll.shared.generated.resources.museum_cliff_walk_subjects
import kmpoddscroll.shared.generated.resources.museum_fan_subjects
import kmpoddscroll.shared.generated.resources.museum_june_subjects
import kmpoddscroll.shared.generated.resources.museum_kiss_subjects
import kmpoddscroll.shared.generated.resources.museum_pasture_subjects
import kmpoddscroll.shared.generated.resources.museum_poppies_subjects
import kmpoddscroll.shared.generated.resources.museum_roses_subjects
import kmpoddscroll.shared.generated.resources.museum_seashore_subjects
import kmpoddscroll.shared.generated.resources.museum_sunflowers_subjects
import kmpoddscroll.shared.generated.resources.museum_venus_subjects
import kmpoddscroll.shared.generated.resources.museum_vermeer_subjects
import kmpoddscroll.shared.generated.resources.museum_whip_subjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * Museum paintings hung whole at their own aspect, ordered so that no painting sits
 * beside one of its own colours — yellow, blue, orange, green, pink, teal, gold
 * and so on round the room, each in a frame of its own behind glass: gilt in
 * yellow, pale, red, green or antique gold, or carved walnut, mahogany, oak,
 * rosewood or ebonised wood, and carved shell, acanthus, gadroon or classical,
 * no two neighbours alike. The Cliff Walk carries Monet's notes from Pourville
 * on its back and Poppy Field his notes from Argenteuil. Turn over The Art
 * of Painting to see a chalk study of the model holding her book. Each carries a Depth Anything V2 Small map for spatial inspection.
 * The Cliff Walk, Poppy Field, Boys in a Pasture and Snap the Whip are
 * rebuilt as places instead: the coast, the hill and the field, the pasture or
 * the schoolyard, the figures, trees and sails laid out round the spot each
 * was painted from, the paint carried on past its edges and in behind its near
 * things, to walk into and look round. Poppy Field and both Homers go all the
 * way round: the field, the sky and the tree line are brushed on in the
 * painting's own colours wherever it does not show them, and the painting
 * itself lies over them from where it was painted. Homer's two boys sit in the
 * grass a few steps in, the walk goes up past their shoulders, and the rail
 * fence runs on round the pasture. His chain of boys in Snap the Whip runs
 * across the meadow before the red schoolhouse, each boy as far off as his feet
 * say, the walk goes in among them, and the schoolhouse they hid stands whole
 * behind them, the woods going on round. In Automat a short walk brings the
 * viewer beside the table, with the cafeteria built all the way round. In
 * The Art of Painting, the tapestry opens onto Vermeer's model and easel,
 * with a framed chess scene between the windows and two onlookers at the door.
 * Credits are in the README.
 *
 * Back steps out of a painting that has been stepped inside, then away from
 * one that has been walked up to, before it leaves the room.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GalleryWallDemoScreen() {
    val palette = LocalPalette.current
    val state = rememberGalleryWallScrollState()
    BackHandler(enabled = state.looking) {
        if (state.inspected && !state.inside) state.uninspect() else state.leave()
    }

    // Mount the wall once its pictures are ready, including on the web where resources arrive asynchronously.
    val wall = rememberWallPictures()
    val pictures = wall.images
    val baseItems = remember(pictures) {
        pictures?.let { images -> museum.map { it.hang(images) } }
    }
    val selected = if (state.looking) state.subject.mod(museum.size) else null
    var loaded by remember { mutableStateOf<Pair<Int, GalleryWallItem>?>(null) }
    LaunchedEffect(baseItems, selected) {
        if (baseItems != null && selected != null && selected != loaded?.first) {
            loaded = selected to museum[selected].loadDetails(baseItems[selected])
        }
    }
    // Keep the last study through the walk back and an immediate revisit.
    val items = remember(baseItems, loaded) {
        val study = loaded
        if (study == null) baseItems else baseItems?.mapIndexed { index, item ->
            if (index == study.first) study.second else item
        }
    }

    var drawn by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(palette.page)) {
        if (items != null) {
            GalleryWallScroll(
                items = items,
                modifier = Modifier.fillMaxSize(),
                background = palette.page,
                contentColor = palette.ink,
                frame = gilt(YELLOW_GOLD, Shell),
                state = state,
                detailsReady = selected == loaded?.first,
                onWallReady = { drawn = true },
            )
        }
        DemoLoader(
            done = drawn,
            progress = 0.9f * wall.read / wallPictureNames.size,
            paper = palette.page,
            ink = palette.ink,
        )
    }
}

private class MuseumPainting(
    val name: String,
    val title: String,
    val artist: String,
    val date: String,
    val medium: String,
    val frame: GalleryWallFrame,
    val subjectMask: DrawableResource,
    val hasBack: Boolean = false,
    val layers: List<String> = emptyList(),
) {
    fun hang(images: Map<String, ImageBitmap>) = GalleryWallItem(
        images.getValue(name), title, artist, date, medium, frame = frame,
        back = if (hasBack) images.getValue("${name}_back") else null,
        inspectionMaskResource = subjectMask,
    )
}

private class WallPictures {
    var read by mutableIntStateOf(0)
    var images by mutableStateOf<Map<String, ImageBitmap>?>(null)
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun rememberWallPictures(): WallPictures {
    val pictures = remember { WallPictures() }
    LaunchedEffect(Unit) {
        val bytes = readPictures(wallPictureNames)
        val images = HashMap<String, ImageBitmap>(wallPictureNames.size)
        for ((index, name) in wallPictureNames.withIndex()) {
            // Web decodes on the UI thread. Yield between pictures so the loader can repaint.
            withFrameNanos { }
            images[name] = withContext(Dispatchers.Default) { bytes[index].decodeToImageBitmap() }
            pictures.read = index + 1
        }
        pictures.images = images
    }
    return pictures
}

@OptIn(ExperimentalResourceApi::class)
private suspend fun MuseumPainting.loadDetails(item: GalleryWallItem): GalleryWallItem = coroutineScope {
    val names = listOf("${name}_depth") + layers.map { "${name}_$it" }
    val geometry = if (layers.isEmpty()) null else async { Res.readBytes("files/${name}_scene.bin") }
    val bytes = readPictures(names)
    val images = bytes.map { encoded ->
        withFrameNanos { }
        withContext(Dispatchers.Default) { encoded.decodeToImageBitmap() }
    }
    val scene = geometry?.await()?.let { layout ->
        withContext(Dispatchers.Default) { GalleryWallScene(layout, images.drop(1)) }
    }
    item.copy(depth = images.first(), scene = scene)
}

@OptIn(ExperimentalResourceApi::class)
private suspend fun readPictures(names: List<String>): List<ByteArray> = coroutineScope {
    val environment = getSystemResourceEnvironment()
    names.map { name ->
        async { getDrawableResourceBytes(environment, Res.allDrawableResources.getValue(name)) }
    }.awaitAll()
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

// Scene layers follow the texture indices in each painting's geometry file.
private val museum = listOf(
    MuseumPainting("museum_sunflowers", "Sunflowers", "Vincent van Gogh", "1888–1889", "Oil on canvas",
        gilt(YELLOW_GOLD, Shell), Res.drawable.museum_sunflowers_subjects,
    ),
    MuseumPainting("museum_boating", "Boating", "Édouard Manet", "1874", "Oil on canvas",
        wood(WALNUT, Acanthus, width = 0.095f), Res.drawable.museum_boating_subjects,
    ),
    MuseumPainting("museum_june", "Flaming June", "Frederic Leighton", "1895", "Oil on canvas",
        gilt(RED_GOLD, Gadroon, width = 0.105f), Res.drawable.museum_june_subjects,
    ),
    MuseumPainting("museum_bridge", "Water Lilies and Japanese Bridge", "Claude Monet", "1899", "Oil on canvas",
        gilt(PALE_GOLD, Acanthus, width = 0.09f), Res.drawable.museum_bridge_subjects,
    ),
    MuseumPainting("museum_roses", "The Roses of Heliogabalus", "Lawrence Alma-Tadema", "1888", "Oil on canvas",
        wood(MAHOGANY, Shell, width = 0.11f), Res.drawable.museum_roses_subjects,
    ),
    MuseumPainting("museum_pasture", "Boys in a Pasture", "Winslow Homer", "1874", "Oil on canvas",
        gilt(ANTIQUE_GOLD, Acanthus, width = 0.09f), Res.drawable.museum_pasture_subjects,
        layers = listOf("far", "ground", "paint", "band", "cards"),
    ),
    MuseumPainting("museum_armchair", "Little Girl in a Blue Armchair", "Mary Cassatt", "1878", "Oil on canvas",
        gilt(GREEN_GOLD, Gadroon, width = 0.095f), Res.drawable.museum_armchair_subjects,
    ),
    MuseumPainting("museum_kiss", "The Kiss", "Gustav Klimt", "1907–1908", "Oil and gold leaf on canvas",
        wood(EBONISED, Acanthus), Res.drawable.museum_kiss_subjects,
    ),
    MuseumPainting("museum_whip", "Snap the Whip", "Winslow Homer", "1872", "Oil on canvas",
        gilt(PALE_GOLD, Gadroon, width = 0.085f), Res.drawable.museum_whip_subjects,
        layers = listOf("far", "ground", "paint", "band", "cards"),
    ),
    MuseumPainting("museum_almond", "Almond Blossoms", "Vincent van Gogh", "1890", "Oil on canvas",
        gilt(ANTIQUE_GOLD, Shell), Res.drawable.museum_almond_subjects,
    ),
    MuseumPainting("museum_boating_party", "Luncheon of the Boating Party", "Pierre-Auguste Renoir", "1881", "Oil on canvas",
        wood(WALNUT, Gadroon), Res.drawable.museum_boating_party_subjects,
    ),
    MuseumPainting("museum_cliff_walk", "The Cliff Walk at Pourville", "Claude Monet", "1882", "Oil on canvas",
        wood(OAK, Classical, width = 0.07f), Res.drawable.museum_cliff_walk_subjects,
        hasBack = true,
        layers = listOf("far", "ground", "cliff", "cards", "strokes"),
    ),
    MuseumPainting("museum_venus", "The Birth of Venus", "Sandro Botticelli", "c. 1485", "Tempera on canvas",
        gilt(PALE_GOLD, Shell, width = 0.115f), Res.drawable.museum_venus_subjects,
    ),
    MuseumPainting("museum_poppies", "Poppy Field", "Claude Monet", "1873", "Oil on canvas",
        gilt(RED_GOLD, Acanthus, width = 0.095f), Res.drawable.museum_poppies_subjects,
        hasBack = true,
        layers = listOf("far", "ground", "paint", "band", "cards"),
    ),
    MuseumPainting("museum_fan", "Lady with a Fan", "Gustav Klimt", "1917–1918", "Oil on canvas",
        wood(ROSEWOOD, Gadroon), Res.drawable.museum_fan_subjects,
    ),
    MuseumPainting("museum_seashore", "Strolling along the Seashore", "Joaquín Sorolla", "1909", "Oil on canvas",
        gilt(YELLOW_GOLD, Classical, width = 0.075f), Res.drawable.museum_seashore_subjects,
    ),
    MuseumPainting("museum_vermeer", "The Art of Painting", "Johannes Vermeer", "c. 1666–1668", "Oil on canvas",
        gilt(ANTIQUE_GOLD, Shell), Res.drawable.museum_vermeer_subjects,
        hasBack = true,
        layers = listOf("front", "walls", "surfaces", "figures", "studio"),
    ),
    MuseumPainting("museum_automat", "Automat", "Edward Hopper", "1927", "Oil on canvas",
        GalleryWallFrame(color = EBONISED), Res.drawable.museum_automat_subjects,
        layers = listOf("front", "left", "rear", "right", "surfaces", "table"),
    ),
)

private val wallPictureNames = museum.flatMap {
    if (it.hasBack) listOf(it.name, "${it.name}_back") else listOf(it.name)
}
