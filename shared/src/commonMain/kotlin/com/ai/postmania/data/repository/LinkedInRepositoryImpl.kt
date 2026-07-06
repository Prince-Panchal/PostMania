package com.ai.postmania.data.repository

import com.ai.postmania.domain.repository.LinkedInRepository
import com.russhwolf.settings.Settings
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.statement.readBytes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*

class LinkedInRepositoryImpl(
    private val settings: Settings = Settings()
) : LinkedInRepository {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    private val _isAuthenticated = MutableStateFlow(!getSavedToken().isNullOrBlank())
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _isPublishing = MutableStateFlow(false)
    override val isPublishing: StateFlow<Boolean> = _isPublishing.asStateFlow()

    companion object {
        private const val TOKEN_KEY = "linkedin_access_token"
        private const val SCOPES = "w_member_social r_liteprofile"
    }

    override fun getAuthUrl(clientId: String, redirectUri: String, state: String): String {
        return "https://www.linkedin.com/oauth/v2/authorization" +
                "?response_type=code" +
                "&client_id=$clientId" +
                "&redirect_uri=${URLBuilder(redirectUri).buildString()}" +
                "&state=$state" +
                "&scope=openid%20profile%20w_member_social"
    }

    override suspend fun handleAuthCallback(
        code: String,
        clientId: String,
        clientSecret: String,
        redirectUri: String
    ): Result<String> {
        return try {
            val response = client.post("https://www.linkedin.com/oauth/v2/accessToken") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf(
                        "grant_type" to "authorization_code",
                        "code" to code,
                        "client_id" to clientId,
                        "client_secret" to clientSecret,
                        "redirect_uri" to redirectUri
                    ).formUrlEncode()
                )
            }
            
            if (response.status.isSuccess()) {
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                val token = json["access_token"]?.jsonPrimitive?.content
                    ?: throw Exception("Access token not found in LinkedIn response")
                
                settings.putString(TOKEN_KEY, token)
                _isAuthenticated.value = true
                Result.success(token)
            } else {
                val errorMsg = response.bodyAsText()
                Result.failure(Exception("Failed to exchange LinkedIn code: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun publishPost(content: String, imageUrl: String?): Result<String> {
        val token = getSavedToken() ?: return Result.failure(Exception("LinkedIn account is not authenticated."))
        _isPublishing.value = true
        return try {
            // Get user's profile details using new userinfo endpoint
            val profileResponse = client.get("https://api.linkedin.com/v2/userinfo") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            
            if (!profileResponse.status.isSuccess()) {
                if (profileResponse.status == HttpStatusCode.Unauthorized) {
                    logout()
                    throw Exception("LinkedIn session has expired. Please sign in again.")
                }
                throw Exception("Failed to fetch LinkedIn profile details: ${profileResponse.bodyAsText()}")
            }
            
            val profileJson = Json.parseToJsonElement(profileResponse.bodyAsText()).jsonObject
            val authorUrn = profileJson["sub"]?.jsonPrimitive?.content 
                ?: throw Exception("Could not find user ID in profile response.")

            var mediaAssetUrn: String? = null

            // If there's an image, download and register/upload it to LinkedIn first
            if (!imageUrl.isNullOrBlank()) {
                // 1. Download image bytes
                val imageBytes = client.get(imageUrl).readBytes()

                // 2. Register upload with LinkedIn Assets API
                val registerResponse = client.post("https://api.linkedin.com/rest/assets?action=registerUpload") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Restli-Protocol-Version", "2.0.0")
                    header("Linkedin-Version", "202606")
                    contentType(ContentType.Application.Json)
                    setBody(
                        buildJsonObject {
                            putJsonObject("registerUploadRequest") {
                                put("owner", "urn:li:person:$authorUrn")
                                putJsonArray("recipes") {
                                    add("urn:li:digitalmediaRecipe:feedshare-image")
                                }
                                putJsonArray("serviceRelationships") {
                                    addJsonObject {
                                        put("identifier", "urn:li:userGeneratedContent")
                                        put("relationshipType", "OWNER")
                                    }
                                }
                            }
                        }
                    )
                }

                if (registerResponse.status.isSuccess()) {
                    val registerJson = Json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject
                    val valueObj = registerJson["value"]?.jsonObject
                        ?: throw Exception("Invalid asset registration response structure")
                    
                    val asset = valueObj["asset"]?.jsonPrimitive?.content
                        ?: throw Exception("Asset URN not found in response")
                    
                    val uploadUrl = valueObj["uploadMechanism"]
                        ?.jsonObject?.get("com.linkedin.digitalmedia.uploading.MediaUploadHttpRequest")
                        ?.jsonObject?.get("uploadUrl")?.jsonPrimitive?.content
                        ?: throw Exception("Upload URL not found in response")

                    // 3. Upload image binary bytes
                    val uploadResponse = client.put(uploadUrl) {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Image.JPEG)
                        setBody(imageBytes)
                    }

                    if (uploadResponse.status.isSuccess()) {
                        mediaAssetUrn = asset
                    }
                }
            }
            
            // Publish using modern rest/posts API with required headers
            val publishResponse = client.post("https://api.linkedin.com/rest/posts") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("X-Restli-Protocol-Version", "2.0.0")
                header("Linkedin-Version", "202606")
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("author", "urn:li:person:$authorUrn")
                        put("commentary", content)
                        put("visibility", "PUBLIC")
                        
                        if (!mediaAssetUrn.isNullOrBlank()) {
                            putJsonObject("content") {
                                putJsonObject("media") {
                                    put("id", mediaAssetUrn)
                                }
                            }
                        }

                        putJsonObject("distribution") {
                            put("feedDistribution", "MAIN_FEED")
                            putJsonArray("targetEntities") {}
                            putJsonArray("thirdPartyDistributionChannels") {}
                        }
                        put("lifecycleState", "PUBLISHED")
                        put("isReshareDisabledByAuthor", false)
                    }
                )
            }
            
            if (publishResponse.status.isSuccess()) {
                Result.success("Post successfully published to LinkedIn!")
            } else {
                val errorMsg = publishResponse.bodyAsText()
                Result.failure(Exception("Failed to publish post: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isPublishing.value = false
        }
    }

    override suspend fun fetchProfileInfo(): Result<Map<String, String>> {
        val token = getSavedToken() ?: return Result.failure(Exception("Not authenticated"))
        return try {
            val response = client.get("https://api.linkedin.com/v2/userinfo") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status.isSuccess()) {
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                val profileMap = mutableMapOf<String, String>()
                profileMap["name"] = json["name"]?.jsonPrimitive?.content ?: "LinkedIn User"
                profileMap["email"] = json["email"]?.jsonPrimitive?.content ?: ""
                profileMap["picture"] = json["picture"]?.jsonPrimitive?.content ?: ""
                Result.success(profileMap)
            } else {
                Result.failure(Exception("Failed to load profile details"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun logout() {
        settings.remove(TOKEN_KEY)
        _isAuthenticated.value = false
    }

    override fun getSavedToken(): String? {
        return settings.getStringOrNull(TOKEN_KEY)
    }
}
