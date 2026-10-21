package dev.huecoder.md3agent

import androidx.compose.ui.window.application
import dev.huecoder.md3agent.app.AppWindows
import dev.huecoder.md3agent.app.DesktopApp
import dev.huecoder.md3agent.system.instance.InstanceSocket
import dev.huecoder.md3agent.system.instance.LaunchArgs
import dev.huecoder.md3agent.system.instance.LaunchCommand
import dev.huecoder.md3agent.system.paths.AppPaths
import dev.huecoder.md3agent.system.shortcuts.GlobalShortcuts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val command = LaunchArgs.parse(args)
    if (command == LaunchCommand.Version) {
        println("md3-agent ${BuildConfig.VERSION_NAME}")
        return
    }
    if (command == LaunchCommand.CheckEnvironment) {
        exitProcess(checkEnvironment())
    }
    val paths = AppPaths()
    val socket = InstanceSocket(paths.runtime.resolve("md3-agent.sock"))
    if (socket.tryForward(command)) exitProcess(0)

    val container = AppContainer(paths, launcher = System.getProperty("md3agent.launcher") ?: "md3-agent")
    // The first frame already in the chosen language, not $LANG for a moment.
    dev.huecoder.md3agent.ui.text.Strings.locale = kotlinx.coroutines.runBlocking { container.settings.current().ui.language }.locale(dev.huecoder.md3agent.app.SYSTEM_LOCALE)
    val windows = AppWindows()
    val handle: (LaunchCommand) -> Unit = { next ->
        when (next) {
            LaunchCommand.Overlay -> windows.overlayVisible.value = true
            is LaunchCommand.Chat -> windows.showChat(next.id)
            is LaunchCommand.Reminder -> container.applicationScope.launch { container.reminders.fire(next.id) }
            else -> windows.mainVisible.value = true
        }
    }
    container.reminderAlarms.onOpenChat = { chatId -> windows.showChat(chatId) }
    // Local MCP servers are child processes; they must not outlive the app.
    Runtime.getRuntime().addShutdownHook(Thread { container.mcp.closeAll() })
    socket.listen(handle)

    // Started by a reminder timer: no main window, just the notification or alarm; quit when nothing is left to show.
    if (command is LaunchCommand.Reminder) {
        windows.mainVisible.value = false
        container.applicationScope.launch {
            delay(BACKGROUND_LINGER_MS)
            while (container.reminderAlarms.active.value.isNotEmpty() || windows.mainVisible.value || windows.overlayVisible.value) delay(BACKGROUND_LINGER_MS)
            exitProcess(0)
        }
    }
    handle(command)
    container.applicationScope.launch { GlobalShortcuts(container.bus).bind { windows.overlayVisible.value = true } }

    application(exitProcessOnExit = true) { DesktopApp(container, windows) }
}

private const val BACKGROUND_LINGER_MS = 120_000L
