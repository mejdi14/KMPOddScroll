package com.example.kmp_odd_scroll

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform