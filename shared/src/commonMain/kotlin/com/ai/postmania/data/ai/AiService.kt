package com.ai.postmania.data.ai

import com.ai.postmania.domain.model.AiProvider
import com.ai.postmania.domain.model.ContentGenerator
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

class AiService {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    suspend fun generateContent(
        provider: AiProvider,
        apiKey: String,
        generator: ContentGenerator,
        input: String,
        options: Map<String, String>
    ): String {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("API Key is missing. Please check your settings.")
        }
        
        val systemPrompt = generator.systemPrompt
        val userPrompt = generator.buildPrompt(input, options)
        
        return when (provider) {
            AiProvider.GEMINI -> generateGemini(apiKey, systemPrompt, userPrompt)
            AiProvider.OPENAI -> generateOpenAi(apiKey, systemPrompt, userPrompt)
        }
    }

    private suspend fun generateGemini(apiKey: String, systemPrompt: String, userPrompt: String): String {
        val url = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=$apiKey"
        
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    putJsonArray("contents") {
                        addJsonObject {
                            put("role", "user")
                            putJsonArray("parts") {
                                addJsonObject {
                                    put("text", "$systemPrompt\n\nUser request:\n$userPrompt")
                                }
                            }
                        }
                    }
                }
            )
        }
        
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw Exception("Gemini API Error (${response.status}): $errorBody")
        }
        
        val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val text = responseJson["candidates"]
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")
            ?.jsonObject?.get("parts")
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")
            ?.jsonPrimitive?.content
            ?: throw Exception("Invalid response structure from Gemini API")
            
        return text.trim()
    }

    private suspend fun generateOpenAi(apiKey: String, systemPrompt: String, userPrompt: String): String {
        val url = "https://api.openai.com/v1/chat/completions"
        
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(
                buildJsonObject {
                    put("model", "gpt-4o-mini")
                    putJsonArray("messages") {
                        addJsonObject {
                            put("role", "system")
                            put("content", systemPrompt)
                        }
                        addJsonObject {
                            put("role", "user")
                            put("content", userPrompt)
                        }
                    }
                }
            )
        }
        
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw Exception("OpenAI API Error (${response.status}): $errorBody")
        }
        
        val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val text = responseJson["choices"]
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")
            ?.jsonObject?.get("content")
            ?.jsonPrimitive?.content
            ?: throw Exception("Invalid response structure from OpenAI API")
        
        return text.trim()
    }

    suspend fun generateDalleImage(apiKey: String, prompt: String): String {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("OpenAI API Key is missing for DALL-E image generation.")
        }
        val url = "https://api.openai.com/v1/images/generations"
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            setBody(
                buildJsonObject {
                    put("model", "dall-e-3")
                    put("prompt", prompt)
                    put("n", 1)
                    put("size", "1024x1024")
                }
            )
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw Exception("DALL-E API Error (${response.status}): $errorBody")
        }

        val responseJson = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val imageUrl = responseJson["data"]
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("url")
            ?.jsonPrimitive?.content
            ?: throw Exception("Invalid response structure from DALL-E API")

        return imageUrl
    }
}
