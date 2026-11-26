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
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun InspectionModeIndex(
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
        modes.forEach { mode ->
            val chosen = mode == selected
            val tone = LocalContentColor.current.copy(alpha = when {
                !enabled -> 0.46f
                chosen -> 1f
                else -> 0.72f
            })
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = if (compact) 44.dp else 48.dp)
                    .inspectionButtonFeedback(enabled = enabled)
                    .selectable(chosen, enabled = enabled, role = Role.Tab) { onMode(mode) },
                horizontalArrangement = Arrangement.spacedBy(7.dp, if (alignEnd) Alignment.End else Alignment.Start),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!alignEnd) ModeGlyph(mode, tone)
                BasicText(
                    mode.label,
                    modifier = Modifier.weight(1f, fill = false),
                    style = TextStyle(
                        color = tone,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = if (compact) 13.sp else 15.sp,
                        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                    ),
                )
                if (alignEnd) ModeGlyph(mode, tone)
            }
        }
    }
}

@Composable
internal fun InspectionAction(
    label: String,
    mark: String,
    alignEnd: Boolean,
    enabled: Boolean = true,
    color: Color = LocalContentColor.current,
    gridFeedback: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .inspectionButtonFeedback(enabled = enabled && gridFeedback)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(7.dp, if (alignEnd) Alignment.End else Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tone = if (enabled) color else color.copy(alpha = 0.46f)
        if (!alignEnd) ActionGlyph(mark, tone)
        BasicText(
            label,
            modifier = Modifier.weight(1f, fill = false),
            style = TextStyle(
                color = tone,
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            ),
        )
        if (alignEnd) ActionGlyph(mark, tone)
    }
}

@Composable
private fun ModeGlyph(mode: InspectionMode, color: Color) {
    Canvas(Modifier.size(15.dp)) {
        when (mode) {
            InspectionMode.Explode -> {
                glyphPath(color, 8, 2, 15, 6, 8, 10, 1, 6, 8, 2)
                glyphPath(color, 1, 9, 8, 13, 15, 9)
            }
            InspectionMode.Lighting -> {
                glyphCircle(color, 8, 8, 3f)
                glyphPath(color, 8, 1, 8, 3)
                glyphPath(color, 8, 13, 8, 15)
                glyphPath(color, 1, 8, 3, 8)
                glyphPath(color, 13, 8, 15, 8)
                glyphPath(color, 3, 3, 4, 4)
                glyphPath(color, 12, 12, 13, 13)
                glyphPath(color, 12, 4, 13, 3)
                glyphPath(color, 3, 13, 4, 12)
            }
            InspectionMode.Microscope -> {
                glyphCircle(color, 7, 7, 4f)
                glyphPath(color, 10, 10, 14, 14)
                glyphPath(color, 5, 7, 9, 7)
                glyphPath(color, 7, 5, 7, 9)
            }
            InspectionMode.PointCloud -> {
                glyphCircle(color, 8, 3, 0.7f)
                glyphCircle(color, 4, 6, 0.7f)
                glyphCircle(color, 12, 6, 0.7f)
                glyphCircle(color, 8, 8, 0.7f)
                glyphCircle(color, 4, 11, 0.7f)
                glyphCircle(color, 12, 11, 0.7f)
                glyphCircle(color, 8, 13, 0.7f)
            }
            InspectionMode.Blueprint -> {
                glyphPath(color, 1, 4, 15, 4)
                glyphPath(color, 1, 12, 15, 12)
                glyphPath(color, 4, 1, 4, 15)
                glyphPath(color, 12, 1, 12, 15)
            }
            InspectionMode.Objects -> {
                glyphPath(color, 8, 1, 14, 5, 14, 12, 8, 15, 2, 12, 2, 5, 8, 1)
                glyphPath(color, 2, 5, 8, 8, 14, 5)
                glyphPath(color, 8, 8, 8, 15)
            }
            InspectionMode.Colors -> {
                glyphCircle(color, 6, 6, 3f)
                glyphCircle(color, 10, 6, 3f)
                glyphCircle(color, 8, 10, 3f)
            }
        }
    }
}

