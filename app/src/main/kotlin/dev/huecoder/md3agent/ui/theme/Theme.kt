package dev.huecoder.md3agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamicColorScheme

/** The desktop's accent colour (from the appearance portal), the stand-in for Android's wallpaper colours. */
val LocalSystemAccent = staticCompositionLocalOf<Color?> { null }

@Composable
fun AssistantTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val accent = LocalSystemAccent.current
    val colorScheme = remember(accent, darkTheme) {
        when {
            accent != null -> dynamicColorScheme(seedColor = accent, isDark = darkTheme)
            darkTheme -> BrandColors.dark
            else -> BrandColors.light
        }
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = AssistantShapes,
        typography = AssistantTypography,
        content = content,
    )
}
