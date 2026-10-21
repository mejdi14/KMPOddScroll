package com.example.kmp_odd_scroll.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmp_odd_scroll.LocalPalette
import com.example.kmp_odd_scroll.OddScrollPalette
import com.example.kmp_odd_scroll.ScrollDemo
import com.example.kmp_odd_scroll.scrollDemos

@Composable
fun HomeScreen(onOpen: (ScrollDemo) -> Unit, onToggleTheme: () -> Unit) {
    val palette = LocalPalette.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(palette.page).safeContentPadding(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Odd Scroll",
                            color = palette.ink,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Scrolling styles for Compose Multiplatform",
                            color = palette.muted,
                            fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    ThemeToggle(
                        dark = palette.dark,
                        surface = palette.tile,
                        ink = palette.ink,
                        onClick = onToggleTheme,
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
        }
        items(scrollDemos, key = { it.route }) { demo ->
            DemoTile(demo, palette, onClick = { onOpen(demo) })
        }
    }
}

@Composable
private fun DemoTile(demo: ScrollDemo, palette: OddScrollPalette, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.tile)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScrollArtwork(
            route = demo.route,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.size(14.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(
                "${demo.category} · ${demo.title}",
                color = palette.ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(demo.subtitle, color = palette.muted, fontSize = 13.sp)
        }
    }
}
