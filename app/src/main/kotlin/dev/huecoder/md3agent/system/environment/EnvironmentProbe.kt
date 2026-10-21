package dev.huecoder.md3agent.system.environment

import dev.huecoder.md3agent.data.settings.ShellMode
import dev.huecoder.md3agent.system.dbus.SessionBus
import dev.huecoder.md3agent.system.shell.AccessStatus
import dev.huecoder.md3agent.system.shell.ProcessRunner
import dev.huecoder.md3agent.system.shell.ShellAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Looks for each optional system component the app can use. Nothing here changes the system. */
class EnvironmentProbe(private val bus: SessionBus, private val shells: ShellAccess?) {
    suspend fun collect(): List<ComponentStatus> = withContext(Dispatchers.IO) {
        Component.entries.map { component -> runCatching { probe(component) }.getOrElse { ComponentStatus(component, false, it.message) } }
    }

    private suspend fun probe(component: Component): ComponentStatus = when (component) {
        Component.BWRAP -> {
            val status = shells?.status(ShellMode.SANDBOX) ?: if (Programs.find("bwrap") != null) AccessStatus.READY else AccessStatus.NOT_INSTALLED
            ComponentStatus(component, status == AccessStatus.READY, shells?.unavailableReason(ShellMode.SANDBOX))
        }
        Component.PYTHON -> program(component, "python3", "--version")
        Component.PYTHON_VENV -> {
            val python = Programs.find("python3")
            val ok = python != null && ProcessRunner.run(listOf(python.path, "-c", "import venv, ensurepip"), TIMEOUT_MS).exitCode == 0
            ComponentStatus(component, ok, null)
        }
        Component.WL_PASTE -> program(component, "wl-paste")
        Component.XCLIP -> ComponentStatus(component, Programs.find("xclip") != null || Programs.find("xsel") != null, null)
        Component.PORTAL -> busName(component, "org.freedesktop.portal.Desktop")
        Component.SECRET_SERVICE -> busName(component, "org.freedesktop.secrets")
        Component.NOTIFICATIONS -> busName(component, "org.freedesktop.Notifications")
        Component.SYSTEMD_USER -> {
            val state = if (Programs.find("systemctl") != null) ProcessRunner.run(listOf("systemctl", "--user", "is-system-running"), TIMEOUT_MS).stdout.trim() else ""
            ComponentStatus(component, state in setOf("running", "degraded"), state.ifEmpty { null })
        }
        Component.SPEECH -> program(component, "spd-say")
    }

    private fun program(component: Component, name: String, versionFlag: String? = null): ComponentStatus {
        val found = Programs.find(name) ?: return ComponentStatus(component, false, null)
        val version = versionFlag?.let { flag -> ProcessRunner.run(listOf(found.path, flag), TIMEOUT_MS).let { (it.stdout + it.stderr).trim().lineSequence().firstOrNull() } }
        return ComponentStatus(component, true, version ?: found.path)
    }

    // Activatable services count: the bus starts them on first use.
    private fun busName(component: Component, name: String): ComponentStatus {
        val dbus = bus.connection?.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", org.freedesktop.dbus.interfaces.DBus::class.java)
            ?: return ComponentStatus(component, false, "no session D-Bus")
        val names = dbus.ListNames().toSet() + dbus.ListActivatableNames().toSet()
        return ComponentStatus(component, name in names, null)
    }

    private companion object {
        const val TIMEOUT_MS = 3_000L
    }
}
