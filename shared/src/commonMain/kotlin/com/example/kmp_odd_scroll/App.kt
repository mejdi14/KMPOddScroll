package com.example.kmp_odd_scroll

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmp_odd_scroll.home.HomeScreen

/**
 * Catalog shell. Navigation is a single route held in state — the sample has no
 * deep links and no back stack to preserve, and a navigation dependency would
 * be the largest thing in the project.
 */
@Composable
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        var open by remember { mutableStateOf<ScrollDemo?>(null) }
        val demo = open

        Box(Modifier.fillMaxSize().background(Color(0xFF07070A))) {
            if (demo == null) {
                HomeScreen(onOpen = { open = it })
            } else {
                demo.screen()
                Box(Modifier.fillMaxSize().safeContentPadding()) {
                    Text(
                        text = "Back",
                        color = Color(0xFFF3F0E9),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(18.dp)
                            .clip(RoundedCornerShape(100))
                            .background(Color(0x33FFFFFF))
                            .clickable { open = null }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}
