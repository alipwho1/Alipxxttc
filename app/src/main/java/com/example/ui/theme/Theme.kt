package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    secondary = EmeraldSecondaryDark,
    background = EmeraldBackgroundDark,
    surface = EmeraldSurfaceDark,
    onPrimary = EmeraldBackgroundDark,
    onSecondary = TextPrimaryDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    primaryContainer = BubbleSelfDark,
    secondaryContainer = BubbleOtherDark
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    secondary = EmeraldSecondaryLight,
    background = EmeraldBackgroundLight,
    surface = EmeraldSurfaceLight,
    onPrimary = EmeraldSurfaceLight,
    onSecondary = TextPrimaryLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    primaryContainer = BubbleSelfLight,
    secondaryContainer = BubbleOtherLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // default to premium Dark Mode as preferred by users
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
