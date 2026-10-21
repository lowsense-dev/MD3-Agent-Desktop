package dev.huecoder.md3agent.system.reminders

import dev.huecoder.md3agent.system.shell.ShellResult
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemdTimersTest {
    private val dir = createTempDirectory("md3-units").toFile()
    private val commands = mutableListOf<List<String>>()
    private val zone = ZoneId.of("Europe/Moscow")
    private val timers = SystemdTimers(dir, launcher = "/usr/bin/md3-agent", zone = zone) { commands += it; ShellResult(0, "running\n", "") }
    private val at = LocalDateTime.of(2026, 10, 21, 18, 30, 5).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun timerUnitHasCalendarAndPersistent() {
        timers.install("ab12cd34", at)
        val timer = dir.resolve("md3-agent-reminder-ab12cd34.timer").readText()
        assertContains(timer, "OnCalendar=2026-10-21 18:30:05")
        assertContains(timer, "Persistent=true")
        assertContains(timer, "AccuracySec=1s")
        assertEquals(listOf("systemctl", "--user", "daemon-reload"), commands[0])
        assertEquals(listOf("systemctl", "--user", "enable", "--now", "md3-agent-reminder-ab12cd34.timer"), commands[1])
    }

    @Test
    fun serviceUnitRunsLauncherWithReminderId() {
        timers.install("ab12cd34", at)
        val service = dir.resolve("md3-agent-reminder-ab12cd34.service").readText()
        assertContains(service, "Type=oneshot")
        assertContains(service, "ExecStart=/usr/bin/md3-agent --reminder ab12cd34")
    }

    @Test
    fun removeDisablesAndDeletesUnits() {
        timers.install("ab12cd34", at)
        commands.clear()
        timers.remove("ab12cd34")
        assertEquals(listOf("systemctl", "--user", "disable", "--now", "md3-agent-reminder-ab12cd34.timer"), commands[0])
        assertFalse(dir.resolve("md3-agent-reminder-ab12cd34.timer").exists())
        assertFalse(dir.resolve("md3-agent-reminder-ab12cd34.service").exists())
    }

    @Test
    fun availableWhenUserManagerRunsOrIsDegraded() {
        assertTrue(SystemdTimers(dir, "x") { ShellResult(1, "degraded\n", "") }.available())
        assertFalse(SystemdTimers(dir, "x") { ShellResult(1, "", "Failed to connect to bus") }.available())
    }

    @Test
    fun launcherWithSpacesIsQuoted() {
        SystemdTimers(dir, launcher = "/opt/MD3 Agent/bin/md3-agent") { ShellResult(0, "", "") }.install("x1", at)
        assertContains(dir.resolve("md3-agent-reminder-x1.service").readText(), "ExecStart=\"/opt/MD3 Agent/bin/md3-agent\" --reminder x1")
    }
}
