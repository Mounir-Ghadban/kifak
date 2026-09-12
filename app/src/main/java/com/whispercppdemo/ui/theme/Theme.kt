package com.whispercppdemo.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NeoColorScheme = lightColorScheme(
    primary = NeoAccent,
    secondary = NeoBrown,
    background = NeoBackground,
    surface = NeoSurface,
    onPrimary = NeoOnAccent,
    onBackground = NeoText,
    onSurface = NeoText
)

@Composable
fun WhisperCppDemoTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Fixed palette derived from the Kifak artwork, regardless of system theme.
    val colorScheme = NeoColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = NeoBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
