package com.example.kmp_odd_scroll.showcase.gallerywall

/** No runtime shader on this target, so the painting turns over without the grain. */
internal actual fun paperShader(): PaperShader? = null

internal actual val paperFolds: Boolean = true
