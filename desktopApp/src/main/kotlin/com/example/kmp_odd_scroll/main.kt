package com.example.kmp_odd_scroll

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KMPOddScroll",
    ) {
        App()
    }
}