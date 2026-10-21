package dev.huecoder.md3agent

import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.huecoder.md3agent.ui.text.Strings

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = Strings.get(R.string.app_name)) {
        MaterialExpressiveTheme { LoadingIndicator() }
    }
}
