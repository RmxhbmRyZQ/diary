package com.secretdiary.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun SecretDiaryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightWarmColors,
        typography = DiaryTypography,
        content = content
    )
}
