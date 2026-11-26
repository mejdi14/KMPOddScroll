package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.foundation.focusable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun ToolButton(label: String, ink: Color, background: Color, enabled: Boolean = true, action: () -> Unit) {
    Box(Modifier.heightIn(min = 38.dp).alpha(if (enabled) 1f else 0.38f).clip(CircleShape).background(background)
        .border(1.dp, ink.copy(alpha = 0.32f), CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = action)
        .padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        BasicText(label, style = TextStyle(color = ink, fontSize = 14.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InspectionToolbar(
    requested: InspectionMode?, active: InspectionMode?, analysis: PaintingAnalysis?, c: InspectionControls,
    selected: Int, compact: Boolean, short: Boolean, ink: Color, background: Color, toolsEnabled: Boolean,
    onMode: (InspectionMode) -> Unit, onSelect: (Int?) -> Unit, onReplay: () -> Unit,
    flipLabel: String?, stepLabel: String?, flipEnabled: Boolean, stepEnabled: Boolean,
    onFlip: () -> Unit, onStep: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = if (compact || short) 8.dp else 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (requested != null && analysis == null && requested != InspectionMode.Microscope) {
            Caption("Studying the painting…", ink)
        } else if (active != null) {
            if (!short) Caption(active.gesture, ink.copy(alpha = 0.72f))
            Row(Modifier.fillMaxWidth().heightIn(min = 41.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                when (active) {
                    InspectionMode.Explode -> Adjust("Spacing", c.amount, ink) { c.amount = it }
                    InspectionMode.Lighting -> Adjust("Intensity", c.amount, ink) { c.amount = it }
                    InspectionMode.Microscope -> {
                        Adjust("Zoom", c.amount, ink) { c.amount = it }
                        val tenths = (MicroscopeInspection.zoom(c.amount) * 10f).roundToInt()
                        Caption("${tenths / 10}.${tenths % 10}×", ink)
                    }
                    InspectionMode.PointCloud -> {
                        Adjust("Depth", c.amount, ink, compact) { c.amount = it }
                        Adjust("Density", c.density, ink, compact) { c.density = it }
                        Adjust("Size", c.grain, ink, compact) { c.grain = it }
                    }
                    InspectionMode.Blueprint -> {
                        Adjust("Detail", c.amount, ink) { c.amount = it }
                        ToolButton("Retrace", ink, background, action = onReplay)
                    }
                    InspectionMode.Objects -> {
                        if (analysis != null && analysis.objects.isNotEmpty()) {
                            val label = if (selected < 0) "Choose a cutout" else "Cutout ${selected + 1} of ${analysis.objects.size}"
                            ToolButton(label, ink, background) { onSelect((selected + 1) % analysis.objects.size) }
                            if (selected >= 0) Adjust("Magnify", c.amount, ink, compact) { c.amount = it }
                        } else Caption("No distinct cutouts found", ink)
                    }
                    InspectionMode.Colors -> if (analysis != null) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            analysis.palette.forEachIndexed { i, argb ->
                                val pct = (analysis.proportions[i] * 100).roundToInt()
                                Row(Modifier.clip(CircleShape).border(1.dp, if (selected == i) ink else Color.Transparent, CircleShape)
                                    .selectable(selected == i, role = Role.Button) { onSelect(i) }
                                    .semantics { contentDescription = "Pigment ${i + 1}, ${pct} percent" }
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Spacer(Modifier.size(14.dp).background(Color(argb), CircleShape)
                                        .border(0.6.dp, ink.copy(alpha = 0.4f), CircleShape))
                                    Caption("${pct}%", ink)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (flipLabel != null) ToolButton(flipLabel, ink, background, flipEnabled, onFlip)
                if (stepLabel != null) ToolButton(stepLabel, ink, background, stepEnabled, onStep)
            }
        }
        Spacer(Modifier.fillMaxWidth().padding(vertical = if (short) 2.dp else 6.dp)
            .height(1.dp).background(ink.copy(alpha = 0.18f)))
        FlowRow(Modifier.fillMaxWidth().padding(bottom = if (short) 3.dp else 9.dp),
            horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            for (mode in InspectionMode.entries) {
                val chosen = mode == requested
                val tone = if (chosen) background else ink
                Row(Modifier.alpha(if (toolsEnabled) 1f else 0.38f).clip(CircleShape)
                    .background(if (chosen) ink else Color.Transparent)
                    .selectable(chosen, enabled = toolsEnabled, role = Role.Tab) { onMode(mode) }
                    .heightIn(min = if (short) 34.dp else 40.dp)
                    .padding(horizontal = when { compact -> 5.dp; short -> 6.dp; else -> 13.dp }),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
                    ModeMark(mode, tone, compact)
                    BasicText(mode.label, style = TextStyle(color = tone, fontSize = if (compact) 13.sp else 14.sp))
                }
            }
        }
    }
}

@Composable
private fun Caption(text: String, ink: Color) = BasicText(text, style = TextStyle(color = ink, fontSize = 12.sp))

@Composable
private fun Adjust(label: String, value: Float, ink: Color, compact: Boolean = false, change: (Float) -> Unit) {
    val update by rememberUpdatedState(change)
    val current by rememberUpdatedState(value)
    Column(Modifier.width(if (compact) 91.dp else 146.dp).height(42.dp)
        .padding(horizontal = 8.dp)
        .semantics {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
            setProgress { update(it.coerceIn(0f, 1f)); true }
        }
        .onKeyEvent {
            val delta = when (it.key) {
                Key.DirectionLeft, Key.DirectionDown -> -0.05f
                Key.DirectionRight, Key.DirectionUp -> 0.05f
                else -> return@onKeyEvent false
            }
            if (it.type == KeyEventType.KeyDown) update((current + delta).coerceIn(0f, 1f))
            true
        }.focusable()
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val inset = 5.dp.toPx()
                fun set(x: Float) = update(((x - inset) / (size.width - 2f * inset).coerceAtLeast(1f)).coerceIn(0f, 1f))
                set(down.position.x)
                down.consume()
                do {
                    val point = awaitPointerEvent().changes.first()
                    set(point.position.x)
                    point.consume()
                } while (point.pressed)
            }
        }, verticalArrangement = Arrangement.Center) {
        Caption(label, ink)
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val min = 5.dp.toPx()
            val max = size.width - min
            val x = mix(min, max, value)
            drawLine(ink.copy(alpha = 0.28f), Offset(min, center.y), Offset(max, center.y), 1.dp.toPx())
            drawLine(ink, Offset(min, center.y), Offset(x, center.y), 1.5.dp.toPx())
            drawCircle(ink, 3.6.dp.toPx(), Offset(x, center.y))
        }
    }
}

@Composable
private fun ModeMark(mode: InspectionMode, color: Color, compact: Boolean) {
    Canvas(Modifier.size(if (compact) 13.dp else 16.dp)) {
        val u = size.width / 16f
        val stroke = Stroke(1.1f * u)
        fun line(x: Float, y: Float, xx: Float, yy: Float) =
            drawLine(color, Offset(x * u, y * u), Offset(xx * u, yy * u), stroke.width)
        fun ring(x: Float, y: Float, r: Float) = drawCircle(color, r * u, Offset(x * u, y * u), style = stroke)
        when (mode) {
            InspectionMode.Explode -> {
                val path = Path().apply { moveTo(1*u, 5*u); lineTo(8*u, 2*u); lineTo(15*u, 5*u); lineTo(8*u, 8*u); close() }
                drawPath(path, color, style = stroke)
                for (y in listOf(8f, 11f)) { line(1f,y,8f,y+3); line(8f,y+3,15f,y) }
            }
            InspectionMode.Lighting -> {
                ring(8f, 8f, 3.4f)
                line(8f,0f,8f,2.5f); line(8f,13.5f,8f,16f); line(0f,8f,2.5f,8f); line(13.5f,8f,16f,8f)
                line(2f,2f,4f,4f); line(12f,12f,14f,14f); line(2f,14f,4f,12f); line(12f,4f,14f,2f)
            }
            InspectionMode.Microscope -> { ring(6.5f, 6.5f, 4.5f); line(10f,10f,15f,15f) }
            InspectionMode.PointCloud -> for (row in 0..2) for (col in 0..2)
                drawCircle(color, (0.85f + row * 0.1f)*u, Offset((2 + col*5 + row%2)*u, (3 + row*5 - col%2)*u))
            InspectionMode.Blueprint -> {
                line(2f,14f,7f,2f); line(7f,2f,14f,14f); line(2f,14f,14f,14f); line(4f,9f,11f,9f)
            }
            InspectionMode.Objects -> {
                drawRect(color, Offset(1*u,5*u), Size(9*u,9*u), style=stroke)
                drawCircle(color, 3.7f*u, Offset(11*u,4.5f*u), style=stroke)
            }
            InspectionMode.Colors -> { ring(5f,9.5f,3.8f); ring(11f,9.5f,3.8f); ring(8f,5f,3.8f) }
        }
    }
}
