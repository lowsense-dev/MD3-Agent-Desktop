package dev.huecoder.md3agent.system.environment

enum class Component(val program: String) {
    BWRAP("bwrap"), PYTHON("python3"), PYTHON_VENV("python3 -m venv"), WL_PASTE("wl-paste"), XCLIP("xclip"),
    PORTAL("xdg-desktop-portal"), SECRET_SERVICE("org.freedesktop.secrets"), NOTIFICATIONS("org.freedesktop.Notifications"),
    SYSTEMD_USER("systemctl --user"), SPEECH("spd-say");

    /** The package to install for this component on [pm]; null where there is no single package to suggest. */
    fun packageName(pm: PackageManager): String? = when (this) {
        BWRAP -> "bubblewrap"
        PYTHON -> if (pm == PackageManager.PACMAN) "python" else "python3"
        PYTHON_VENV -> when (pm) {
            PackageManager.APT -> "python3-venv"
            PackageManager.PACMAN -> "python"
            else -> "python3"
        }
        WL_PASTE -> "wl-clipboard"
        XCLIP -> "xclip"
        PORTAL -> "xdg-desktop-portal"
        SECRET_SERVICE -> "gnome-keyring"
        NOTIFICATIONS, SYSTEMD_USER -> null
        SPEECH -> "speech-dispatcher"
    }
}

data class ComponentStatus(val component: Component, val found: Boolean, val detail: String?)

object EnvironmentReport {
    /** Plain-text report for `md3-agent --check-environment`. */
    fun format(statuses: List<ComponentStatus>, distro: Distro): String = buildString {
        appendLine("MD3 Agent environment on ${distro.pretty}")
        val pm = distro.packageManager()
        statuses.forEach { status ->
            val mark = if (status.found) "ok     " else "missing"
            append("  [$mark] ").append(status.component.name.lowercase().replace('_', '-')).append(" (").append(status.component.program).append(")")
            status.detail?.takeIf { status.found }?.let { append(": ").append(it) }
            appendLine()
            if (!status.found) status.component.packageName(pm)?.let { appendLine("            install: ${pm.installCommand(it)}") }
        }
    }.trimEnd()
}
