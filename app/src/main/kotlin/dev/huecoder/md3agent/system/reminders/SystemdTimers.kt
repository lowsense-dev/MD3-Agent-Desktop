package dev.huecoder.md3agent.system.reminders

import dev.huecoder.md3agent.system.shell.ShellResult
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reminders that fire while the app is closed: one systemd user timer per reminder, which launches
 * `md3-agent --reminder <id>`; a running app gets that through the instance socket.
 */
class SystemdTimers(
    private val unitDir: File,
    private val launcher: String,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val run: (List<String>) -> ShellResult,
) {
    /** The user manager answers "running" or "degraded" (some unit failed, timers still work). */
    fun available(): Boolean = runCatching {
        run(listOf("systemctl", "--user", "is-system-running")).stdout.trim() in setOf("running", "degraded")
    }.getOrDefault(false)

    fun install(id: String, atEpochMs: Long) {
        unitDir.mkdirs()
        val name = unitName(id)
        val calendar = CALENDAR.format(Instant.ofEpochMilli(atEpochMs).atZone(zone))
        File(unitDir, "$name.service").writeText(
            """
            [Unit]
            Description=MD3 Agent reminder $id

            [Service]
            Type=oneshot
            ExecStart=${quoted(launcher)} --reminder $id
            """.trimIndent() + "\n",
        )
        File(unitDir, "$name.timer").writeText(
            """
            [Unit]
            Description=MD3 Agent reminder $id

            [Timer]
            OnCalendar=$calendar
            AccuracySec=1s
            Persistent=true

            [Install]
            WantedBy=timers.target
            """.trimIndent() + "\n",
        )
        run(listOf("systemctl", "--user", "daemon-reload"))
        run(listOf("systemctl", "--user", "enable", "--now", "$name.timer"))
    }

    fun remove(id: String) {
        val name = unitName(id)
        run(listOf("systemctl", "--user", "disable", "--now", "$name.timer"))
        File(unitDir, "$name.timer").delete()
        File(unitDir, "$name.service").delete()
        run(listOf("systemctl", "--user", "daemon-reload"))
    }

    private fun unitName(id: String) = "md3-agent-reminder-${id.filter { it.isLetterOrDigit() || it == '-' }}"

    // systemd splits ExecStart on spaces unless the word is quoted.
    private fun quoted(path: String) = if (path.any { it.isWhitespace() }) "\"$path\"" else path

    private companion object {
        val CALENDAR: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
