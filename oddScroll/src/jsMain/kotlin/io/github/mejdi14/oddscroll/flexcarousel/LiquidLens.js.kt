package io.github.mejdi14.oddscroll.flexcarousel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * No runtime shader on this target, so the row runs undistorted. Everything
 * else about the carousel — the uneven widths, the spring, the squeeze, the
 * parallax, the focus — is unchanged; only the glass is missing.
 */
@Composable
internal actual fun Modifier.liquidLens(lens: FlexLens, strength: Float): Modifier = this
