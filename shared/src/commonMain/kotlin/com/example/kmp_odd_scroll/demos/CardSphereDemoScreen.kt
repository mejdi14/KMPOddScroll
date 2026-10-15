package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.mejdi14.oddscroll.cardsphere.CardSphereScroll

/**
 * The only demo with no pictures in it at all — every card is drawn, so there
 * is nothing to bundle and nothing to credit.
 */
@Composable
fun CardSphereDemoScreen() {
    CardSphereScroll(modifier = Modifier.fillMaxSize())
}
