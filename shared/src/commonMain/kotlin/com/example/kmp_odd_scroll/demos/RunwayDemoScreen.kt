package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.kmp_odd_scroll.showcase.runway.PopupCutout
import com.example.kmp_odd_scroll.showcase.runway.PopupScene
import com.example.kmp_odd_scroll.showcase.runway.RunwayScroll
import com.example.kmp_odd_scroll.showcase.runway.rememberRunwayScrollState
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.popup_ancient_tree
import kmpoddscroll.shared.generated.resources.popup_autumn_tree
import kmpoddscroll.shared.generated.resources.popup_blossom_tree
import kmpoddscroll.shared.generated.resources.popup_dusk_oak
import kmpoddscroll.shared.generated.resources.popup_forest_cottage
import kmpoddscroll.shared.generated.resources.popup_mushroom_cluster
import kmpoddscroll.shared.generated.resources.popup_pine_tree
import kmpoddscroll.shared.generated.resources.popup_red_barn
import kmpoddscroll.shared.generated.resources.popup_slate_cottage
import kmpoddscroll.shared.generated.resources.popup_twilight_pine
import kmpoddscroll.shared.generated.resources.popup_windmill
import kmpoddscroll.shared.generated.resources.popup_wood_bridge
import org.jetbrains.compose.resources.imageResource

/**
 * The runway as a pop-up papercraft diorama: each colour band carries a themed
 * miniature scene of illustrated standees hinged along their bottom edges.
 * Cutouts lie flat on their cardstock platform until they scroll into the lower
 * frame, flip upright with a cardstock spring bounce, and fold flat again near
 * the horizon.
 */
