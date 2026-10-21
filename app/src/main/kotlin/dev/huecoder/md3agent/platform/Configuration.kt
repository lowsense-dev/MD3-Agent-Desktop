package dev.huecoder.md3agent.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/** Window size in dp, standing in for Android's screen configuration (on the desktop the "screen" is the window). */
class Configuration(val screenWidthDp: Int, val screenHeightDp: Int) {
    val locales: List<java.util.Locale> get() = listOf(dev.huecoder.md3agent.ui.text.Strings.locale)
}

object LocalConfiguration {
    val current: Configuration
        @Composable
        @ReadOnlyComposable
        get() {
            val size = LocalWindowInfo.current.containerSize
            val density = LocalDensity.current.density
            return Configuration((size.width / density).toInt(), (size.height / density).toInt())
        }
}
