package com.example.kmp_odd_scroll

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmp_odd_scroll.home.HomeScreen

internal const val HOME_TITLE = "Odd Scroll"
private const val HOME_ROUTE = "home"

@Composable
fun App() {
    var dark by rememberSaveable { mutableStateOf(false) }
    val palette = if (dark) DarkPalette else LightPalette
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(LocalPalette provides palette) {
            BoxWithConstraints(Modifier.fillMaxSize().background(palette.page)) {
                CatalogLayout(split = maxWidth >= 840.dp, dark = dark, onToggleTheme = { dark = !dark })
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CatalogLayout(split: Boolean, dark: Boolean, onToggleTheme: () -> Unit) {
    var route by rememberSaveable { mutableStateOf<String?>(null) }
    val demo = scrollDemos.find { it.route == route } ?: if (split) scrollDemos.first() else null
    if (split && demo != null && route != demo.route) {
        // The initial wide selection follows the user into a narrower window.
        SideEffect { route = demo.route }
    }
    val screens = rememberSaveableStateHolder()
    val curtain = rememberPageCurtainState()

    fun open(target: ScrollDemo) {
        if (target.route == demo?.route) return
        curtain.navigate(target.title) {
            route?.let(screens::removeState)
            route = target.route
        }
    }

    fun goHome() {
        curtain.navigate(HOME_TITLE, direction = -1, holdMillis = 250) {
            route?.let(screens::removeState)
            route = null
        }
    }

    BackHandler(enabled = !split && demo != null, onBack = ::goHome)
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            if (split || demo == null) {
                screens.SaveableStateProvider(HOME_ROUTE) {
                    HomeScreen(
                        demos = scrollDemos,
                        onOpenDemo = ::open,
                        dark = dark,
                        onToggleTheme = onToggleTheme,
                        modifier = Modifier.fillMaxWidth(if (split) 0.2f else 1f),
                        selectedRoute = demo?.route,
                        compact = split,
                        enabled = !curtain.isActive,
                    )
                }
            }
            if (demo != null) {
                DemoPane(Modifier.weight(1f).fillMaxHeight()) {
                    screens.SaveableStateProvider(demo.route) { demo.screen() }
                    if (split) {
                        PageCurtain(curtain)
                    } else {
                        BackChip(onClick = ::goHome)
                    }
                }
            }
        }
        if (!split) PageCurtain(curtain)
    }
    // Once the curtain is moving, take precedence over the demo's nested back handlers.
    if (curtain.isActive) key(demo?.route) { BackHandler {} }
}

@Composable
private fun DemoPane(modifier: Modifier, content: @Composable () -> Unit) {
    val windowInfo = LocalWindowInfo.current
    var paneSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val paneWindow = remember(windowInfo, density) {
        object : WindowInfo by windowInfo {
            override val containerSize: IntSize
                get() = if (paneSize == IntSize.Zero) windowInfo.containerSize else paneSize
            override val containerDpSize: DpSize
                get() = if (paneSize == IntSize.Zero) windowInfo.containerDpSize else with(density) {
                    DpSize(paneSize.width.toDp(), paneSize.height.toDp())
                }
        }
    }
    Box(modifier.clipToBounds().onSizeChanged { paneSize = it }) {
        CompositionLocalProvider(LocalWindowInfo provides paneWindow) { content() }
    }
}

@Composable
private fun BackChip(onClick: () -> Unit) {
    Box(
        Modifier.systemBarsPadding().padding(12.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text("‹ Back", color = Color.White, fontSize = 15.sp)
    }
}
