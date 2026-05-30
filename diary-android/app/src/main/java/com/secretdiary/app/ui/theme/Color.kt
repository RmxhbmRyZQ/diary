package com.secretdiary.app.ui.theme

import androidx.compose.ui.graphics.Color

// 温暖色调
val WarmWhite = Color(0xFFFFF8F0)
val WarmPink = Color(0xFFFCE4EC)
val WarmSage = Color(0xFFC8E6C9)
val WarmPrimary = Color(0xFFD4A574)
val WarmPrimaryDark = Color(0xFFB8860B)
val WarmSurface = Color(0xFFFFFDF5)
val WarmOnSurface = Color(0xFF3E2723)
val WarmSecondary = Color(0xFFE8B4B8)
val WarmError = Color(0xFFD32F2F)

// Material 3 Light ColorScheme
val LightWarmColors = androidx.compose.material3.lightColorScheme(
    primary = WarmPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB3),
    secondary = WarmSecondary,
    tertiary = WarmSage,
    background = WarmWhite,
    surface = WarmSurface,
    onBackground = WarmOnSurface,
    onSurface = WarmOnSurface,
    error = WarmError,
    errorContainer = WarmPink
)
