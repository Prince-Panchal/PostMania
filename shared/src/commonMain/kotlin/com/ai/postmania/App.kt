package com.ai.postmania

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
// We will use unicode characters in buttons for cross-platform reliability without extra icon dependencies.
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.postmania.domain.model.AiProvider
import com.ai.postmania.presentation.PostManiaViewModel
import com.ai.postmania.presentation.UiState
import com.ai.postmania.presentation.theme.PostManiaTheme
import kotlinx.coroutines.delay

@Composable
fun App(viewModel: PostManiaViewModel = remember { PostManiaViewModel() }) {
    PostManiaTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val showSettings by viewModel.showSettings.collectAsState()
            val uiState by viewModel.uiState.collectAsState()

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeContentPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Bar
                    HeaderSection(
                        viewModel = viewModel,
                        onOpenSettings = { viewModel.showSettings.value = true }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    AnimatedContent(
                        targetState = uiState,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        }
                    ) { state ->
                        when (state) {
                            is UiState.Idle, is UiState.Error -> {
                                HomeScreen(viewModel = viewModel, errorMsg = (state as? UiState.Error)?.message)
                            }
                            is UiState.Loading -> {
                                LoadingScreen()
                            }
                            is UiState.Success -> {
                                ResultScreen(viewModel = viewModel)
                            }
                        }
                    }
                }

                // API Key Settings Modal
                if (showSettings) {
                    SettingsDialog(
                        viewModel = viewModel,
                        onDismiss = { viewModel.showSettings.value = false }
                    )
                }

                // LinkedIn OAuth manual entry Callback simulation dialog for ease of testing
                val showLinkedInAuth by viewModel.showLinkedInAuthDialog.collectAsState()
                if (showLinkedInAuth) {
                    LinkedInAuthDialog(
                        viewModel = viewModel,
                        onDismiss = { viewModel.showLinkedInAuthDialog.value = false }
                    )
                }
            }
        }
    }
}

