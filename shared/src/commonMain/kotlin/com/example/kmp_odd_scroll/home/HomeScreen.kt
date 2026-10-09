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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmp_odd_scroll.ScrollDemo
import com.example.kmp_odd_scroll.scrollDemos

private val Page = Color(0xFF07070A)
private val Ink = Color(0xFFF3F0E9)
private val Muted = Color(0xFF8A8A95)

@Composable
fun HomeScreen(onOpen: (ScrollDemo) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Page).safeContentPadding(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text(
                    "Odd Scroll",
                    color = Ink,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Scrolling styles for Compose Multiplatform",
                    color = Muted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(18.dp))
            }
        }
        items(scrollDemos, key = { it.route }) { demo ->
            DemoTile(demo, onClick = { onOpen(demo) })
        }
    }
}

@Composable
private fun DemoTile(demo: ScrollDemo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0E0F13))
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
                color = Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(demo.subtitle, color = Muted, fontSize = 13.sp)
        }
    }
}
