package com.ai.postmania.presentation

import com.ai.postmania.data.ai.AiService
import com.ai.postmania.data.repository.LinkedInRepositoryImpl
import com.ai.postmania.domain.model.AiProvider
import com.ai.postmania.domain.model.ContentGenerator
import com.ai.postmania.domain.model.LinkedInPostGenerator
import com.ai.postmania.domain.repository.LinkedInRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UiState {
    object Idle : UiState
    object Loading : UiState
    data class Success(val generatedPost: String) : UiState
    data class Error(val message: String) : UiState
}

class PostManiaViewModel(
    val linkedinRepository: LinkedInRepository = LinkedInRepositoryImpl(),
    private val aiService: AiService = AiService(),
    private val settings: Settings = Settings()
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main)
    
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Config options
    val inputPrompt = MutableStateFlow("")
    val selectedTone = MutableStateFlow("Professional")
    val selectedLength = MutableStateFlow("Medium")
    val selectedAudience = MutableStateFlow("General")
    val selectedProvider = MutableStateFlow(AiProvider.GEMINI)
    
    // API keys & settings (Provide your default API keys here if you want them pre-packaged inside the application)
    val geminiApiKey = MutableStateFlow(settings.getString("gemini_api_key", "").ifBlank { "" })
    val openAiApiKey = MutableStateFlow(settings.getString("openai_api_key", "").ifBlank { "" })
    
    // LinkedIn client configs (for sandbox & development testing)
    val linkedinClientId = MutableStateFlow(settings.getString("linkedin_client_id", ""))
    val linkedinClientSecret = MutableStateFlow(settings.getString("linkedin_client_secret", ""))
    val linkedinRedirectUri = MutableStateFlow(settings.getString("linkedin_redirect_uri", "https://redirectmeto.com/postmania://callback"))

    val showSettings = MutableStateFlow(false)
    val showLinkedInAuthDialog = MutableStateFlow(false)

    // Cached post for editing
    val editablePostText = MutableStateFlow("")

    fun saveApiKeys(geminiKey: String, openAiKey: String) {
        settings.putString("gemini_api_key", geminiKey)
        settings.putString("openai_api_key", openAiKey)
        geminiApiKey.value = geminiKey
        openAiApiKey.value = openAiKey
    }

    fun saveLinkedInClientCredentials(clientId: String, secret: String, redirect: String) {
        settings.putString("linkedin_client_id", clientId)
        settings.putString("linkedin_client_secret", secret)
        settings.putString("linkedin_redirect_uri", redirect)
        linkedinClientId.value = clientId
        linkedinClientSecret.value = secret
        linkedinRedirectUri.value = redirect
    }

    val generateImageWithPost = MutableStateFlow(true)
    val generatedImageUrl = MutableStateFlow<String?>(null)

    fun generatePost() {
        val input = inputPrompt.value
        if (input.isBlank()) {
            _uiState.value = UiState.Error("Please enter your achievement or idea first.")
            return
        }

        val provider = selectedProvider.value
        val key = if (provider == AiProvider.GEMINI) geminiApiKey.value else openAiApiKey.value
        
        if (key.isBlank()) {
            _uiState.value = UiState.Error("API Key is missing for ${provider.displayName}. Please configure it in settings.")
            return
        }

        _uiState.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val generator: ContentGenerator = LinkedInPostGenerator()
                val options = mapOf(
                    "tone" to selectedTone.value,
                    "length" to selectedLength.value,
                    "audience" to selectedAudience.value
                )
                val postContent = aiService.generateContent(
                    provider = provider,
                    apiKey = key,
                    generator = generator,
                    input = input,
                    options = options
                )

                var imageUrl: String? = null
                if (generateImageWithPost.value) {
                    val openAiKey = openAiApiKey.value
                    if (openAiKey.isNotBlank()) {
                        // Describe standard DALL-E visual context request based on core input idea
                        val visualPrompt = "Professional, editorial and highly aesthetic modern illustration for a LinkedIn post about: $input. Flat vector minimal style, vibrant corporative palette."
                        try {
                            imageUrl = aiService.generateDalleImage(openAiKey, visualPrompt)
                        } catch (e: Exception) {
                            // Propagate image error warning to console or state
                            println("Image generation failed: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                }

                generatedImageUrl.value = imageUrl
                _uiState.value = UiState.Success(postContent)
                editablePostText.value = postContent
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to generate post. Check internet connection and API keys.")
            }
        }
    }

    val linkedinUserName = MutableStateFlow("")
    val linkedinUserEmail = MutableStateFlow("")

    fun fetchUserProfile() {
        if (!linkedinRepository.isAuthenticated.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = linkedinRepository.fetchProfileInfo()
            result.onSuccess { details ->
                linkedinUserName.value = details["name"] ?: "LinkedIn User"
                linkedinUserEmail.value = details["email"] ?: ""
            }.onFailure {
                linkedinUserName.value = "LinkedIn User"
            }
        }
    }

    init {
        fetchUserProfile()
    }

    fun logoutLinkedIn() {
        linkedinRepository.logout()
        linkedinUserName.value = ""
        linkedinUserEmail.value = ""
    }

    fun publishToLinkedIn(onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        val postText = editablePostText.value
        if (postText.isBlank()) {
            onFailure("No content to publish.")
            return
        }
        
        val imageUrl = generatedImageUrl.value
        viewModelScope.launch(Dispatchers.IO) {
            val result = linkedinRepository.publishPost(postText, imageUrl)
            result.onSuccess { msg ->
                onSuccess(msg)
            }.onFailure { err ->
                onFailure(err.message ?: "Failed to publish post")
            }
        }
    }

    fun finishAuthFlow(code: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = linkedinRepository.handleAuthCallback(
                code = code,
                clientId = linkedinClientId.value,
                clientSecret = linkedinClientSecret.value,
                redirectUri = linkedinRedirectUri.value
            )
            result.onSuccess {
                fetchUserProfile()
                onSuccess()
            }.onFailure { err ->
                onFailure(err.message ?: "OAuth verification failed")
            }
        }
    }

    fun resetToIdle() {
        _uiState.value = UiState.Idle
    }
}