@Composable
fun HeaderSection(viewModel: PostManiaViewModel, onOpenSettings: () -> Unit) {
    val isAuthenticated by viewModel.linkedinRepository.isAuthenticated.collectAsState()
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "PostMania",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )
            )
            Text(
                text = "AI LinkedIn Post Strategist",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.secondary
                )
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { 
                    viewModel.showLinkedInAuthDialog.value = true
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isAuthenticated) Color(0xFF0077B5) else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = if (isAuthenticated) "👤" else "🔑",
                    fontSize = 16.sp,
                    color = if (isAuthenticated) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "⚙️",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: PostManiaViewModel, errorMsg: String?) {
    val input by viewModel.inputPrompt.collectAsState()
    val tone by viewModel.selectedTone.collectAsState()
    val length by viewModel.selectedLength.collectAsState()
    val audience by viewModel.selectedAudience.collectAsState()
    val provider by viewModel.selectedProvider.collectAsState()

    val examples = listOf(
        "Implemented SSL Pinning in Android client app.",
        "Created an API networking module with Kotlin Multiplatform.",
        "Led the core mobile release workflow successfully.",
        "Improved initial app startup time by 45% using baseline profiles."
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Generate Professional Post",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        
        Spacer(modifier = Modifier.height(12.dp))

        // Large Multiline Input Field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            if (input.isEmpty()) {
                Text(
                    text = "Describe your idea, project, or achievement in one or two lines...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                    )
                )
            }
            BasicTextField(
                value = input,
                onValueChange = { viewModel.inputPrompt.value = it },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxSize()
            )
        }

        if (errorMsg != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMsg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Example Badges
        Text(
            text = "Examples:",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.secondary)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            examples.take(2).forEach { example ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .clickable { viewModel.inputPrompt.value = example }
                        .padding(8.dp)
                ) {
                    Text(
                        text = example,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 11.sp
                        ),
                        maxLines = 2
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))

        // Dropdowns / Option selectors
        OptionsSelector(
            label = "Tone",
            options = listOf("Professional", "Technical", "Storytelling", "Friendly", "Thought Leadership"),
            selected = tone,
            onSelect = { viewModel.selectedTone.value = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OptionsSelector(
            label = "Length",
            options = listOf("Short", "Medium", "Long"),
            selected = length,
            onSelect = { viewModel.selectedLength.value = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OptionsSelector(
            label = "Audience",
            options = listOf("Recruiters", "Developers", "Managers", "Founders", "General"),
            selected = audience,
            onSelect = { viewModel.selectedAudience.value = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OptionsSelector(
            label = "AI Engine",
            options = AiProvider.entries.map { it.name },
            selected = provider.name,
            onSelect = { viewModel.selectedProvider.value = AiProvider.valueOf(it) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Image Generation Toggle Switch
        val isImageToggled by viewModel.generateImageWithPost.collectAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.generateImageWithPost.value = !isImageToggled },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Checkbox(
                checked = isImageToggled,
                onCheckedChange = { viewModel.generateImageWithPost.value = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.secondary
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = "Generate AI Visual",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Uses OpenAI DALL-E-3 to create a custom graphic",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.secondary)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Button
        Button(
            onClick = { viewModel.generatePost() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Generate Post", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OptionsSelector(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Medium
            )
        )

        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selected,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = "▼",
                    fontSize = 10.sp,
                    color = Color.White
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = Color.White) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    val dots = remember { mutableStateOf(".") }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            dots.value = when (dots.value) {
                "." -> ".."
                ".." -> "..."
                else -> "."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Strategizing post${dots.value}",
            style = MaterialTheme.typography.titleMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = "Applying copywriting standards...",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.secondary
            )
        )
    }
}

@Composable
fun ResultScreen(viewModel: PostManiaViewModel) {
    val postText by viewModel.editablePostText.collectAsState()
    val isPublishing by viewModel.linkedinRepository.isPublishing.collectAsState()
    val isAuth by viewModel.linkedinRepository.isAuthenticated.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var publishStatusMessage by remember { mutableStateOf<String?>(null) }
    var publishStatusColor by remember { mutableStateOf(Color.Green) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Generated Strategy",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )

            IconButton(
                onClick = { viewModel.resetToIdle() }
            ) {
                Text(
                    text = "✕",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val imageUrl by viewModel.generatedImageUrl.collectAsState()

        if (!imageUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
                    .clickable {
                        // Open generated image link
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🖼️ AI Visual Generated", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("URL: ${imageUrl!!.take(45)}...", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Editable Post Output
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            BasicTextField(
                value = postText,
                onValueChange = { viewModel.editablePostText.value = it },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status Feedback (e.g. Success publishing)
        publishStatusMessage?.let { msg ->
            Text(
                text = msg,
                color = publishStatusColor,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        val errorColor = MaterialTheme.colorScheme.error

        // Actions Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Copy
            Button(
                onClick = { clipboardManager.setText(AnnotatedString(postText)) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("📋 Copy", fontSize = 13.sp)
            }

            // Regenerate
            Button(
                onClick = { viewModel.generatePost() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("🔄 Retry", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // LinkedIn Publish Button
        if (isPublishing) {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0077B5)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Publishing...", fontSize = 14.sp)
            }
        } else {
            Button(
                onClick = {
                    if (!isAuth) {
                        viewModel.showLinkedInAuthDialog.value = true
                    } else {
                        viewModel.publishToLinkedIn(
                            onSuccess = { msg ->
                                publishStatusMessage = msg
                                publishStatusColor = Color.Green
                            },
                            onFailure = { err ->
                                publishStatusMessage = err
                                publishStatusColor = errorColor
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0077B5),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isAuth) "📤 Publish to LinkedIn" else "📤 Connect LinkedIn & Publish",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SettingsDialog(viewModel: PostManiaViewModel, onDismiss: () -> Unit) {
    var geminiKey by remember { mutableStateOf(viewModel.geminiApiKey.value) }
    var openAiKey by remember { mutableStateOf(viewModel.openAiApiKey.value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API Configuration", color = Color.White) },
        text = {
            Column {
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it },
                    label = { Text("Gemini API Key") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = openAiKey,
                    onValueChange = { openAiKey = it },
                    label = { Text("OpenAI API Key") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.saveApiKeys(geminiKey, openAiKey)
                    onDismiss()
                }
            ) {
                Text("Save Keys")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun LinkedInAuthDialog(viewModel: PostManiaViewModel, onDismiss: () -> Unit) {
    val isAuthenticated by viewModel.linkedinRepository.isAuthenticated.collectAsState()
    val profileName by viewModel.linkedinUserName.collectAsState()
    val profileEmail by viewModel.linkedinUserEmail.collectAsState()
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = if (isAuthenticated) "LinkedIn Active Profile" else "LinkedIn Auth Integration", 
                color = Color.White
            ) 
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (isAuthenticated) {
                    // Profile details dashboard layout
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = profileName.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column {
                                    Text(
                                        text = profileName.ifBlank { "LinkedIn Member" },
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    if (profileEmail.isNotBlank()) {
                                        Text(
                                            text = profileEmail,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = { 
                                    viewModel.logoutLinkedIn() 
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Log out Account", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Sign in to connect your LinkedIn account to PostMania. Logging in will authorize publishing posts directly on your behalf.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.secondary)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val uriHandler = LocalUriHandler.current
                    Button(
                        onClick = {
                            val authUrl = viewModel.linkedinRepository.getAuthUrl(
                                clientId = viewModel.linkedinClientId.value,
                                redirectUri = viewModel.linkedinRedirectUri.value,
                                state = "postmania_state"
                            )
                            uriHandler.openUri(authUrl)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0077B5),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Sign in with LinkedIn", fontWeight = FontWeight.Bold)
                    }

                    errorMsg?.let { err ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}