package com.omniguard.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography

val WearDarkBackground = Color(0xFF000000)
val WearSurfaceDark = Color(0xFF161618)
val WearEmergencyRed = Color(0xFFFF3B30)
val WearSafetyGreen = Color(0xFF34C759)
val WearWarningOrange = Color(0xFFFF9500)
val WearNavCyan = Color(0xFF0A84FF)
val WearTextPrimary = Color(0xFFFFFFFF)
val WearTextSecondary = Color(0xFFB0B0B6)

private val WearColorScheme = ColorScheme(
    primary = WearNavCyan,
    primaryContainer = Color(0xFF003262),
    onPrimary = Color.White,
    secondary = WearSafetyGreen,
    secondaryContainer = Color(0xFF0B3D1B),
    onSecondary = Color.White,
    error = WearEmergencyRed,
    errorContainer = Color(0xFF5A0000),
    onError = Color.White,
    background = WearDarkBackground,
    onBackground = WearTextPrimary,
    surfaceContainer = WearSurfaceDark,
    onSurface = WearTextPrimary,
    onSurfaceVariant = WearTextSecondary
)

@Composable
fun OmniGuardWearTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WearColorScheme,
        typography = Typography(),
        content = content
    )
}
