package io.github.mejdi14.oddscroll.gallerywall

/** No runtime shader on this target, so the painting turns over without the halftone and grain. */
internal actual fun paperShader(): PaperShader? = null

internal actual val paperFolds: Boolean = true
