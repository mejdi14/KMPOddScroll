package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    // Read in by hand rather than through imageResource, which on the web
    // hands back a blank picture first and swaps each real one in as it
    // arrives — every swap a new hang, and the frames baked again. The
    // paintings come first, behind the loader; their depth maps and the
    // scenes walked into follow once the wall is up, a picture a frame.
    val wall = rememberPictures(WALL_PICTURES)
    val deep = rememberPictures(DEEP_PICTURES, start = wall.images != null, spread = true)
    val layouts by produceState<Map<String, ByteArray>?>(null, deep.images) {
        if (deep.images != null) value = SCENES.associateWith { Res.readBytes("files/museum_${it}_scene.bin") }
    }
    val items = remember(wall.images, deep.images, layouts) {
        wall.images?.let { museum(it, deep.images?.let { d -> layouts?.let { l -> d to l } }) }
    }

    Box(Modifier.fillMaxSize().background(palette.page)) {
        if (items != null) {
            GalleryWallScroll(
                items = items,
                modifier = Modifier.fillMaxSize(),
                background = palette.page,
                contentColor = palette.ink,
                frame = gilt(YELLOW_GOLD, Shell),
                state = state,
            )
        }
        // Held over the first frame of the wall too, which bakes every frame
        // and label once and is the one long frame it has.
        var drawn by remember { mutableStateOf(false) }
        LaunchedEffect(items != null) {
            if (items == null) return@LaunchedEffect
            repeat(2) { withFrameNanos { } }
            drawn = true
        }
        DemoLoader(
            done = drawn,
            progress = 0.9f * wall.read / WALL_PICTURES.size,
            paper = palette.page,
            ink = palette.ink,
        )
    }
}

/** The paintings as they hang on the wall, fronts and backs. */
private val WALL_PICTURES = listOf(
    "museum_sunflowers",
    "museum_boating",
    "museum_june",
    "museum_bridge",
    "museum_roses",
    "museum_armchair",
    "museum_kiss",
    "museum_almond",
    "museum_boating_party",
    "museum_cliff_walk",
    "museum_cliff_walk_back",
    "museum_venus",
    "museum_poppies",
    "museum_poppies_back",
    "museum_fan",
    "museum_seashore",
    "museum_vermeer",
    "museum_vermeer_back",
    "museum_pasture",
    "museum_whip",
    "museum_automat",
)

/** Everything for stepping inside: each painting's depth map and the layers of the scenes walked into. */
private val DEEP_PICTURES = listOf(
    "museum_sunflowers_depth",
    "museum_boating_depth",
    "museum_june_depth",
    "museum_bridge_depth",
    "museum_roses_depth",
    "museum_armchair_depth",
    "museum_kiss_depth",
    "museum_almond_depth",
    "museum_boating_party_depth",
    "museum_cliff_walk_depth",
    "museum_venus_depth",
    "museum_poppies_depth",
    "museum_fan_depth",
    "museum_seashore_depth",
    "museum_pasture_depth",
    "museum_whip_depth",
    "museum_vermeer_depth",
    "museum_automat_depth",
    "museum_cliff_walk_far",
    "museum_cliff_walk_ground",
    "museum_cliff_walk_cliff",
    "museum_cliff_walk_cards",
    "museum_cliff_walk_strokes",
    "museum_poppies_far",
    "museum_poppies_ground",
    "museum_poppies_paint",
    "museum_poppies_band",
    "museum_poppies_cards",
    "museum_pasture_far",
    "museum_pasture_ground",
    "museum_pasture_paint",
    "museum_pasture_band",
    "museum_pasture_cards",
    "museum_whip_far",
    "museum_whip_ground",
    "museum_whip_paint",
    "museum_whip_band",
    "museum_whip_cards",
    "museum_automat_front",
    "museum_automat_left",
    "museum_automat_rear",
    "museum_automat_right",
    "museum_automat_surfaces",
    "museum_automat_table",
    "museum_vermeer_front",
    "museum_vermeer_walls",
    "museum_vermeer_surfaces",
    "museum_vermeer_figures",
    "museum_vermeer_studio",
)

/** The paintings rebuilt as places, each laid out by `files/museum_<name>_scene.bin`. */
private val SCENES = listOf("cliff_walk", "poppies", "pasture", "whip", "automat", "vermeer")

/**
 * The hang, out of the [wall] pictures; with [deep] — its pictures and the
 * scenes' layouts — each painting can be stepped inside as well.
 */
