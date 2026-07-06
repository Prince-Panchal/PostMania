package com.ai.postmania.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF0A66C2),        // Official LinkedIn Blue
    onPrimary = Color(0xFFFFFFFF),      // Crisp White
    primaryContainer = Color(0xFF1D2226),// Deep slate card container
    onPrimaryContainer = Color(0xFFF3F2EF),
    secondary = Color(0xFF70B5F9),      // Soft Accent Blue
    onSecondary = Color(0xFF030712),
    background = Color(0xFF1D2226),     // Professional Slate Gray
    onBackground = Color(0xFFF3F2EF),   // Warm off-white
    surface = Color(0xFF2F353A),        // Contrasting Card Slate
    onSurface = Color(0xFFF3F2EF),
    surfaceVariant = Color(0xFF1D2226),
    onSurfaceVariant = Color(0xFF70B5F9),
    outline = Color(0xFF434A52)         // Soft Border Outlines
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF111827),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3F4F6),
    onPrimaryContainer = Color(0xFF111827),
    secondary = Color(0xFF4B5563),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF9FAFB),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFD1D5DB)
)

@Composable
fun PostManiaTheme(
    darkTheme: Boolean = true, // Force premium dark theme by default
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
