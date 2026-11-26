package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
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
import kmpoddscroll.shared.generated.resources.museum_almond
import kmpoddscroll.shared.generated.resources.museum_armchair
import kmpoddscroll.shared.generated.resources.museum_automat
import kmpoddscroll.shared.generated.resources.museum_automat_front
import kmpoddscroll.shared.generated.resources.museum_automat_left
import kmpoddscroll.shared.generated.resources.museum_automat_rear
import kmpoddscroll.shared.generated.resources.museum_automat_right
import kmpoddscroll.shared.generated.resources.museum_automat_surfaces
import kmpoddscroll.shared.generated.resources.museum_automat_table
import kmpoddscroll.shared.generated.resources.museum_boating
import kmpoddscroll.shared.generated.resources.museum_boating_party
import kmpoddscroll.shared.generated.resources.museum_bridge
import kmpoddscroll.shared.generated.resources.museum_cliff_walk
import kmpoddscroll.shared.generated.resources.museum_cliff_walk_back
import kmpoddscroll.shared.generated.resources.museum_cliff_walk_cards
import kmpoddscroll.shared.generated.resources.museum_cliff_walk_cliff
import kmpoddscroll.shared.generated.resources.museum_cliff_walk_far
import kmpoddscroll.shared.generated.resources.museum_cliff_walk_ground
import kmpoddscroll.shared.generated.resources.museum_cliff_walk_strokes
import kmpoddscroll.shared.generated.resources.museum_fan
import kmpoddscroll.shared.generated.resources.museum_june
import kmpoddscroll.shared.generated.resources.museum_kiss
import kmpoddscroll.shared.generated.resources.museum_pasture
import kmpoddscroll.shared.generated.resources.museum_pasture_band
import kmpoddscroll.shared.generated.resources.museum_pasture_cards
import kmpoddscroll.shared.generated.resources.museum_pasture_far
import kmpoddscroll.shared.generated.resources.museum_pasture_ground
import kmpoddscroll.shared.generated.resources.museum_pasture_paint
import kmpoddscroll.shared.generated.resources.museum_poppies
import kmpoddscroll.shared.generated.resources.museum_poppies_back
import kmpoddscroll.shared.generated.resources.museum_poppies_band
import kmpoddscroll.shared.generated.resources.museum_poppies_cards
import kmpoddscroll.shared.generated.resources.museum_poppies_far
import kmpoddscroll.shared.generated.resources.museum_poppies_ground
import kmpoddscroll.shared.generated.resources.museum_poppies_paint
import kmpoddscroll.shared.generated.resources.museum_roses
import kmpoddscroll.shared.generated.resources.museum_seashore
import kmpoddscroll.shared.generated.resources.museum_sunflowers
import kmpoddscroll.shared.generated.resources.museum_venus
import kmpoddscroll.shared.generated.resources.museum_vermeer
import kmpoddscroll.shared.generated.resources.museum_vermeer_back
import kmpoddscroll.shared.generated.resources.museum_vermeer_figures
import kmpoddscroll.shared.generated.resources.museum_vermeer_front
import kmpoddscroll.shared.generated.resources.museum_vermeer_studio
import kmpoddscroll.shared.generated.resources.museum_vermeer_surfaces
import kmpoddscroll.shared.generated.resources.museum_vermeer_walls
import kmpoddscroll.shared.generated.resources.museum_whip
import kmpoddscroll.shared.generated.resources.museum_whip_band
import kmpoddscroll.shared.generated.resources.museum_whip_cards
import kmpoddscroll.shared.generated.resources.museum_whip_far
import kmpoddscroll.shared.generated.resources.museum_whip_ground
import kmpoddscroll.shared.generated.resources.museum_whip_paint
import kmpoddscroll.shared.generated.resources.museum_almond_depth
import kmpoddscroll.shared.generated.resources.museum_armchair_depth
import kmpoddscroll.shared.generated.resources.museum_boating_depth
import kmpoddscroll.shared.generated.resources.museum_boating_party_depth
import kmpoddscroll.shared.generated.resources.museum_bridge_depth
import kmpoddscroll.shared.generated.resources.museum_cliff_walk_depth
import kmpoddscroll.shared.generated.resources.museum_fan_depth
import kmpoddscroll.shared.generated.resources.museum_june_depth
import kmpoddscroll.shared.generated.resources.museum_kiss_depth
import kmpoddscroll.shared.generated.resources.museum_poppies_depth
import kmpoddscroll.shared.generated.resources.museum_roses_depth
import kmpoddscroll.shared.generated.resources.museum_seashore_depth
import kmpoddscroll.shared.generated.resources.museum_sunflowers_depth
import kmpoddscroll.shared.generated.resources.museum_venus_depth
import kmpoddscroll.shared.generated.resources.museum_sunflowers_subjects
import kmpoddscroll.shared.generated.resources.museum_boating_subjects
import kmpoddscroll.shared.generated.resources.museum_june_subjects
import kmpoddscroll.shared.generated.resources.museum_bridge_subjects
import kmpoddscroll.shared.generated.resources.museum_roses_subjects
import kmpoddscroll.shared.generated.resources.museum_pasture_subjects
import kmpoddscroll.shared.generated.resources.museum_armchair_subjects
import kmpoddscroll.shared.generated.resources.museum_kiss_subjects
import kmpoddscroll.shared.generated.resources.museum_whip_subjects
import kmpoddscroll.shared.generated.resources.museum_almond_subjects
import kmpoddscroll.shared.generated.resources.museum_boating_party_subjects
import kmpoddscroll.shared.generated.resources.museum_cliff_walk_subjects
import kmpoddscroll.shared.generated.resources.museum_venus_subjects
import kmpoddscroll.shared.generated.resources.museum_poppies_subjects
import kmpoddscroll.shared.generated.resources.museum_fan_subjects
import kmpoddscroll.shared.generated.resources.museum_seashore_subjects
import kmpoddscroll.shared.generated.resources.museum_vermeer_subjects
import kmpoddscroll.shared.generated.resources.museum_automat_subjects
import kmpoddscroll.shared.generated.resources.museum_pasture_depth
import kmpoddscroll.shared.generated.resources.museum_whip_depth
import kmpoddscroll.shared.generated.resources.museum_vermeer_depth
import kmpoddscroll.shared.generated.resources.museum_automat_depth
import org.jetbrains.compose.resources.imageResource

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
    BackHandler(enabled = state.looking) { state.leave() }

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
    val cliffwalkBack = imageResource(Res.drawable.museum_cliff_walk_back)
    val venus = imageResource(Res.drawable.museum_venus)
    val poppies = imageResource(Res.drawable.museum_poppies)
    val poppiesBack = imageResource(Res.drawable.museum_poppies_back)
    val fan = imageResource(Res.drawable.museum_fan)
    val seashore = imageResource(Res.drawable.museum_seashore)
    val vermeer = imageResource(Res.drawable.museum_vermeer)
    val vermeerBack = imageResource(Res.drawable.museum_vermeer_back)

    // Depth Anything V2 Small, white near and black far, for stepping inside.
    val sunflowersDepth = imageResource(Res.drawable.museum_sunflowers_depth)
    val boatingDepth = imageResource(Res.drawable.museum_boating_depth)
    val juneDepth = imageResource(Res.drawable.museum_june_depth)
    val bridgeDepth = imageResource(Res.drawable.museum_bridge_depth)
    val rosesDepth = imageResource(Res.drawable.museum_roses_depth)
    val armchairDepth = imageResource(Res.drawable.museum_armchair_depth)
    val kissDepth = imageResource(Res.drawable.museum_kiss_depth)
    val almondDepth = imageResource(Res.drawable.museum_almond_depth)
    val boatingpartyDepth = imageResource(Res.drawable.museum_boating_party_depth)
    val cliffwalkDepth = imageResource(Res.drawable.museum_cliff_walk_depth)
    val venusDepth = imageResource(Res.drawable.museum_venus_depth)
    val poppiesDepth = imageResource(Res.drawable.museum_poppies_depth)
    val fanDepth = imageResource(Res.drawable.museum_fan_depth)
    val seashoreDepth = imageResource(Res.drawable.museum_seashore_depth)

    // The Cliff Walk as a place: sky and sea, meadow, cliffs, figures and sails, the sea's brushwork, and how they lie.
    val cliffwalkFar = imageResource(Res.drawable.museum_cliff_walk_far)
    val cliffwalkGround = imageResource(Res.drawable.museum_cliff_walk_ground)
    val cliffwalkCliff = imageResource(Res.drawable.museum_cliff_walk_cliff)
    val cliffwalkCards = imageResource(Res.drawable.museum_cliff_walk_cards)
    val cliffwalkStrokes = imageResource(Res.drawable.museum_cliff_walk_strokes)
    val cliffwalkLayout by produceState<ByteArray?>(null) { value = Res.readBytes("files/museum_cliff_walk_scene.bin") }
    val cliffwalkScene = remember(cliffwalkLayout, cliffwalkFar, cliffwalkGround, cliffwalkCliff, cliffwalkCards, cliffwalkStrokes) {
        cliffwalkLayout?.let {
            GalleryWallScene(it, listOf(cliffwalkFar, cliffwalkGround, cliffwalkCliff, cliffwalkCards, cliffwalkStrokes))
        }
    }

    // Poppy Field as a place all the way round: the sky, the field, the painting itself laid back over both,
    // the far tree line, the hill's trees and the figures, and how they lie.
    val poppiesFar = imageResource(Res.drawable.museum_poppies_far)
    val poppiesGround = imageResource(Res.drawable.museum_poppies_ground)
    val poppiesPaint = imageResource(Res.drawable.museum_poppies_paint)
    val poppiesBand = imageResource(Res.drawable.museum_poppies_band)
    val poppiesCards = imageResource(Res.drawable.museum_poppies_cards)
    val poppiesLayout by produceState<ByteArray?>(null) { value = Res.readBytes("files/museum_poppies_scene.bin") }
    val poppiesScene = remember(poppiesLayout, poppiesFar, poppiesGround, poppiesPaint, poppiesBand, poppiesCards) {
        poppiesLayout?.let { GalleryWallScene(it, listOf(poppiesFar, poppiesGround, poppiesPaint, poppiesBand, poppiesCards)) }
    }

    // Boys in a Pasture the same way, all the way round: the sky, the pasture, the painting laid back over both,
    // the fence and tree line, the two boys and the shade trees, and how they lie.
    val pasture = imageResource(Res.drawable.museum_pasture)
    val pastureFar = imageResource(Res.drawable.museum_pasture_far)
    val pastureGround = imageResource(Res.drawable.museum_pasture_ground)
    val pasturePaint = imageResource(Res.drawable.museum_pasture_paint)
    val pastureBand = imageResource(Res.drawable.museum_pasture_band)
    val pastureCards = imageResource(Res.drawable.museum_pasture_cards)
    val pastureLayout by produceState<ByteArray?>(null) { value = Res.readBytes("files/museum_pasture_scene.bin") }
    val pastureScene = remember(pastureLayout, pastureFar, pastureGround, pasturePaint, pastureBand, pastureCards) {
        pastureLayout?.let { GalleryWallScene(it, listOf(pastureFar, pastureGround, pasturePaint, pastureBand, pastureCards)) }
    }

    // Snap the Whip the same way: the sky, the meadow and the schoolyard, the painting laid back over both, the
    // schoolhouse, woods and far fields, the chain of boys and the fallen one, and how they lie.
    val whip = imageResource(Res.drawable.museum_whip)
    val whipFar = imageResource(Res.drawable.museum_whip_far)
    val whipGround = imageResource(Res.drawable.museum_whip_ground)
    val whipPaint = imageResource(Res.drawable.museum_whip_paint)
    val whipBand = imageResource(Res.drawable.museum_whip_band)
    val whipCards = imageResource(Res.drawable.museum_whip_cards)
    val whipLayout by produceState<ByteArray?>(null) { value = Res.readBytes("files/museum_whip_scene.bin") }
    val whipScene = remember(whipLayout, whipFar, whipGround, whipPaint, whipBand, whipCards) {
        whipLayout?.let { GalleryWallScene(it, listOf(whipFar, whipGround, whipPaint, whipBand, whipCards)) }
    }

    val automat = imageResource(Res.drawable.museum_automat)
    val automatFront = imageResource(Res.drawable.museum_automat_front)
    val automatLeft = imageResource(Res.drawable.museum_automat_left)
    val automatRear = imageResource(Res.drawable.museum_automat_rear)
    val automatRight = imageResource(Res.drawable.museum_automat_right)
    val automatSurfaces = imageResource(Res.drawable.museum_automat_surfaces)
    val automatTable = imageResource(Res.drawable.museum_automat_table)
    val automatLayout by produceState<ByteArray?>(null) { value = Res.readBytes("files/museum_automat_scene.bin") }
    val automatScene = remember(
        automatLayout, automatFront, automatLeft, automatRear, automatRight, automatSurfaces, automatTable,
    ) {
        automatLayout?.let {
            GalleryWallScene(it, listOf(automatFront, automatLeft, automatRear, automatRight, automatSurfaces, automatTable))
        }
    }

    val vermeerFront = imageResource(Res.drawable.museum_vermeer_front)
    val vermeerWalls = imageResource(Res.drawable.museum_vermeer_walls)
    val vermeerSurfaces = imageResource(Res.drawable.museum_vermeer_surfaces)
    val vermeerFigures = imageResource(Res.drawable.museum_vermeer_figures)
    val vermeerStudio = imageResource(Res.drawable.museum_vermeer_studio)
    val vermeerLayout by produceState<ByteArray?>(null) { value = Res.readBytes("files/museum_vermeer_scene.bin") }
    val vermeerScene = remember(
        vermeerLayout, vermeerFront, vermeerWalls, vermeerSurfaces, vermeerFigures, vermeerStudio,
    ) {
        vermeerLayout?.let {
            GalleryWallScene(
                it,
                listOf(vermeerFront, vermeerWalls, vermeerSurfaces, vermeerFigures, vermeerStudio),
                panoramic = true,
            )
        }
    }

    val pastureDepth = imageResource(Res.drawable.museum_pasture_depth)
    val whipDepth = imageResource(Res.drawable.museum_whip_depth)
    val vermeerDepth = imageResource(Res.drawable.museum_vermeer_depth)
    val automatDepth = imageResource(Res.drawable.museum_automat_depth)

    val items = remember(
        pastureDepth, whipDepth, vermeerDepth, automatDepth,
        sunflowers, boating, june, bridge, roses, armchair, kiss,
        almond, boatingparty, cliffwalk, cliffwalkBack, venus, poppies, poppiesBack, fan, seashore, vermeer, vermeerBack,
        sunflowersDepth, boatingDepth, juneDepth, bridgeDepth, rosesDepth, armchairDepth, kissDepth,
        almondDepth, boatingpartyDepth, cliffwalkDepth, venusDepth, poppiesDepth, fanDepth, seashoreDepth,
        pasture, whip, automat, cliffwalkScene, poppiesScene, pastureScene, whipScene, automatScene, vermeerScene,
    ) {
        listOf(
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