private fun museum(
    wall: Map<String, ImageBitmap>,
    deep: Pair<Map<String, ImageBitmap>, Map<String, ByteArray>>?,
): List<GalleryWallItem> {
    fun scene(name: String, vararg layers: String): GalleryWallScene? = deep?.let { (pictures, layouts) ->
        GalleryWallScene(layouts.getValue(name), layers.map { pictures.getValue(it) })
    }
    val depth = deep?.first
    val sunflowers = wall.getValue("museum_sunflowers")
    val boating = wall.getValue("museum_boating")
    val june = wall.getValue("museum_june")
    val bridge = wall.getValue("museum_bridge")
    val roses = wall.getValue("museum_roses")
    val armchair = wall.getValue("museum_armchair")
    val kiss = wall.getValue("museum_kiss")
    val almond = wall.getValue("museum_almond")
    val boatingparty = wall.getValue("museum_boating_party")
    val cliffwalk = wall.getValue("museum_cliff_walk")
    val cliffwalkBack = wall.getValue("museum_cliff_walk_back")
    val venus = wall.getValue("museum_venus")
    val poppies = wall.getValue("museum_poppies")
    val poppiesBack = wall.getValue("museum_poppies_back")
    val fan = wall.getValue("museum_fan")
    val seashore = wall.getValue("museum_seashore")
    val vermeer = wall.getValue("museum_vermeer")
    val vermeerBack = wall.getValue("museum_vermeer_back")
    val pasture = wall.getValue("museum_pasture")
    val whip = wall.getValue("museum_whip")
    val automat = wall.getValue("museum_automat")
    val sunflowersDepth = depth?.get("museum_sunflowers_depth")
    val boatingDepth = depth?.get("museum_boating_depth")
    val juneDepth = depth?.get("museum_june_depth")
    val bridgeDepth = depth?.get("museum_bridge_depth")
    val rosesDepth = depth?.get("museum_roses_depth")
    val armchairDepth = depth?.get("museum_armchair_depth")
    val kissDepth = depth?.get("museum_kiss_depth")
    val almondDepth = depth?.get("museum_almond_depth")
    val boatingpartyDepth = depth?.get("museum_boating_party_depth")
    val cliffwalkDepth = depth?.get("museum_cliff_walk_depth")
    val venusDepth = depth?.get("museum_venus_depth")
    val poppiesDepth = depth?.get("museum_poppies_depth")
    val fanDepth = depth?.get("museum_fan_depth")
    val seashoreDepth = depth?.get("museum_seashore_depth")
    val pastureDepth = depth?.get("museum_pasture_depth")
    val whipDepth = depth?.get("museum_whip_depth")
    val vermeerDepth = depth?.get("museum_vermeer_depth")
    val automatDepth = depth?.get("museum_automat_depth")
    val cliffwalkScene = scene("cliff_walk", "museum_cliff_walk_far", "museum_cliff_walk_ground", "museum_cliff_walk_cliff", "museum_cliff_walk_cards", "museum_cliff_walk_strokes")
    val poppiesScene = scene("poppies", "museum_poppies_far", "museum_poppies_ground", "museum_poppies_paint", "museum_poppies_band", "museum_poppies_cards")
    val pastureScene = scene("pasture", "museum_pasture_far", "museum_pasture_ground", "museum_pasture_paint", "museum_pasture_band", "museum_pasture_cards")
    val whipScene = scene("whip", "museum_whip_far", "museum_whip_ground", "museum_whip_paint", "museum_whip_band", "museum_whip_cards")
    val automatScene = scene("automat", "museum_automat_front", "museum_automat_left", "museum_automat_rear", "museum_automat_right", "museum_automat_surfaces", "museum_automat_table")
    val vermeerScene = scene("vermeer", "museum_vermeer_front", "museum_vermeer_walls", "museum_vermeer_surfaces", "museum_vermeer_figures", "museum_vermeer_studio")

    return listOf(
        GalleryWallItem(sunflowers, "Sunflowers", "Vincent van Gogh", "1888–1889", "Oil on canvas", frame = gilt(YELLOW_GOLD, Shell), depth = sunflowersDepth,
            inspectionMaskResource = Res.drawable.museum_sunflowers_subjects,
        ),
        GalleryWallItem(boating, "Boating", "Édouard Manet", "1874", "Oil on canvas", frame = wood(WALNUT, Acanthus, width = 0.095f), depth = boatingDepth,
            inspectionMaskResource = Res.drawable.museum_boating_subjects,
        ),
        GalleryWallItem(june, "Flaming June", "Frederic Leighton", "1895", "Oil on canvas", frame = gilt(RED_GOLD, Gadroon, width = 0.105f), depth = juneDepth,
            inspectionMaskResource = Res.drawable.museum_june_subjects,
        ),
        GalleryWallItem(bridge, "Water Lilies and Japanese Bridge", "Claude Monet", "1899", "Oil on canvas", frame = gilt(PALE_GOLD, Acanthus, width = 0.09f), depth = bridgeDepth,
            inspectionMaskResource = Res.drawable.museum_bridge_subjects,
        ),
        GalleryWallItem(roses, "The Roses of Heliogabalus", "Lawrence Alma-Tadema", "1888", "Oil on canvas", frame = wood(MAHOGANY, Shell, width = 0.11f), depth = rosesDepth,
            inspectionMaskResource = Res.drawable.museum_roses_subjects,
        ),
        GalleryWallItem(pasture, "Boys in a Pasture", "Winslow Homer", "1874", "Oil on canvas", frame = gilt(ANTIQUE_GOLD, Acanthus, width = 0.09f), depth = pastureDepth, scene = pastureScene,
            inspectionMaskResource = Res.drawable.museum_pasture_subjects,
        ),
        GalleryWallItem(armchair, "Little Girl in a Blue Armchair", "Mary Cassatt", "1878", "Oil on canvas", frame = gilt(GREEN_GOLD, Gadroon, width = 0.095f), depth = armchairDepth,
            inspectionMaskResource = Res.drawable.museum_armchair_subjects,
        ),
        GalleryWallItem(kiss, "The Kiss", "Gustav Klimt", "1907–1908", "Oil and gold leaf on canvas", frame = wood(EBONISED, Acanthus), depth = kissDepth,
            inspectionMaskResource = Res.drawable.museum_kiss_subjects,
        ),
        GalleryWallItem(whip, "Snap the Whip", "Winslow Homer", "1872", "Oil on canvas", frame = gilt(PALE_GOLD, Gadroon, width = 0.085f), depth = whipDepth, scene = whipScene,
            inspectionMaskResource = Res.drawable.museum_whip_subjects,
        ),
        GalleryWallItem(almond, "Almond Blossoms", "Vincent van Gogh", "1890", "Oil on canvas", frame = gilt(ANTIQUE_GOLD, Shell), depth = almondDepth,
            inspectionMaskResource = Res.drawable.museum_almond_subjects,
        ),
        GalleryWallItem(boatingparty, "Luncheon of the Boating Party", "Pierre-Auguste Renoir", "1881", "Oil on canvas", frame = wood(WALNUT, Gadroon), depth = boatingpartyDepth,
            inspectionMaskResource = Res.drawable.museum_boating_party_subjects,
        ),
        GalleryWallItem(cliffwalk, "The Cliff Walk at Pourville", "Claude Monet", "1882", "Oil on canvas", frame = wood(OAK, Classical, width = 0.07f), back = cliffwalkBack, depth = cliffwalkDepth, scene = cliffwalkScene,
            inspectionMaskResource = Res.drawable.museum_cliff_walk_subjects,
        ),
        GalleryWallItem(venus, "The Birth of Venus", "Sandro Botticelli", "c. 1485", "Tempera on canvas", frame = gilt(PALE_GOLD, Shell, width = 0.115f), depth = venusDepth,
            inspectionMaskResource = Res.drawable.museum_venus_subjects,
        ),
        GalleryWallItem(poppies, "Poppy Field", "Claude Monet", "1873", "Oil on canvas", frame = gilt(RED_GOLD, Acanthus, width = 0.095f), back = poppiesBack, depth = poppiesDepth, scene = poppiesScene,
            inspectionMaskResource = Res.drawable.museum_poppies_subjects,
        ),
        GalleryWallItem(fan, "Lady with a Fan", "Gustav Klimt", "1917–1918", "Oil on canvas", frame = wood(ROSEWOOD, Gadroon), depth = fanDepth,
            inspectionMaskResource = Res.drawable.museum_fan_subjects,
        ),
        GalleryWallItem(seashore, "Strolling along the Seashore", "Joaquín Sorolla", "1909", "Oil on canvas", frame = gilt(YELLOW_GOLD, Classical, width = 0.075f), depth = seashoreDepth,
            inspectionMaskResource = Res.drawable.museum_seashore_subjects,
        ),
        GalleryWallItem(vermeer, "The Art of Painting", "Johannes Vermeer", "c. 1666–1668", "Oil on canvas", frame = gilt(ANTIQUE_GOLD, Shell), back = vermeerBack, depth = vermeerDepth, scene = vermeerScene,
            inspectionMaskResource = Res.drawable.museum_vermeer_subjects,
        ),
        GalleryWallItem(automat, "Automat", "Edward Hopper", "1927", "Oil on canvas", frame = GalleryWallFrame(color = EBONISED), depth = automatDepth, scene = automatScene,
            inspectionMaskResource = Res.drawable.museum_automat_subjects,
        ),
    )
}

/** Pictures read in by name, all of them or none: [images] is null until the last is in. */
private class Pictures {
    var read by mutableIntStateOf(0)
    var images by mutableStateOf<Map<String, ImageBitmap>?>(null)
}

/**
 * The drawables [names] read in once [start] is true, fetched together and
 * decoded in turn; [spread] decodes one a frame, for reading in while the
 * demo is already running.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
private fun rememberPictures(names: List<String>, start: Boolean = true, spread: Boolean = false): Pictures {
    val pictures = remember { Pictures() }
    LaunchedEffect(start) {
        if (!start || pictures.images != null) return@LaunchedEffect
        val environment = getSystemResourceEnvironment()
        val bytes = coroutineScope {
            names.map { name -> async { getDrawableResourceBytes(environment, Res.allDrawableResources.getValue(name)) } }.awaitAll()
        }
        val images = HashMap<String, ImageBitmap>(names.size)
        for ((n, name) in names.withIndex()) {
            if (spread) withFrameNanos { }
            images[name] = bytes[n].decodeToImageBitmap()
            pictures.read = n + 1
        }
        pictures.images = images
    }
    return pictures
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
