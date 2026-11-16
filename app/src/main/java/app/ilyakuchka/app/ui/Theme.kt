package app.ilyakuchka.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.ilyakuchka.app.data.Prefs

private val Orange = Color(0xFFFF6A00)

private val Light = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    secondary = Color(0xFF8A4B00),
    background = Color(0xFFFFFBF8),
    surface = Color(0xFFFFFBF8)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF8A3D),
    onPrimary = Color(0xFF3A1A00),
    secondary = Color(0xFFFFB68A),
    background = Color(0xFF121212),
    surface = Color(0xFF181818)
)

@Composable
fun EmptyTheme(content: @Composable () -> Unit) {
    val dark = when (Prefs.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
