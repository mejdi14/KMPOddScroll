package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.mejdi14.oddscroll.infinitemenu.InfiniteMenuItem
import io.github.mejdi14.oddscroll.infinitemenu.InfiniteMenuScroll
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.movie_battleship_potemkin
import kmpoddscroll.shared.generated.resources.movie_faust
import kmpoddscroll.shared.generated.resources.movie_greed
import kmpoddscroll.shared.generated.resources.movie_intolerance
import kmpoddscroll.shared.generated.resources.movie_metropolis
import kmpoddscroll.shared.generated.resources.movie_sherlock_jr
import kmpoddscroll.shared.generated.resources.movie_spies
import kmpoddscroll.shared.generated.resources.movie_sunrise_a_song_of_two_humans
import kmpoddscroll.shared.generated.resources.movie_the_beloved_rogue
import kmpoddscroll.shared.generated.resources.movie_the_big_parade
import kmpoddscroll.shared.generated.resources.movie_the_black_pirate
import kmpoddscroll.shared.generated.resources.movie_the_blue_angel
import kmpoddscroll.shared.generated.resources.movie_the_broadway_melody
import kmpoddscroll.shared.generated.resources.movie_the_cameraman
import kmpoddscroll.shared.generated.resources.movie_the_cat_and_the_canary
import kmpoddscroll.shared.generated.resources.movie_the_circus
import kmpoddscroll.shared.generated.resources.movie_the_freshman
import kmpoddscroll.shared.generated.resources.movie_the_gold_rush
import kmpoddscroll.shared.generated.resources.movie_the_jazz_singer
import kmpoddscroll.shared.generated.resources.movie_the_kid
import kmpoddscroll.shared.generated.resources.movie_the_last_command
import kmpoddscroll.shared.generated.resources.movie_the_last_laugh
import kmpoddscroll.shared.generated.resources.movie_the_love_parade
import kmpoddscroll.shared.generated.resources.movie_the_navigator
import kmpoddscroll.shared.generated.resources.movie_the_passion_of_joan_of_arc
import kmpoddscroll.shared.generated.resources.movie_the_sheik
import kmpoddscroll.shared.generated.resources.movie_the_ten_commandments
import kmpoddscroll.shared.generated.resources.movie_the_thief_of_bagdad
import kmpoddscroll.shared.generated.resources.movie_the_wedding_march
import kmpoddscroll.shared.generated.resources.movie_wings
import org.jetbrains.compose.resources.imageResource

/**
 * Thirty silent-era film posters, one to a tile.
 *
 * A tile is a square patch of the sphere, so each portrait poster is cut to a
 * square from its top, keeping the artwork and clipping the credit block below.
 * Credits are in the README.
 */
@Composable
fun InfiniteMenuDemoScreen() {
    val tiles = listOf(
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_last_laugh), "The Last Laugh"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_metropolis), "Metropolis"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_wings), "Wings"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_broadway_melody), "The Broadway Melody"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_sunrise_a_song_of_two_humans), "Sunrise: A Song of Two Humans"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_jazz_singer), "The Jazz Singer"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_big_parade), "The Big Parade"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_gold_rush), "The Gold Rush"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_greed), "Greed"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_sherlock_jr), "Sherlock Jr."),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_thief_of_bagdad), "The Thief of Bagdad"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_love_parade), "The Love Parade"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_battleship_potemkin), "Battleship Potemkin"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_black_pirate), "The Black Pirate"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_circus), "The Circus"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_intolerance), "Intolerance"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_last_command), "The Last Command"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_passion_of_joan_of_arc), "The Passion of Joan of Arc"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_wedding_march), "The Wedding March"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_beloved_rogue), "The Beloved Rogue"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_sheik), "The Sheik"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_faust), "Faust"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_kid), "The Kid"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_blue_angel), "The Blue Angel"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_ten_commandments), "The Ten Commandments"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_freshman), "The Freshman"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_navigator), "The Navigator"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_cat_and_the_canary), "The Cat and the Canary"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_spies), "Spies"),
        InfiniteMenuItem(imageResource(Res.drawable.movie_the_cameraman), "The Cameraman"),
    )
    val items = remember(tiles) { tiles }

    InfiniteMenuScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        autoplay = 0f,
    )
}
