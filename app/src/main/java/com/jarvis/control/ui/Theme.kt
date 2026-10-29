package com.jarvis.control.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF070B10)
val Panel = Color(0xFF0D141C)
val Line = Color(0xFF1C2A38)
val TextMain = Color(0xFFCFE7F5)
val Dim = Color(0xFF6F8799)
val Accent = Color(0xFF2EE6FF)
val Accent2 = Color(0xFF0AA5C0)
val Ok = Color(0xFF3DDC84)
val Err = Color(0xFFFF5B5B)

private val JarvisColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF001018),
    secondary = Accent2,
    background = Bg,
    onBackground = TextMain,
    surface = Panel,
    onSurface = TextMain,
    surfaceVariant = Line,
    error = Err,
    outline = Line,
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme() // app is always dark
    MaterialTheme(colorScheme = JarvisColors, content = content)
}
