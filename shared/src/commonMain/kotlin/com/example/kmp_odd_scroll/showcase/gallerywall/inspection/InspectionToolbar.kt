package com.example.kmp_odd_scroll.showcase.gallerywall.inspection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

internal val ArchivePaper = Color(0xFFF9F8F6)
internal val ArchiveInk = Color(0xFF242321)
internal val ArchiveAccent = Color(0xFF791F28)

@Composable
internal fun ArchiveModeIndex(
    modes: List<InspectionMode>,
    selected: InspectionMode?,
    enabled: Boolean,
    alignEnd: Boolean,
    compact: Boolean,
    onMode: (InspectionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).selectableGroup(),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        modes.forEachIndexed { index, mode ->
            if (index > 0) ArchiveRule(Modifier.fillMaxWidth())
            val chosen = mode == selected
            val color = if (chosen) ArchiveAccent else ArchiveInk
            Box(
                Modifier.fillMaxWidth()
                    .heightIn(min = if (compact) 44.dp else 48.dp)
                    .selectable(chosen, enabled = enabled, role = Role.Tab) { onMode(mode) },
                contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                BasicText(
                    mode.label,
                    style = TextStyle(
                        color = if (enabled) color else color.copy(alpha = 0.55f),
                        fontFamily = FontFamily.Serif,
                        fontSize = if (compact) 16.sp else 19.sp,
                        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun ArchiveAction(
    label: String,
    mark: String,
    alignEnd: Boolean,
    enabled: Boolean = true,
    color: Color = ArchiveInk,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(5.dp, if (alignEnd) Alignment.End else Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tone = if (enabled) color else color.copy(alpha = 0.55f)
        BasicText(
            label,
            modifier = Modifier.weight(1f, fill = false),
            style = TextStyle(
                color = tone,
                fontFamily = FontFamily.Serif,
                fontSize = 15.sp,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            ),
        )
        if (mark.isNotEmpty()) {
            BasicText(mark, Modifier.clearAndSetSemantics {}, style = TextStyle(color = tone, fontSize = 14.sp))
        }
    }
}

@Composable
internal fun InspectionAdjustments(
    requested: InspectionMode?,
    active: InspectionMode?,
    analysis: PaintingAnalysis?,
    c: InspectionControls,
    selected: Int,
    onSelect: (Int?) -> Unit,
    onReplay: () -> Unit,
) {
    key(requested) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (requested != null && analysis == null && requested != InspectionMode.Microscope) {
                Caption("Studying the painting…")
            } else if (active != null) {
                Caption(active.gesture, ArchiveInk.copy(alpha = 0.72f))
                ArchiveRule(Modifier.width(24.dp))
                when (active) {
                    InspectionMode.Explode -> Adjust("Spacing", c.amount) { c.amount = it }
                    InspectionMode.Lighting -> Adjust("Intensity", c.amount) { c.amount = it }
                    InspectionMode.Microscope -> {
                        val tenths = (MicroscopeInspection.zoom(c.amount) * 10f).roundToInt()
                        Adjust("Zoom", c.amount, "${tenths / 10}.${tenths % 10}×") { c.amount = it }
                    }
                    InspectionMode.PointCloud -> {
                        Adjust("Depth", c.amount) { c.amount = it }
                        Adjust("Density", c.density) { c.density = it }
                        Adjust("Size", c.grain) { c.grain = it }
                    }
                    InspectionMode.Blueprint -> {
                        Adjust("Detail", c.amount) { c.amount = it }
                        ArchiveAction("Retrace", "↻", alignEnd = false, onClick = onReplay)
                    }
                    InspectionMode.Objects -> {
                        val count = analysis?.objects?.size ?: 0
                        if (count == 0) {
                            Caption("No distinct cutouts found")
                        } else {
                            val label = if (selected < 0) "Choose a cutout" else "Cutout ${selected + 1} of $count"
                            ArchiveAction(label, "›", alignEnd = false) { onSelect((selected + 1) % count) }
                            if (selected >= 0) Adjust("Magnify", c.amount) { c.amount = it }
                        }
                    }
                    InspectionMode.Colors -> if (analysis != null) {
                        Column(Modifier.selectableGroup()) {
                            analysis.palette.forEachIndexed { index, argb ->
                                val percent = (analysis.proportions[index] * 100).roundToInt()
                                val chosen = index == selected
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                        .selectable(chosen, role = Role.Button) { onSelect(index) }
                                        .semantics { contentDescription = "Pigment ${index + 1}, $percent percent" },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    Spacer(
                                        Modifier.size(17.dp).background(Color(argb), CircleShape)
                                            .border(1.dp, if (chosen) ArchiveAccent else ArchiveInk.copy(alpha = 0.4f), CircleShape),
                                    )
                                    BasicText(
                                        "$percent%",
                                        Modifier.clearAndSetSemantics {},
                                        style = TextStyle(color = if (chosen) ArchiveAccent else ArchiveInk, fontSize = 14.sp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveRule(modifier: Modifier) {
    Spacer(modifier.height(0.5.dp).background(ArchiveInk.copy(alpha = 0.23f)))
}

@Composable
private fun Caption(text: String, color: Color = ArchiveInk) {
    BasicText(text, style = TextStyle(color = color, fontSize = 12.sp, lineHeight = 16.sp))
}

@Composable
private fun Adjust(
    label: String,
    value: Float,
    displayValue: String = "${(value * 100).roundToInt()}%",
    change: (Float) -> Unit,
) {
    val update by rememberUpdatedState(change)
    val current by rememberUpdatedState(value)
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress {
                    update(it.coerceIn(0f, 1f))
                    true
                }
            }
            .onKeyEvent {
                val next = when (it.key) {
                    Key.DirectionLeft, Key.DirectionDown -> current - 0.05f
                    Key.DirectionRight, Key.DirectionUp -> current + 0.05f
                    else -> return@onKeyEvent false
                }
                if (it.type == KeyEventType.KeyDown) update(next.coerceIn(0f, 1f))
                true
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures { update(sliderPosition(it.x)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> update(sliderPosition(change.position.x)) }
            },
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier.fillMaxWidth().clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText(label, Modifier.weight(1f), style = TextStyle(color = ArchiveInk, fontSize = 12.sp))
            Caption(displayValue, ArchiveAccent)
        }
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val min = 5.dp.toPx()
            val max = (size.width - min).coerceAtLeast(min)
            val x = mix(min, max, value)
            drawLine(ArchiveInk.copy(alpha = 0.28f), Offset(min, center.y), Offset(max, center.y), 1.dp.toPx())
            drawLine(ArchiveAccent, Offset(min, center.y), Offset(x, center.y), 1.dp.toPx())
            drawCircle(ArchiveAccent, 3.5.dp.toPx(), Offset(x, center.y))
        }
    }
}

private fun PointerInputScope.sliderPosition(x: Float): Float {
    val inset = 5.dp.toPx()
    val trackWidth = (size.width - 2f * inset).coerceAtLeast(1f)
    return ((x - inset) / trackWidth).coerceIn(0f, 1f)
}
