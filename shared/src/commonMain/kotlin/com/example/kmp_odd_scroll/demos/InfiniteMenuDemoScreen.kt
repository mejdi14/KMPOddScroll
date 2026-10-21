package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.mejdi14.oddscroll.infinitemenu.InfiniteMenuItem
import io.github.mejdi14.oddscroll.infinitemenu.InfiniteMenuScroll
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.movie_2001_a_space_odyssey
import kmpoddscroll.shared.generated.resources.movie_a_clockwork_orange
import kmpoddscroll.shared.generated.resources.movie_akira
import kmpoddscroll.shared.generated.resources.movie_alien
import kmpoddscroll.shared.generated.resources.movie_amelie
import kmpoddscroll.shared.generated.resources.movie_black_swan
import kmpoddscroll.shared.generated.resources.movie_blade_runner
import kmpoddscroll.shared.generated.resources.movie_drive
import kmpoddscroll.shared.generated.resources.movie_dune
import kmpoddscroll.shared.generated.resources.movie_everything_everywhere_all_at_once
import kmpoddscroll.shared.generated.resources.movie_her
import kmpoddscroll.shared.generated.resources.movie_in_the_mood_for_love
import kmpoddscroll.shared.generated.resources.movie_jaws
import kmpoddscroll.shared.generated.resources.movie_la_la_land
import kmpoddscroll.shared.generated.resources.movie_moonlight
import kmpoddscroll.shared.generated.resources.movie_parasite
import kmpoddscroll.shared.generated.resources.movie_portrait_of_a_lady_on_fire
import kmpoddscroll.shared.generated.resources.movie_pulp_fiction
import kmpoddscroll.shared.generated.resources.movie_spirited_away
import kmpoddscroll.shared.generated.resources.movie_star_wars
import kmpoddscroll.shared.generated.resources.movie_the_batman
import kmpoddscroll.shared.generated.resources.movie_the_french_dispatch
import kmpoddscroll.shared.generated.resources.movie_the_godfather
import kmpoddscroll.shared.generated.resources.movie_the_grand_budapest_hotel
import kmpoddscroll.shared.generated.resources.movie_the_handmaiden
import kmpoddscroll.shared.generated.resources.movie_the_lobster
import kmpoddscroll.shared.generated.resources.movie_the_shape_of_water
import kmpoddscroll.shared.generated.resources.movie_the_shining
import kmpoddscroll.shared.generated.resources.movie_the_silence_of_the_lambs
import kmpoddscroll.shared.generated.resources.movie_vertigo
import org.jetbrains.compose.resources.imageResource

/**
 * Thirty film posters, one to a tile.
 *
 * A tile is a square patch of the sphere, so each portrait poster is cut to a
 * square from its top, keeping the artwork and clipping the credit block below.
 * Credits are in the README.
 */
@Composable
fun InfiniteMenuDemoScreen() {
    val tiles = listOf(
        InfiniteMenuItem(imageResource(Res.drawable.movie_2001_a_space_odyssey), "2001: A Space Odyssey"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_vertigo), "Vertigo"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_grand_budapest_hotel), "The Grand Budapest Hotel"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_in_the_mood_for_love), "In the Mood for Love"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_blade_runner), "Blade Runner"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_pulp_fiction), "Pulp Fiction"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_godfather), "The Godfather"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_alien), "Alien"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_jaws), "Jaws"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_star_wars), "Star Wars"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_silence_of_the_lambs), "The Silence of the Lambs"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_black_swan), "Black Swan"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_moonlight), "Moonlight"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_la_la_land), "La La Land"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_shape_of_water), "The Shape of Water"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_french_dispatch), "The French Dispatch"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_parasite), "Parasite"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_spirited_away), "Spirited Away"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_akira), "Akira"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_shining), "The Shining"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_a_clockwork_orange), "A Clockwork Orange"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_drive), "Drive"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_her), "Her"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_lobster), "The Lobster"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_amelie), "Amélie"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_handmaiden), "The Handmaiden"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_portrait_of_a_lady_on_fire), "Portrait of a Lady on Fire"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_everything_everywhere_all_at_once), "Everything Everywhere All at Once"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_dune), "Dune"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_batman), "The Batman"),
    )
    val items = remember(tiles) { tiles }

    InfiniteMenuScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        autoplay = 0f,
    )
}
