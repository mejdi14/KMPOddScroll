package io.github.mejdi14.oddscroll.internal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlin.math.min
import kotlin.math.pow

/** What the text is cut into before it is let in. */
internal enum class SplitBy { Chars, Words }

/* The reference's "rise": each piece comes up from below and fades in, one
   after the next, on a curve that arrives fast and settles slowly. */
private const val STAGGER_MS = 50f
private const val PIECE_MS = 700f

/**
 * However long the text, the stagger is never spread wider than this. Fifty
 * milliseconds a piece reads beautifully across a title and takes the better
 * part of a minute across a paragraph, so past a point the pieces are simply
 * packed closer together.
 */
private const val SPREAD_CAP_MS = 850f

/**
 * Text let in a piece at a time, each piece rising into place as it arrives.
 *
 * Words are the unit of wrapping whatever the pieces are, so characters are
 * nested inside their own word rather than laid out loose — otherwise a line
 * break would fall wherever it liked and words would come apart across lines.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SplitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    splitBy: SplitBy = SplitBy.Chars,
    rise: Dp = 40.dp,
    startDelay: Int = 0,
    startWhen: (() -> Boolean)? = null,
) {
    if (text.isEmpty()) return

    val words = remember(text) { text.split(" ").filter { it.isNotEmpty() } }
    val pieces = remember(words, splitBy) {
        if (splitBy == SplitBy.Words) words.size else words.sumOf { it.length }
    }
    val step = remember(pieces) {
        if (pieces <= 1) 0f else min(STAGGER_MS, SPREAD_CAP_MS / (pieces - 1))
    }
    val total = remember(pieces, step) { step * (pieces - 1) + PIECE_MS }

    val elapsed = remember(text, splitBy) { Animatable(0f) }
    LaunchedEffect(text, splitBy) {
        // Composed and measured now, started later. Waiting for the signal here
        // rather than waiting to be composed means the pieces are already laid
        // out when their moment comes, instead of eighty of them being built in
        // the one frame that is supposed to look smooth.
        if (startWhen != null) snapshotFlow(startWhen).first { it }
        elapsed.animateTo(
            targetValue = total,
            animationSpec = tween(total.toInt(), startDelay, LinearEasing),
        )
    }

    // Words are set apart by a gap rather than by a space character, since a
    // space of their own would be a piece to animate and would hang off the end
    // of a wrapped line.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val gap = remember(style, density) {
        val pair = measurer.measure(AnnotatedString("n n"), style).size.width
        val joined = measurer.measure(AnnotatedString("nn"), style).size.width
        with(density) { (pair - joined).coerceAtLeast(1).toDp() }
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        var index = 0
        for (word in words) {
            if (splitBy == SplitBy.Words) {
                Piece(word, style, index, step, rise, elapsed)
                index++
            } else {
                Row {
                    for (letter in word) {
                        Piece(letter.toString(), style, index, step, rise, elapsed)
                        index++
                    }
                }
            }
        }
    }
}

@Composable
private fun Piece(
    text: String,
    style: TextStyle,
    index: Int,
    step: Float,
    rise: Dp,
    elapsed: Animatable<Float, *>,
) {
    BasicText(
        text = text,
        style = style,
        // Read inside the layer rather than in composition: the clock moves
        // every frame, and only the transform has to follow it.
        modifier = Modifier.graphicsLayer {
            val local = ((elapsed.value - index * step) / PIECE_MS).coerceIn(0f, 1f)
            // power3.out — most of the distance covered immediately, the rest
            // let down gently.
            val eased = 1f - (1f - local).pow(3)
            alpha = eased
            translationY = (1f - eased) * rise.toPx()
        },
    )
}