@Composable
private fun ActionGlyph(mark: String, color: Color) {
    Canvas(Modifier.size(15.dp)) {
        when (mark) {
            "←" -> {
                glyphPath(color, 14, 8, 2, 8)
                glyphPath(color, 6, 4, 2, 8, 6, 12)
            }
            "↪" -> {
                glyphPath(color, 3, 3, 3, 8, 5, 10, 13, 10)
                glyphPath(color, 10, 7, 13, 10, 10, 13)
            }
            "→" -> {
                glyphPath(color, 2, 8, 14, 8)
                glyphPath(color, 10, 4, 14, 8, 10, 12)
            }
            "↺", "↻" -> {
                val clockwise = mark == "↻"
                drawArc(
                    color = color,
                    startAngle = if (clockwise) 30f else 70f,
                    sweepAngle = if (clockwise) 305f else -280f,
                    useCenter = false,
                    topLeft = glyphPoint(3, 3),
                    size = Size(size.width * 0.625f, size.height * 0.625f),
                    style = Stroke(1.dp.toPx(), cap = StrokeCap.Round),
                )
                if (clockwise) glyphPath(color, 13, 3, 13, 6, 10, 6)
                else glyphPath(color, 3, 13, 3, 10, 6, 10)
            }
            "›" -> glyphPath(color, 6, 4, 10, 8, 6, 12)
        }
    }
}

private fun DrawScope.glyphPoint(x: Int, y: Int): Offset =
    Offset(x * size.width / 16f, y * size.height / 16f)

private fun DrawScope.glyphPath(color: Color, vararg coordinates: Int) {
    val path = Path()
    val first = glyphPoint(coordinates[0], coordinates[1])
    path.moveTo(first.x, first.y)
    for (index in 2 until coordinates.size step 2) {
        val point = glyphPoint(coordinates[index], coordinates[index + 1])
        path.lineTo(point.x, point.y)
    }
    drawPath(path, color, style = Stroke(1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.glyphCircle(color: Color, x: Int, y: Int, radius: Float) {
    drawCircle(color, radius * size.minDimension / 16f, glyphPoint(x, y), style = Stroke(1.dp.toPx()))
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
    val ink = LocalContentColor.current
    key(requested) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (requested != null && analysis == null && requested != InspectionMode.Microscope) {
                Caption("Studying the painting…")
            } else if (active != null) {
                Caption(active.gesture, ink.copy(alpha = 0.72f))
                InspectionRule(Modifier.width(24.dp))
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
                        InspectionAction("Retrace", "↻", alignEnd = false, onClick = onReplay)
                    }
                    InspectionMode.Objects -> {
                        val count = analysis?.objects?.size ?: 0
                        if (count == 0) {
                            Caption("No distinct cutouts found")
                        } else {
                            val label = if (selected < 0) "Choose a cutout" else "Cutout ${selected + 1} of $count"
                            InspectionAction(label, "›", alignEnd = false) { onSelect((selected + 1) % count) }
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
                                        .inspectionButtonFeedback()
                                        .selectable(chosen, role = Role.Button) { onSelect(index) }
                                        .semantics { contentDescription = "Pigment ${index + 1}, $percent percent" },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    Spacer(
                                        Modifier.size(17.dp).background(Color(argb), CircleShape)
                                            .border(1.dp, if (chosen) ink else ink.copy(alpha = 0.4f), CircleShape),
                                    )
                                    BasicText(
                                        "$percent%",
                                        Modifier.clearAndSetSemantics {},
                                        style = TextStyle(
                                            color = ink.copy(alpha = if (chosen) 1f else 0.72f),
                                            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                                            fontSize = 14.sp,
                                        ),
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
private fun InspectionRule(modifier: Modifier) {
    Spacer(modifier.height(0.5.dp).background(LocalContentColor.current.copy(alpha = 0.23f)))
}

@Composable
private fun Caption(text: String, color: Color = LocalContentColor.current) {
    BasicText(text, style = TextStyle(color = color, fontSize = 12.sp, lineHeight = 16.sp))
}

@Composable
private fun Adjust(
    label: String,
    value: Float,
    displayValue: String = "${(value * 100).roundToInt()}%",
    change: (Float) -> Unit,
) {
    val ink = LocalContentColor.current
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
            BasicText(label, Modifier.weight(1f), style = TextStyle(color = ink, fontSize = 12.sp))
            Caption(displayValue, ink)
        }
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val min = 5.dp.toPx()
            val max = (size.width - min).coerceAtLeast(min)
            val x = mix(min, max, value)
            drawLine(ink.copy(alpha = 0.28f), Offset(min, center.y), Offset(max, center.y), 1.dp.toPx())
            drawLine(ink, Offset(min, center.y), Offset(x, center.y), 1.dp.toPx())
            drawCircle(ink, 3.5.dp.toPx(), Offset(x, center.y))
        }
    }
}

private fun PointerInputScope.sliderPosition(x: Float): Float {
    val inset = 5.dp.toPx()
    val trackWidth = (size.width - 2f * inset).coerceAtLeast(1f)
    return ((x - inset) / trackWidth).coerceIn(0f, 1f)
}
