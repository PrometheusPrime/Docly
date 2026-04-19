package com.docly.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val doclyDarkColorScheme = darkColorScheme(
    primary = doclyScanGreenDark,
    onPrimary = doclyPaperDark,
    primaryContainer = doclyScanGreenContainerDark,
    onPrimaryContainer = doclyScanGreenDark,
    secondary = doclySlateDark,
    onSecondary = doclyPaperDark,
    tertiary = doclyAmberDark,
    onTertiary = doclyPaperDark,
    background = doclyPaperDark,
    onBackground = doclyPaper,
    surface = doclyPaperDark,
    onSurface = doclyPaper,
    surfaceVariant = doclyPaperDarkRaised,
    onSurfaceVariant = Color(0xFFC5D0CB),
    error = doclyErrorDark,
    onError = doclyPaperDark
)

private val doclyLightColorScheme = lightColorScheme(
    primary = doclyScanGreen,
    onPrimary = doclyPaperRaised,
    primaryContainer = doclyScanGreenContainer,
    onPrimaryContainer = Color(0xFF063D32),
    secondary = doclySlate,
    onSecondary = doclyPaperRaised,
    tertiary = doclyAmber,
    onTertiary = doclyPaperRaised,
    background = doclyPaper,
    onBackground = doclyInk,
    surface = doclyPaperRaised,
    onSurface = doclyInk,
    surfaceVariant = Color(0xFFE3E7E0),
    onSurfaceVariant = doclyInkMuted,
    error = doclyError,
    onError = doclyPaperRaised
)

@Composable
fun DoclyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> doclyDarkColorScheme

        else -> doclyLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = doclyTypography,
        content = content
    )
}
