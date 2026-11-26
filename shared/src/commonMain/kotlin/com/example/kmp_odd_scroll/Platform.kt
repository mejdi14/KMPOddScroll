package com.example.kmp_odd_scroll

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

internal enum class PlatformFamily { Mobile, Desktop, Web }

internal expect val platformFamily: PlatformFamily
