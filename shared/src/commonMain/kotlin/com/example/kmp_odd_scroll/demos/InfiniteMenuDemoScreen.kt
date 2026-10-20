package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.mejdi14.oddscroll.infinitemenu.InfiniteMenuItem
import io.github.mejdi14.oddscroll.infinitemenu.InfiniteMenuScroll
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.animal_bear
import kmpoddscroll.shared.generated.resources.animal_bird
import kmpoddscroll.shared.generated.resources.animal_bison
import kmpoddscroll.shared.generated.resources.animal_bunny
import kmpoddscroll.shared.generated.resources.animal_deer
import kmpoddscroll.shared.generated.resources.animal_elephant
import kmpoddscroll.shared.generated.resources.animal_ferret
import kmpoddscroll.shared.generated.resources.animal_giraffe
import kmpoddscroll.shared.generated.resources.animal_hare
import kmpoddscroll.shared.generated.resources.animal_lion
import kmpoddscroll.shared.generated.resources.animal_lion_cub
import kmpoddscroll.shared.generated.resources.animal_penguin
import kmpoddscroll.shared.generated.resources.animal_pig
import kmpoddscroll.shared.generated.resources.animal_rabbit
import kmpoddscroll.shared.generated.resources.animal_raccoon
import kmpoddscroll.shared.generated.resources.animal_rhino
import kmpoddscroll.shared.generated.resources.animal_stag
import kmpoddscroll.shared.generated.resources.animal_zebra
import kmpoddscroll.shared.generated.resources.animal_zebra_run
import org.jetbrains.compose.resources.imageResource

/**
 * Nineteen flat-drawn animals, one to a tile.
 *
 * They were cut out of three collection sheets the same way the card sphere's
 * flowers were, except framed square rather than 4:5, since a tile is a square
 * patch of the sphere. Each is framed to fill its own square, so a tall giraffe
 * and a wide rhino sit the same size — without that the giraffes would read as
 * slivers. Two of the three giraffes were left out all the same: at better than
 * three to one they were thin enough to disappear. Credits are in the README.
 */
@Composable
fun InfiniteMenuDemoScreen() {
    val tiles = listOf(
        InfiniteMenuItem(imageResource(Res.drawable.animal_elephant), "Elephant"),
        InfiniteMenuItem(imageResource(Res.drawable.animal_lion), "Lion"),
        InfiniteMenuItem(imageResource(Res.drawable.animal_zebra), "Zebra"),
        InfiniteMenuItem(imageResource(Res.drawable.animal_giraffe), "Giraffe"),
        InfiniteMenuItem(imageResource(Res.drawable.animal_bear), "Bear"),
        InfiniteMenuItem(imageResource(Res.drawable.animal_penguin), "Penguin"),
        InfiniteMenuItem(imageResource(Res.drawable.animal_rhino), "Rhinoceros"),
        InfiniteMenuItem(imageResource(Res.drawable.animal_stag), "Stag"),
        InfiniteMenuItem(imageResource(Res.drawable.animal_raccoon), "Raccoon"),
        InfiniteMenuItem(imageResource(Res.drawable.animal_bison), "Bison"),
        InfiniteMenuItem(imageResource(Res.drawable.animal_pig), "Pig"),
        InfiniteMenuItem(imageResource(Res.drawable.animal_deer), "Deer"),
        InfiniteMenuItem(imageResource(Res.drawable.animal_bunny), "Bunny"),
        InfiniteMenuItem(imageResource(Res.drawable.animal_ferret), "Ferret"),
        InfiniteMenuItem(imageResource(Res.drawable.animal_zebra_run), "Zebra at a run"),
        InfiniteMenuItem(imageResource(Res.drawable.animal_lion_cub), "Lion cub"),
        InfiniteMenuItem(imageResource(Res.drawable.animal_bird), "Bird"),
        InfiniteMenuItem(imageResource(Res.drawable.animal_hare), "Hare"),
        InfiniteMenuItem(imageResource(Res.drawable.animal_rabbit), "Rabbit"),
    )
    val items = remember(tiles) { tiles }

    InfiniteMenuScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        autoplay = 0f,
    )
}
