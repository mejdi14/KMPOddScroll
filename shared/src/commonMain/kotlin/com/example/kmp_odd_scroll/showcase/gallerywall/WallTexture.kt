package com.example.kmp_odd_scroll.showcase.gallerywall

import androidx.compose.ui.graphics.ImageBitmap

/** Called after the final write to a bitmap owned by the wall renderer. */
internal expect fun ImageBitmap.finishWallTexture()
