package com.ai.postmania.domain.model

interface ContentGenerator {
    val id: String
    val name: String
    val systemPrompt: String
    fun buildPrompt(input: String, options: Map<String, String>): String
}
