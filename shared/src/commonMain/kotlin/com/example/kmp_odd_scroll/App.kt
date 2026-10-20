package com.example.kmp_odd_scroll

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import com.example.kmp_odd_scroll.home.HomeScreen

/**
 * Catalog shell. Navigation is a single route held in state — the sample has no
 * deep links and no back stack to preserve, and a navigation dependency would
 * be the largest thing in the project.
 *
 * A demo fills the frame with nothing drawn over it. Each one is a scroll style
 * being shown off, and a button floating in its corner is the one thing
 * guaranteed to be in the way; the way out is the system's own back instead.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        var open by remember { mutableStateOf<ScrollDemo?>(null) }
        val demo = open

        BackHandler(enabled = demo != null) { open = null }

        Box(Modifier.fillMaxSize().background(Color(0xFF07070A))) {
            if (demo == null) {
                HomeScreen(onOpen = { open = it })
            } else {
                demo.screen()
            }
        }
    }
}
