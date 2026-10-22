package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.ImageBitmap
import io.github.mejdi14.oddscroll.cdrack.CdRackItem
import io.github.mejdi14.oddscroll.cdrack.CdRackScroll
import io.github.mejdi14.oddscroll.cdrack.rememberCdRackScrollState
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.allDrawableResources
import kmpoddscroll.shared.generated.resources.ps1_cover_ape_escape
import kmpoddscroll.shared.generated.resources.ps1_cover_crash_bandicoot
import kmpoddscroll.shared.generated.resources.ps1_cover_ctr
import kmpoddscroll.shared.generated.resources.ps1_cover_dino_crisis
import kmpoddscroll.shared.generated.resources.ps1_cover_final_fantasy_ix
import kmpoddscroll.shared.generated.resources.ps1_cover_final_fantasy_vii
import kmpoddscroll.shared.generated.resources.ps1_cover_final_fantasy_viii
import kmpoddscroll.shared.generated.resources.ps1_cover_gran_turismo
import kmpoddscroll.shared.generated.resources.ps1_cover_medal_of_honor
import kmpoddscroll.shared.generated.resources.ps1_cover_medievil
import kmpoddscroll.shared.generated.resources.ps1_cover_metal_gear_solid
import kmpoddscroll.shared.generated.resources.ps1_cover_micro_machines_v3
import kmpoddscroll.shared.generated.resources.ps1_cover_rayman_2
import kmpoddscroll.shared.generated.resources.ps1_cover_resident_evil
import kmpoddscroll.shared.generated.resources.ps1_cover_resident_evil_2
import kmpoddscroll.shared.generated.resources.ps1_cover_silent_hill
import kmpoddscroll.shared.generated.resources.ps1_cover_soul_blade
import kmpoddscroll.shared.generated.resources.ps1_cover_spyro
import kmpoddscroll.shared.generated.resources.ps1_cover_tekken_3
import kmpoddscroll.shared.generated.resources.ps1_cover_tomb_raider
import kmpoddscroll.shared.generated.resources.ps1_disc_ape_escape
import kmpoddscroll.shared.generated.resources.ps1_disc_crash_bandicoot
import kmpoddscroll.shared.generated.resources.ps1_disc_ctr
import kmpoddscroll.shared.generated.resources.ps1_disc_dino_crisis
import kmpoddscroll.shared.generated.resources.ps1_disc_final_fantasy_ix
import kmpoddscroll.shared.generated.resources.ps1_disc_final_fantasy_vii
import kmpoddscroll.shared.generated.resources.ps1_disc_final_fantasy_viii
import kmpoddscroll.shared.generated.resources.ps1_disc_gran_turismo
import kmpoddscroll.shared.generated.resources.ps1_disc_medal_of_honor
import kmpoddscroll.shared.generated.resources.ps1_disc_medievil
import kmpoddscroll.shared.generated.resources.ps1_disc_metal_gear_solid
import kmpoddscroll.shared.generated.resources.ps1_disc_micro_machines_v3
import kmpoddscroll.shared.generated.resources.ps1_disc_rayman_2
import kmpoddscroll.shared.generated.resources.ps1_disc_resident_evil
import kmpoddscroll.shared.generated.resources.ps1_disc_resident_evil_2
import kmpoddscroll.shared.generated.resources.ps1_disc_silent_hill
import kmpoddscroll.shared.generated.resources.ps1_disc_soul_blade
import kmpoddscroll.shared.generated.resources.ps1_disc_spyro
import kmpoddscroll.shared.generated.resources.ps1_disc_tekken_3
import kmpoddscroll.shared.generated.resources.ps1_disc_tomb_raider
import kmpoddscroll.shared.generated.resources.ps1_manual_paper
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.jetbrains.compose.resources.imageResource

