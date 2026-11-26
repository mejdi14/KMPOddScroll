package com.example.kmp_odd_scroll.showcase.gallerywall

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap

internal actual fun ImageBitmap.finishWallTexture() {
    // Compose creates a SkImage per projected strip. Immutable pixels can be shared by all of them.
    asSkiaBitmap().setImmutable()
}