@Composable
fun RunwayDemoScreen() {
    val ancientTree = imageResource(Res.drawable.popup_ancient_tree)
    val forestCottage = imageResource(Res.drawable.popup_forest_cottage)
    val woodBridge = imageResource(Res.drawable.popup_wood_bridge)
    val mushrooms = imageResource(Res.drawable.popup_mushroom_cluster)
    val pineTree = imageResource(Res.drawable.popup_pine_tree)
    val windmill = imageResource(Res.drawable.popup_windmill)
    val redBarn = imageResource(Res.drawable.popup_red_barn)
    val autumnTree = imageResource(Res.drawable.popup_autumn_tree)
    val blossomTree = imageResource(Res.drawable.popup_blossom_tree)
    val twilightPine = imageResource(Res.drawable.popup_twilight_pine)
    val slateCottage = imageResource(Res.drawable.popup_slate_cottage)
    val duskOak = imageResource(Res.drawable.popup_dusk_oak)

    val scenes = remember(
        ancientTree,
        forestCottage,
        woodBridge,
        mushrooms,
        pineTree,
        windmill,
        redBarn,
        autumnTree,
        blossomTree,
        twilightPine,
        slateCottage,
        duskOak,
    ) {
        listOf(
            // 1. Woodland Hollow
            PopupScene(
                groundColor = Color(0xFF2F5E41),
                platformColor = Color(0xFF477A58),
                cutouts = listOf(
                    PopupCutout(ancientTree, u = 0.00f, localS = -225f, heightPx = 435f),
                    PopupCutout(pineTree, u = -0.31f, localS = -180f, heightPx = 290f),
                    PopupCutout(pineTree, u = 0.31f, localS = -170f, heightPx = 280f, mirror = true),
                    PopupCutout(forestCottage, u = -0.22f, localS = 10f, heightPx = 230f),
                    PopupCutout(forestCottage, u = 0.23f, localS = 25f, heightPx = 215f, mirror = true),
                    PopupCutout(woodBridge, u = 0.00f, localS = 200f, heightPx = 140f),
                    PopupCutout(mushrooms, u = -0.26f, localS = 255f, heightPx = 135f),
                    PopupCutout(mushrooms, u = 0.27f, localS = 245f, heightPx = 120f, mirror = true),
                ),
            ),
            // 2. Sunlit Windmill Valley
            PopupScene(
                groundColor = Color(0xFF8F6D2F),
                platformColor = Color(0xFFB08E48),
                cutouts = listOf(
                    PopupCutout(windmill, u = 0.11f, localS = -220f, heightPx = 435f),
                    PopupCutout(autumnTree, u = -0.28f, localS = -195f, heightPx = 305f),
                    PopupCutout(pineTree, u = 0.33f, localS = -165f, heightPx = 260f, mirror = true),
                    PopupCutout(redBarn, u = -0.15f, localS = 15f, heightPx = 265f),
                    PopupCutout(autumnTree, u = 0.26f, localS = 40f, heightPx = 225f, mirror = true),
                    PopupCutout(woodBridge, u = 0.12f, localS = 220f, heightPx = 135f, mirror = true),
                    PopupCutout(mushrooms, u = -0.25f, localS = 250f, heightPx = 125f),
                ),
            ),
            // 3. Blossom Orchard Village
            PopupScene(
                groundColor = Color(0xFF7A3B54),
                platformColor = Color(0xFF9B5672),
                cutouts = listOf(
                    PopupCutout(blossomTree, u = 0.00f, localS = -220f, heightPx = 415f),
                    PopupCutout(blossomTree, u = -0.30f, localS = -170f, heightPx = 280f, mirror = true),
                    PopupCutout(blossomTree, u = 0.30f, localS = -165f, heightPx = 275f),
                    PopupCutout(slateCottage, u = -0.20f, localS = 20f, heightPx = 235f),
                    PopupCutout(forestCottage, u = 0.21f, localS = 30f, heightPx = 225f, mirror = true),
                    PopupCutout(woodBridge, u = 0.00f, localS = 215f, heightPx = 145f),
                    PopupCutout(mushrooms, u = -0.27f, localS = 255f, heightPx = 115f, mirror = true),
                    PopupCutout(mushrooms, u = 0.27f, localS = 260f, heightPx = 122f),
                ),
            ),
            // 4. Harvest Mill & Meadow
            PopupScene(
                groundColor = Color(0xFF8A3D28),
                platformColor = Color(0xFFAA5A40),
                cutouts = listOf(
                    PopupCutout(redBarn, u = -0.06f, localS = -215f, heightPx = 360f),
                    PopupCutout(windmill, u = 0.24f, localS = -205f, heightPx = 390f, mirror = true),
                    PopupCutout(autumnTree, u = -0.32f, localS = -160f, heightPx = 270f),
                    PopupCutout(forestCottage, u = -0.22f, localS = 30f, heightPx = 215f, mirror = true),
                    PopupCutout(autumnTree, u = 0.18f, localS = 25f, heightPx = 235f),
                    PopupCutout(woodBridge, u = -0.04f, localS = 215f, heightPx = 135f),
                    PopupCutout(mushrooms, u = 0.25f, localS = 250f, heightPx = 130f),
                ),
            ),
            // 5. Twilight Sacred Grove
            PopupScene(
                groundColor = Color(0xFF1B4F5A),
                platformColor = Color(0xFF2C6E7A),
                cutouts = listOf(
                    PopupCutout(duskOak, u = 0.00f, localS = -225f, heightPx = 440f),
                    PopupCutout(twilightPine, u = -0.31f, localS = -180f, heightPx = 300f),
                    PopupCutout(twilightPine, u = 0.31f, localS = -175f, heightPx = 290f, mirror = true),
                    PopupCutout(slateCottage, u = -0.22f, localS = 15f, heightPx = 230f, mirror = true),
                    PopupCutout(slateCottage, u = 0.22f, localS = 25f, heightPx = 220f),
                    PopupCutout(woodBridge, u = 0.00f, localS = 205f, heightPx = 140f),
                    PopupCutout(twilightPine, u = -0.32f, localS = 230f, heightPx = 185f, mirror = true),
                    PopupCutout(mushrooms, u = 0.24f, localS = 255f, heightPx = 125f),
                ),
            ),
            // 6. Starlight Alpine Hamlet
            PopupScene(
                groundColor = Color(0xFF222756),
                platformColor = Color(0xFF363E7A),
                cutouts = listOf(
                    PopupCutout(windmill, u = -0.14f, localS = -220f, heightPx = 415f),
                    PopupCutout(duskOak, u = 0.18f, localS = -205f, heightPx = 370f, mirror = true),
                    PopupCutout(twilightPine, u = -0.34f, localS = -165f, heightPx = 275f),
                    PopupCutout(slateCottage, u = 0.02f, localS = 15f, heightPx = 240f),
                    PopupCutout(blossomTree, u = -0.27f, localS = 40f, heightPx = 225f),
                    PopupCutout(twilightPine, u = 0.31f, localS = 35f, heightPx = 230f),
                    PopupCutout(woodBridge, u = 0.00f, localS = 220f, heightPx = 138f, mirror = true),
                    PopupCutout(mushrooms, u = -0.24f, localS = 255f, heightPx = 118f),
                    PopupCutout(mushrooms, u = 0.25f, localS = 250f, heightPx = 122f, mirror = true),
                ),
            ),
        )
    }

    RunwayScroll(
        items = emptyList(),
        modifier = Modifier.fillMaxSize(),
        state = rememberRunwayScrollState(autoplay = false, initialScrollPx = 0f),
        popupScenes = scenes,
    )
}
