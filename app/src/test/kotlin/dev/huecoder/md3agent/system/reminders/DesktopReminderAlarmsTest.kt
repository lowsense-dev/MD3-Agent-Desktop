package dev.huecoder.md3agent.system.reminders

import dev.huecoder.md3agent.data.db.ReminderEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopReminderAlarmsTest {
    private data class Shown(val title: String, val body: String, val actions: List<String>)

    init {
        dev.huecoder.md3agent.ui.text.Strings.locale = java.util.Locale.ENGLISH
    }

    private val shown = mutableListOf<Shown>()
    private val due = mutableListOf<String>()

    private fun TestScope.alarms(): DesktopReminderAlarms = DesktopReminderAlarms(
        notify = { title, body, actions, _ -> shown += Shown(title, body, actions.map { it.first }); 1 },
        timers = null,
        scope = backgroundScope,
        clock = { testScheduler.currentTime },
    ).also { it.onDue = { id -> due += id } }

    private fun reminder(id: String, at: Long, sound: Boolean = false) = ReminderEntity(id = id, text = "Text $id", triggerAt = at, chatId = "c1", createdAt = 0, sound = sound)

    @Test
    fun firesWhenDue() = runTest {
        val alarms = alarms()
        alarms.schedule(reminder("a", at = 5_000))
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(emptyList(), due)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("a"), due)
    }

    @Test
    fun cancelledReminderNeverFires() = runTest {
        val alarms = alarms()
        alarms.schedule(reminder("a", at = 5_000))
        alarms.cancel("a")
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(emptyList(), due)
    }

    @Test
    fun soundReminderBecomesActiveAlarmWithStopAndSnooze() = runTest {
        val alarms = alarms()
        alarms.notify(reminder("t", at = 0, sound = true))
        runCurrent()
        assertEquals(listOf("t"), alarms.active.value.map { it.reminder.id })
        assertEquals(listOf("stop", "snooze"), shown.single().actions)
        alarms.stop("t")
        assertEquals(emptyList(), alarms.active.value)
    }

    @Test
    fun lateReminderSaysMissed() = runTest {
        val alarms = alarms()
        advanceTimeBy(10 * 60_000)
        alarms.notify(reminder("m", at = 0))
        runCurrent()
        assertTrue(shown.single().body.contains("missed", ignoreCase = true), shown.single().body)
        assertEquals(listOf("open"), shown.single().actions)
    }
}
