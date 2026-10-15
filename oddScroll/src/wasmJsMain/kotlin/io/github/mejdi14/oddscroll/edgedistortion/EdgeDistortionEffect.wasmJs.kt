package io.github.mejdi14.oddscroll.edgedistortion

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * No runtime shader on this target, so the lattice is drawn flat. Everything
 * else about the gallery — the endless pan, the drift, the cards — is unchanged;
 * only the border bend and the colour fringe are missing.
 */
@Composable
internal actual fun Modifier.edgeDistortion(strength: Float, aberration: Float): Modifier =
    this
