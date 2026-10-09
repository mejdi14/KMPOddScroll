package com.example.kmp_odd_scroll.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.mejdi14.oddscroll.helix.HelixScroll

@Composable
fun HelixDemoScreen() {
    val items = remember { demoHelixItems() }
    HelixScroll(
        items = items,
        modifier = Modifier.fillMaxSize(),
        brand = "Studio",
    )
}
