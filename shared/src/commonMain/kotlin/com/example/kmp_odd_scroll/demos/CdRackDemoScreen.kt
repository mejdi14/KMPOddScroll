package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.example.kmp_odd_scroll.showcase.cdrack.CdManual
import com.example.kmp_odd_scroll.showcase.cdrack.CdRackItem
import com.example.kmp_odd_scroll.showcase.cdrack.CdRackScroll
import com.example.kmp_odd_scroll.showcase.cdrack.rememberCdRackScrollState
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.allDrawableResources
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/** A game in the rack: its resources are `ps1_disc_<key>` and `ps1_manual_<key>_…`. */
private class Game(val key: String, val title: String, val artist: String, val spreads: Int = MANUAL_SPREADS)

private val games = listOf(
    Game("tekken_3", "Tekken 3", "Namco"),
    Game("final_fantasy_vii", "Final Fantasy VII", "Square"),
    Game("spyro", "Spyro the Dragon", "Insomniac Games"),
    Game("metal_gear_solid", "Metal Gear Solid", "Konami", spreads = 13),
    Game("crash_bandicoot", "Crash Bandicoot", "Naughty Dog"),
    Game("silent_hill", "Silent Hill", "Konami"),
    Game("gran_turismo", "Gran Turismo", "Polyphony Digital"),
    Game("resident_evil_2", "Resident Evil 2", "Capcom"),
    Game("ctr", "Crash Team Racing", "Naughty Dog"),
    Game("tomb_raider", "Tomb Raider", "Core Design"),
)

/** The background the rack is drawn on, and its ink, as [CdRackScroll] has them. */
private val PAPER = Color.White
private val INK = Color(0xFF111114)

/** Of the loader's bar, how much reading the pictures in fills; the rack's own drawing fills the rest. */
private const val READ_SHARE = 0.6f

/**
 * Ten PlayStation games in clear CD cases, each disc's own label printed
 * on it and the front of its manual slipped in under the lid's glass, on the
 * white studio wall the case's reference is set on. Tap one to take it out;
 * the system's back puts it back before it leaves the demo.
 *
 * The manual is pictured whole: its front and back, and five spreads of it
 * lying open (Metal Gear Solid's runs to thirteen), read in only for the
 * case that is out.
 * Credits are in the README.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalResourceApi::class)
@Composable
fun CdRackDemoScreen() {
    val state = rememberCdRackScrollState()
    BackHandler(enabled = state.inspecting) { state.leave() }

    // Every disc and cover read in before the rack is built. imageResource
    // hands back a blank picture first on the web and swaps the real one in
    // later, and each swap is a new list of pictures: the rack would be drawn
    // again from the start once for each of them.
    var read by remember { mutableIntStateOf(0) }
    val items by produceState<List<CdRackItem>?>(null) {
        val environment = getSystemResourceEnvironment()
        suspend fun picture(name: String): ImageBitmap =
            getDrawableResourceBytes(environment, Res.allDrawableResources.getValue(name))
                .decodeToImageBitmap()
                .also { read++ }
        value = coroutineScope {
            games.map { game ->
                async {
                    CdRackItem(
                        image = picture("ps1_disc_${game.key}"),
                        title = game.title,
                        artist = game.artist,
                        cover = picture("ps1_manual_${game.key}_front"),
                        manual = manual(game.key, game.spreads),
                    )
                }
            }.awaitAll()
        }
    }

    Box(Modifier.fillMaxSize().background(PAPER)) {
        items?.let {
            CdRackScroll(
                items = it,
                modifier = Modifier.fillMaxSize(),
                background = PAPER,
                contentColor = INK,
                state = state,
            )
        }
        // Up until the rack has drawn every case once.
        DemoLoader(
            done = items != null && state.loaded >= 1f,
            progress = READ_SHARE * read / (games.size * 2f) + (1f - READ_SHARE) * state.loaded,
            paper = PAPER,
            ink = INK,
        )
    }
}

private const val MANUAL_SPREADS = 5

/** A game's manual behind its front, `ps1_manual_<game>_spread_1` on and its back, decoded when asked for. */
@OptIn(ExperimentalResourceApi::class)
private fun manual(game: String, spreads: Int = MANUAL_SPREADS): suspend () -> CdManual = {
    val environment = getSystemResourceEnvironment()
    suspend fun read(name: String): ImageBitmap =
        getDrawableResourceBytes(environment, Res.allDrawableResources.getValue("ps1_manual_${game}_$name"))
            .decodeToImageBitmap()
    CdManual(
        spreads = (1..spreads).map { read("spread_$it") },
        back = read("back"),
    )
}
