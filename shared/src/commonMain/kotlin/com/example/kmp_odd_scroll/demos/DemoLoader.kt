package com.example.kmp_odd_scroll.demos

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A hairline that fills to [progress] on the [paper], over a demo that is
 * still reading its pictures in and drawing them once; it fades away the
 * first time [done] is true and does not come back. Taps under it go nowhere.
 */
@Composable
internal fun DemoLoader(done: Boolean, progress: Float, paper: Color, ink: Color) {
    var finished by remember { mutableStateOf(false) }
    if (done) finished = true
    val veil = animateFloatAsState(if (finished) 0f else 1f, tween(450), label = "veil")
    val fill = animateFloatAsState(if (finished) 1f else progress.coerceIn(0f, 1f), tween(250), label = "fill")
    if (finished && veil.value <= 0f) return
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = veil.value }.background(paper).pointerInput(Unit) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(
                "LOADING",
                style = TextStyle(color = ink.copy(alpha = 0.55f), fontSize = 11.sp, letterSpacing = 2.4.sp),
            )
            Spacer(Modifier.height(14.dp))
            Canvas(Modifier.width(168.dp).height(2.dp)) {
                drawRect(ink.copy(alpha = 0.1f))
                drawRect(ink.copy(alpha = 0.8f), size = size.copy(width = size.width * fill.value))
            }
        }
    }
}