/**
 * Twenty PlayStation games in clear CD cases, each disc's own label printed
 * on it and the front cover slipped in under the lid's glass, on the white
 * studio wall the case's reference is set on. Tap one to take it out; the
 * system's back puts it back before it leaves the demo.
 *
 * Both are cut square: the disc scans already are, and the covers lose the
 * edges of the wider PAL inserts, spines and all. Each cover is the front of
 * ten pages of the game's own manual, printed on a blank sketchbook spread
 * and read in only for the case that is out.
 * Credits are in the README.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CdRackDemoScreen() {
    val state = rememberCdRackScrollState()
    BackHandler(enabled = state.inspecting) { state.leave() }
    val cases = listOf(
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_tekken_3),
            title = "Tekken 3",
            artist = "Namco",
            cover = imageResource(Res.drawable.ps1_cover_tekken_3),
            manual = manual("tekken_3"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_final_fantasy_vii),
            title = "Final Fantasy VII",
            artist = "Square",
            cover = imageResource(Res.drawable.ps1_cover_final_fantasy_vii),
            manual = manual("final_fantasy_vii"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_spyro),
            title = "Spyro the Dragon",
            artist = "Insomniac Games",
            cover = imageResource(Res.drawable.ps1_cover_spyro),
            manual = manual("spyro"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_metal_gear_solid),
            title = "Metal Gear Solid",
            artist = "Konami",
            cover = imageResource(Res.drawable.ps1_cover_metal_gear_solid),
            manual = manual("metal_gear_solid"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_crash_bandicoot),
            title = "Crash Bandicoot",
            artist = "Naughty Dog",
            cover = imageResource(Res.drawable.ps1_cover_crash_bandicoot),
            manual = manual("crash_bandicoot"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_silent_hill),
            title = "Silent Hill",
            artist = "Konami",
            cover = imageResource(Res.drawable.ps1_cover_silent_hill),
            manual = manual("silent_hill"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_gran_turismo),
            title = "Gran Turismo",
            artist = "Polyphony Digital",
            cover = imageResource(Res.drawable.ps1_cover_gran_turismo),
            manual = manual("gran_turismo"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_resident_evil_2),
            title = "Resident Evil 2",
            artist = "Capcom",
            cover = imageResource(Res.drawable.ps1_cover_resident_evil_2),
            manual = manual("resident_evil_2"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_ape_escape),
            title = "Ape Escape",
            artist = "Sony Computer Entertainment",
            cover = imageResource(Res.drawable.ps1_cover_ape_escape),
            manual = manual("ape_escape"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_final_fantasy_viii),
            title = "Final Fantasy VIII",
            artist = "Square",
            cover = imageResource(Res.drawable.ps1_cover_final_fantasy_viii),
            manual = manual("final_fantasy_viii"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_ctr),
            title = "Crash Team Racing",
            artist = "Naughty Dog",
            cover = imageResource(Res.drawable.ps1_cover_ctr),
            manual = manual("ctr"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_dino_crisis),
            title = "Dino Crisis",
            artist = "Capcom",
            cover = imageResource(Res.drawable.ps1_cover_dino_crisis),
            manual = manual("dino_crisis"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_rayman_2),
            title = "Rayman 2: The Great Escape",
            artist = "Ubi Soft",
            cover = imageResource(Res.drawable.ps1_cover_rayman_2),
            manual = manual("rayman_2"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_medal_of_honor),
            title = "Medal of Honor",
            artist = "DreamWorks Interactive",
            cover = imageResource(Res.drawable.ps1_cover_medal_of_honor),
            manual = manual("medal_of_honor"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_medievil),
            title = "MediEvil",
            artist = "SCE Cambridge Studio",
            cover = imageResource(Res.drawable.ps1_cover_medievil),
            manual = manual("medievil"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_final_fantasy_ix),
            title = "Final Fantasy IX",
            artist = "Square",
            cover = imageResource(Res.drawable.ps1_cover_final_fantasy_ix),
            manual = manual("final_fantasy_ix"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_micro_machines_v3),
            title = "Micro Machines V3",
            artist = "Codemasters",
            cover = imageResource(Res.drawable.ps1_cover_micro_machines_v3),
            manual = manual("micro_machines_v3"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_resident_evil),
            title = "Resident Evil",
            artist = "Capcom",
            cover = imageResource(Res.drawable.ps1_cover_resident_evil),
            manual = manual("resident_evil"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_soul_blade),
            title = "Soul Blade",
            artist = "Namco",
            cover = imageResource(Res.drawable.ps1_cover_soul_blade),
            manual = manual("soul_blade"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_tomb_raider),
            title = "Tomb Raider",
            artist = "Core Design",
            cover = imageResource(Res.drawable.ps1_cover_tomb_raider),
            manual = manual("tomb_raider"),
        ),
    )
    val items = remember(cases) { cases }

    CdRackScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        manualPaper = imageResource(Res.drawable.ps1_manual_paper),
        state = state,
    )
}

private const val MANUAL_PAGES = 10

/** The pages of a game's manual, `ps1_manual_<game>_01` on, decoded when asked for. */
@OptIn(ExperimentalResourceApi::class)
private fun manual(game: String): suspend () -> List<ImageBitmap> = {
    val environment = getSystemResourceEnvironment()
    (1..MANUAL_PAGES).map { page ->
        val name = "ps1_manual_${game}_${page.toString().padStart(2, '0')}"
        getDrawableResourceBytes(environment, Res.allDrawableResources.getValue(name)).decodeToImageBitmap()
    }
}
