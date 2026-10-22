package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.ImageBitmap
import io.github.mejdi14.oddscroll.cdrack.CdManual
import io.github.mejdi14.oddscroll.cdrack.CdRackItem
import io.github.mejdi14.oddscroll.cdrack.CdRackScroll
import io.github.mejdi14.oddscroll.cdrack.rememberCdRackScrollState
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.allDrawableResources
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
import kmpoddscroll.shared.generated.resources.ps1_manual_ape_escape_front
import kmpoddscroll.shared.generated.resources.ps1_manual_crash_bandicoot_front
import kmpoddscroll.shared.generated.resources.ps1_manual_ctr_front
import kmpoddscroll.shared.generated.resources.ps1_manual_dino_crisis_front
import kmpoddscroll.shared.generated.resources.ps1_manual_final_fantasy_ix_front
import kmpoddscroll.shared.generated.resources.ps1_manual_final_fantasy_vii_front
import kmpoddscroll.shared.generated.resources.ps1_manual_final_fantasy_viii_front
import kmpoddscroll.shared.generated.resources.ps1_manual_gran_turismo_front
import kmpoddscroll.shared.generated.resources.ps1_manual_medal_of_honor_front
import kmpoddscroll.shared.generated.resources.ps1_manual_medievil_front
import kmpoddscroll.shared.generated.resources.ps1_manual_metal_gear_solid_front
import kmpoddscroll.shared.generated.resources.ps1_manual_micro_machines_v3_front
import kmpoddscroll.shared.generated.resources.ps1_manual_rayman_2_front
import kmpoddscroll.shared.generated.resources.ps1_manual_resident_evil_2_front
import kmpoddscroll.shared.generated.resources.ps1_manual_resident_evil_front
import kmpoddscroll.shared.generated.resources.ps1_manual_silent_hill_front
import kmpoddscroll.shared.generated.resources.ps1_manual_soul_blade_front
import kmpoddscroll.shared.generated.resources.ps1_manual_spyro_front
import kmpoddscroll.shared.generated.resources.ps1_manual_tekken_3_front
import kmpoddscroll.shared.generated.resources.ps1_manual_tomb_raider_front
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.jetbrains.compose.resources.imageResource

/**
 * Twenty PlayStation games in clear CD cases, each disc's own label printed
 * on it and the front of its manual slipped in under the lid's glass, on the
 * white studio wall the case's reference is set on. Tap one to take it out;
 * the system's back puts it back before it leaves the demo.
 *
 * The manual is pictured whole: its front and back, and five spreads of it
 * lying open, read in only for the case that is out.
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
            cover = imageResource(Res.drawable.ps1_manual_tekken_3_front),
            manual = manual("tekken_3"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_final_fantasy_vii),
            title = "Final Fantasy VII",
            artist = "Square",
            cover = imageResource(Res.drawable.ps1_manual_final_fantasy_vii_front),
            manual = manual("final_fantasy_vii"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_spyro),
            title = "Spyro the Dragon",
            artist = "Insomniac Games",
            cover = imageResource(Res.drawable.ps1_manual_spyro_front),
            manual = manual("spyro"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_metal_gear_solid),
            title = "Metal Gear Solid",
            artist = "Konami",
            cover = imageResource(Res.drawable.ps1_manual_metal_gear_solid_front),
            manual = manual("metal_gear_solid"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_crash_bandicoot),
            title = "Crash Bandicoot",
            artist = "Naughty Dog",
            cover = imageResource(Res.drawable.ps1_manual_crash_bandicoot_front),
            manual = manual("crash_bandicoot"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_silent_hill),
            title = "Silent Hill",
            artist = "Konami",
            cover = imageResource(Res.drawable.ps1_manual_silent_hill_front),
            manual = manual("silent_hill"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_gran_turismo),
            title = "Gran Turismo",
            artist = "Polyphony Digital",
            cover = imageResource(Res.drawable.ps1_manual_gran_turismo_front),
            manual = manual("gran_turismo"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_resident_evil_2),
            title = "Resident Evil 2",
            artist = "Capcom",
            cover = imageResource(Res.drawable.ps1_manual_resident_evil_2_front),
            manual = manual("resident_evil_2"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_ape_escape),
            title = "Ape Escape",
            artist = "Sony Computer Entertainment",
            cover = imageResource(Res.drawable.ps1_manual_ape_escape_front),
            manual = manual("ape_escape"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_final_fantasy_viii),
            title = "Final Fantasy VIII",
            artist = "Square",
            cover = imageResource(Res.drawable.ps1_manual_final_fantasy_viii_front),
            manual = manual("final_fantasy_viii"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_ctr),
            title = "Crash Team Racing",
            artist = "Naughty Dog",
            cover = imageResource(Res.drawable.ps1_manual_ctr_front),
            manual = manual("ctr"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_dino_crisis),
            title = "Dino Crisis",
            artist = "Capcom",
            cover = imageResource(Res.drawable.ps1_manual_dino_crisis_front),
            manual = manual("dino_crisis"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_rayman_2),
            title = "Rayman 2: The Great Escape",
            artist = "Ubi Soft",
            cover = imageResource(Res.drawable.ps1_manual_rayman_2_front),
            manual = manual("rayman_2"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_medal_of_honor),
            title = "Medal of Honor",
            artist = "DreamWorks Interactive",
            cover = imageResource(Res.drawable.ps1_manual_medal_of_honor_front),
            manual = manual("medal_of_honor"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_medievil),
            title = "MediEvil",
            artist = "SCE Cambridge Studio",
            cover = imageResource(Res.drawable.ps1_manual_medievil_front),
            manual = manual("medievil"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_final_fantasy_ix),
            title = "Final Fantasy IX",
            artist = "Square",
            cover = imageResource(Res.drawable.ps1_manual_final_fantasy_ix_front),
            manual = manual("final_fantasy_ix"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_micro_machines_v3),
            title = "Micro Machines V3",
            artist = "Codemasters",
            cover = imageResource(Res.drawable.ps1_manual_micro_machines_v3_front),
            manual = manual("micro_machines_v3"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_resident_evil),
            title = "Resident Evil",
            artist = "Capcom",
            cover = imageResource(Res.drawable.ps1_manual_resident_evil_front),
            manual = manual("resident_evil"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_soul_blade),
            title = "Soul Blade",
            artist = "Namco",
            cover = imageResource(Res.drawable.ps1_manual_soul_blade_front),
            manual = manual("soul_blade"),
        ),
        CdRackItem(
            image = imageResource(Res.drawable.ps1_disc_tomb_raider),
            title = "Tomb Raider",
            artist = "Core Design",
            cover = imageResource(Res.drawable.ps1_manual_tomb_raider_front),
            manual = manual("tomb_raider"),
        ),
    )
    val items = remember(cases) { cases }

    CdRackScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        state = state,
    )
}

private const val MANUAL_SPREADS = 5

/** A game's manual behind its front, `ps1_manual_<game>_spread_1` on and its back, decoded when asked for. */
@OptIn(ExperimentalResourceApi::class)
private fun manual(game: String): suspend () -> CdManual = {
    val environment = getSystemResourceEnvironment()
    suspend fun read(name: String): ImageBitmap =
        getDrawableResourceBytes(environment, Res.allDrawableResources.getValue("ps1_manual_${game}_$name"))
            .decodeToImageBitmap()
    CdManual(
        spreads = (1..MANUAL_SPREADS).map { read("spread_$it") },
        back = read("back"),
    )
}
