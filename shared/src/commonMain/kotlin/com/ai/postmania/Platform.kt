package com.ai.postmania

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform