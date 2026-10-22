package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import io.github.mejdi14.oddscroll.cdrack.CdRackItem
import io.github.mejdi14.oddscroll.cdrack.CdRackScroll
import io.github.mejdi14.oddscroll.cdrack.rememberCdRackScrollState
import kmpoddscroll.shared.generated.resources.Res
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
import org.jetbrains.compose.resources.imageResource

/**
 * Twenty PlayStation games in clear CD cases, each disc's own label printed
 * on it and the front cover slipped in under the lid's glass, on the white
 * studio wall the case's reference is set on. Tap one to take it out; the
 * system's back puts it back before it leaves the demo.
 *
 * Both are cut square: the disc scans already are, and the covers lose the
 * edges of the wider PAL inserts, spines and all.
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
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_final_fantasy_vii),
            title = "Final Fantasy VII",
            artist = "Square",
            cover = imageResource(Res.drawable.ps1_cover_final_fantasy_vii),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_spyro),
            title = "Spyro the Dragon",
            artist = "Insomniac Games",
            cover = imageResource(Res.drawable.ps1_cover_spyro),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_metal_gear_solid),
            title = "Metal Gear Solid",
            artist = "Konami",
            cover = imageResource(Res.drawable.ps1_cover_metal_gear_solid),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_crash_bandicoot),
            title = "Crash Bandicoot",
            artist = "Naughty Dog",
            cover = imageResource(Res.drawable.ps1_cover_crash_bandicoot),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_silent_hill),
            title = "Silent Hill",
            artist = "Konami",
            cover = imageResource(Res.drawable.ps1_cover_silent_hill),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_gran_turismo),
            title = "Gran Turismo",
            artist = "Polyphony Digital",
            cover = imageResource(Res.drawable.ps1_cover_gran_turismo),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_resident_evil_2),
            title = "Resident Evil 2",
            artist = "Capcom",
            cover = imageResource(Res.drawable.ps1_cover_resident_evil_2),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_ape_escape),
            title = "Ape Escape",
            artist = "Sony Computer Entertainment",
            cover = imageResource(Res.drawable.ps1_cover_ape_escape),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_final_fantasy_viii),
            title = "Final Fantasy VIII",
            artist = "Square",
            cover = imageResource(Res.drawable.ps1_cover_final_fantasy_viii),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_ctr),
            title = "Crash Team Racing",
            artist = "Naughty Dog",
            cover = imageResource(Res.drawable.ps1_cover_ctr),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_dino_crisis),
            title = "Dino Crisis",
            artist = "Capcom",
            cover = imageResource(Res.drawable.ps1_cover_dino_crisis),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_rayman_2),
            title = "Rayman 2: The Great Escape",
            artist = "Ubi Soft",
            cover = imageResource(Res.drawable.ps1_cover_rayman_2),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_medal_of_honor),
            title = "Medal of Honor",
            artist = "DreamWorks Interactive",
            cover = imageResource(Res.drawable.ps1_cover_medal_of_honor),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_medievil),
            title = "MediEvil",
            artist = "SCE Cambridge Studio",
            cover = imageResource(Res.drawable.ps1_cover_medievil),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_final_fantasy_ix),
            title = "Final Fantasy IX",
            artist = "Square",
            cover = imageResource(Res.drawable.ps1_cover_final_fantasy_ix),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_micro_machines_v3),
            title = "Micro Machines V3",
            artist = "Codemasters",
            cover = imageResource(Res.drawable.ps1_cover_micro_machines_v3),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_resident_evil),
            title = "Resident Evil",
            artist = "Capcom",
            cover = imageResource(Res.drawable.ps1_cover_resident_evil),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_soul_blade),
            title = "Soul Blade",
            artist = "Namco",
            cover = imageResource(Res.drawable.ps1_cover_soul_blade),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_tomb_raider),
            title = "Tomb Raider",
            artist = "Core Design",
            cover = imageResource(Res.drawable.ps1_cover_tomb_raider),
        ),
    )
    val items = remember(cases) { cases }

    CdRackScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        state = state,
    )
}
