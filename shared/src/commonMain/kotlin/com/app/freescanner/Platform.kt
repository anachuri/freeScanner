package com.app.freescanner

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform