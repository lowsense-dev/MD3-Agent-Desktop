package dev.huecoder.md3agent

import dev.huecoder.md3agent.system.dbus.SessionBus
import dev.huecoder.md3agent.system.environment.Distro
import dev.huecoder.md3agent.system.environment.EnvironmentProbe
import dev.huecoder.md3agent.system.environment.EnvironmentReport
import dev.huecoder.md3agent.system.paths.AppPaths
import dev.huecoder.md3agent.system.shell.ShellAccess
import kotlinx.coroutines.runBlocking

/** `md3-agent --check-environment`: prints what this computer has for the optional features and exits; no window. */
internal fun checkEnvironment(): Int {
    System.setProperty("java.awt.headless", "true")
    val statuses = runBlocking { EnvironmentProbe(SessionBus.connect(), ShellAccess(AppPaths())).collect() }
    println(EnvironmentReport.format(statuses, Distro.read()))
    return 0
}
