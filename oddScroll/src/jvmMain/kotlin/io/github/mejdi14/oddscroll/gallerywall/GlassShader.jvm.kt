package io.github.mejdi14.oddscroll.gallerywall

/** No runtime shader on this target, so the glass is drawn plainly — the room it throws back and the doorway's glow — and keeps its frame on when walked up to. */
internal actual fun glassShader(): GlassShader? = null
