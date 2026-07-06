package com.ai.postmania.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface LinkedInRepository {
    val isAuthenticated: StateFlow<Boolean>
    val isPublishing: StateFlow<Boolean>
    
    fun getAuthUrl(clientId: String, redirectUri: String, state: String): String
    
    suspend fun handleAuthCallback(
        code: String,
        clientId: String,
        clientSecret: String,
        redirectUri: String
    ): Result<String>
    
    suspend fun publishPost(content: String, imageUrl: String? = null): Result<String>
    
    suspend fun fetchProfileInfo(): Result<Map<String, String>>
    fun logout()
    fun getSavedToken(): String?
}
