package dev.huecoder.md3agent

import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.huecoder.md3agent.system.instance.InstanceSocket
import dev.huecoder.md3agent.system.instance.LaunchArgs
import dev.huecoder.md3agent.system.instance.LaunchCommand
import dev.huecoder.md3agent.system.paths.AppPaths
import dev.huecoder.md3agent.ui.text.Strings
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val command = LaunchArgs.parse(args)
    if (command == LaunchCommand.Version) {
        println("md3-agent ${BuildConfig.VERSION_NAME}")
        return
    }
    val paths = AppPaths()
    val socket = InstanceSocket(paths.runtime.resolve("md3-agent.sock"))
    if (socket.tryForward(command)) exitProcess(0)
    application {
        Window(onCloseRequest = ::exitApplication, title = Strings.get(R.string.app_name)) {
            MaterialExpressiveTheme { LoadingIndicator() }
        }
    }
}
