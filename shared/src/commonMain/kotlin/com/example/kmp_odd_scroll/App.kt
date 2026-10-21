package com.example.kmp_odd_scroll

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.example.kmp_odd_scroll.home.HomeScreen

/**
 * Catalog shell. Navigation is a single route held in state — the sample has no
 * deep links and no back stack to preserve, and a navigation dependency would
 * be the largest thing in the project.
 *
 * A demo fills the frame with nothing drawn over it. Each one is a scroll style
 * being shown off, and a button floating in its corner is the one thing
 * guaranteed to be in the way; the way out is the system's own back instead.
 *
 * The theme is held here, dark to begin with, and handed down as
 * [LocalPalette]. Only the gallery has a control for it, but the demos read
 * the same palette, so the choice follows the user into every one of them.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App() {
    var dark by rememberSaveable { mutableStateOf(true) }
    val palette = if (dark) DarkPalette else LightPalette

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(LocalPalette provides palette) {
            var open by remember { mutableStateOf<ScrollDemo?>(null) }
            val demo = open

            BackHandler(enabled = demo != null) { open = null }

            Box(Modifier.fillMaxSize().background(palette.page)) {
                if (demo == null) {
                    HomeScreen(onOpen = { open = it }, onToggleTheme = { dark = !dark })
                } else {
                    demo.screen()
                }
            }
        }
    }
}
