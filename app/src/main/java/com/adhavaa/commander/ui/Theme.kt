package com.adhavaa.commander.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Field palette: amber on olive-black at night, deep amber on parchment by day.
private val Dark = darkColorScheme(
    primary = Color(0xFFE8A33D),
    onPrimary = Color(0xFF2A1A00),
    secondary = Color(0xFF9DB57A),
    tertiary = Color(0xFF8FB3E0),
    background = Color(0xFF121411),
    onBackground = Color(0xFFE6E3DA),
    surface = Color(0xFF121411),
    onSurface = Color(0xFFE6E3DA),
    surfaceVariant = Color(0xFF22261F),
    onSurfaceVariant = Color(0xFFB9B6AB),
    surfaceContainer = Color(0xFF1B1E19),
    outline = Color(0xFF6A6D62),
)

private val Light = lightColorScheme(
    primary = Color(0xFF8A5A00),
    onPrimary = Color.White,
    secondary = Color(0xFF4F6B2A),
    tertiary = Color(0xFF2F5E95),
    background = Color(0xFFF6F4EE),
    onBackground = Color(0xFF1B1C18),
    surface = Color(0xFFF6F4EE),
    onSurface = Color(0xFF1B1C18),
    surfaceVariant = Color(0xFFE9E6DC),
    onSurfaceVariant = Color(0xFF4A4A42),
    surfaceContainer = Color(0xFFEFECE4),
    outline = Color(0xFF7A7B70),
)

@Composable
fun CommanderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
