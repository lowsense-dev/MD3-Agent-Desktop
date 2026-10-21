package dev.huecoder.md3agent.system.reminders

import dev.huecoder.md3agent.data.db.AppDatabase
import dev.huecoder.md3agent.data.db.ReminderEntity
import dev.huecoder.md3agent.data.reminders.ReminderRepository
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemindersTest {
    private val db = AppDatabase.build(createTempDirectory("md3-rem").toFile().resolve("db").path)
    private var now = 1_000_000L
    private val shown = mutableListOf<Pair<ReminderEntity, Boolean>>()

    private val alarms = object : ReminderAlarms {
        val scheduled = mutableMapOf<String, Long>()
        override val canScheduleExact = true
        override val canNotify = true
        override fun schedule(reminder: ReminderEntity) { scheduled[reminder.id] = reminder.triggerAt }
        override fun cancel(reminderId: String) { scheduled.remove(reminderId) }
        override fun notify(reminder: ReminderEntity) { shown += reminder to (reminder.triggerAt < now - ReminderRepository.MISSED_AFTER_MS) }
    }
    private val repository = ReminderRepository(db.reminderDao(), alarms) { now }

    @Test
    fun missedReminderFiresOnceOnStart() = runBlocking {
        val reminder = repository.create("Call mom", triggerAt = now + 60_000, chatId = null)
        now += 3_600_000
        repository.restore()
        repository.restore()
        repository.fire(reminder.id)
        assertEquals(1, shown.size)
        assertTrue(shown.single().second, "late reminder is shown as missed")
    }

    @Test
    fun fireRemovesOneShotReminder() = runBlocking {
        val reminder = repository.create("Tea", triggerAt = now + 1_000, chatId = "c1")
        now += 1_000
        repository.fire(reminder.id)
        assertNull(db.reminderDao().get(reminder.id))
        assertEquals(listOf("Tea"), shown.map { it.first.text })
    }

    @Test
    fun repeatingAlarmIsRescheduledToTheNextDay() = runBlocking {
        val zone = ZoneId.systemDefault()
        // Monday 07:00, repeating on Monday and Wednesday.
        now = LocalDateTime.of(2026, 10, 19, 6, 0).atZone(zone).toInstant().toEpochMilli()
        val clock = DesktopClock(repository, zone) { now }
        clock.setAlarm(7, 0, "Wake up", setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val reminder = db.reminderDao().listAll().single()
        assertTrue(reminder.sound)
        assertEquals(LocalDateTime.of(2026, 10, 19, 7, 0).atZone(zone).toInstant().toEpochMilli(), reminder.triggerAt)
        now = reminder.triggerAt
        repository.fire(reminder.id)
        val next = db.reminderDao().get(reminder.id)!!
        assertEquals(LocalDateTime.of(2026, 10, 21, 7, 0).atZone(zone).toInstant().toEpochMilli(), next.triggerAt)
        assertEquals(next.triggerAt, alarms.scheduled[reminder.id])
    }

    @Test
    fun oneTimeAlarmForAPassedTimeGoesToTomorrow() = runBlocking {
        val zone = ZoneId.systemDefault()
        now = LocalDateTime.of(2026, 10, 19, 8, 0).atZone(zone).toInstant().toEpochMilli()
        DesktopClock(repository, zone) { now }.setAlarm(7, 30, null, emptySet())
        assertEquals(LocalDateTime.of(2026, 10, 20, 7, 30).atZone(zone).toInstant().toEpochMilli(), db.reminderDao().listAll().single().triggerAt)
    }

    @Test
    fun timerIsASoundReminderSecondsAhead() = runBlocking {
        DesktopClock(repository) { now }.setTimer(90, "Pasta")
        val reminder = db.reminderDao().listAll().single()
        assertEquals(now + 90_000, reminder.triggerAt)
        assertTrue(reminder.sound)
        assertEquals("Pasta", reminder.text)
    }

    @Test
    fun aSecondSignalForTheSameRepeatingAlarmDoesNotFireItEarly() = runBlocking {
        val zone = ZoneId.systemDefault()
        now = LocalDateTime.of(2026, 10, 19, 6, 0).atZone(zone).toInstant().toEpochMilli()
        DesktopClock(repository, zone) { now }.setAlarm(7, 0, null, setOf(DayOfWeek.MONDAY))
        val reminder = db.reminderDao().listAll().single()
        now = reminder.triggerAt
        repository.fire(reminder.id)
        // The in-app timer fired first; the systemd timer's launch arrives a moment later.
        now += 2_000
        repository.fire(reminder.id)
        assertEquals(1, shown.size)
    }
}
