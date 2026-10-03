package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AutoDarkColorScheme = darkColorScheme(
    primary = AutoAmberPrimary,
    onPrimary = AutoOnPrimaryContainer,
    primaryContainer = AutoAmberContainer,
    onPrimaryContainer = AutoOnPrimaryContainer,
    secondary = AutoAmberFixedDim,
    background = AutoBackground,
    onBackground = AutoOnSurface,
    surface = AutoSurface,
    onSurface = AutoOnSurface,
    surfaceVariant = AutoSurfaceHigh,
    onSurfaceVariant = AutoOnSurfaceVariant,
    error = AutoStatusCancelled
)

@Composable
fun AutoAlertTheme(
    isMalayalam: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AutoDarkColorScheme,
        typography = getAppTypography(isMalayalam),
        content = content
    )
}

