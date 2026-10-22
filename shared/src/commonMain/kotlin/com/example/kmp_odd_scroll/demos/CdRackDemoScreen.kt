package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.mejdi14.oddscroll.cdrack.CdCase
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.art_night
import org.jetbrains.compose.resources.imageResource

/**
 * One CD case for now, on the white studio wall the reference is set on, with
 * a museum scan printed on its disc: drag it to turn it, tap it to open it.
 * Credits are in the README.
 */
@Composable
fun CdRackDemoScreen() {
    CdCase(
        art = imageResource(Res.drawable.art_night),
        modifier = Modifier.fillMaxSize(),
    )
}
