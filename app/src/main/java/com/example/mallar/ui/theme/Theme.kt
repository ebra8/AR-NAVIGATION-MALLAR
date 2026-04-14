package com.example.mallar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val MallARColorScheme = lightColorScheme(
    primary = Teal,
    onPrimary = White,
    secondary = DarkTeal,
    onSecondary = White,
    background = White,
    onBackground = TextPrimary,
    surface = White,
    onSurface = TextPrimary,
    surfaceVariant = LightGray,
    onSurfaceVariant = TextSecondary
)

@Composable
fun MallARTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MallARColorScheme,
        typography = Typography,
        content = content
    )
}