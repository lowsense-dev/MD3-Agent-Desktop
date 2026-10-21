package dev.huecoder.md3agent.data.reminders

import dev.huecoder.md3agent.data.db.ReminderDao
import dev.huecoder.md3agent.data.db.ReminderEntity
import dev.huecoder.md3agent.system.reminders.ReminderAlarms
import dev.huecoder.md3agent.system.reminders.DesktopClock
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class ReminderRepository(
    private val dao: ReminderDao,
    private val alarms: ReminderAlarms,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val canScheduleExact: Boolean get() = alarms.canScheduleExact

    val canNotify: Boolean get() = alarms.canNotify

    fun observeAll(): Flow<List<ReminderEntity>> = dao.observeAll()

    suspend fun upcoming(): List<ReminderEntity> = dao.listAll()

    suspend fun create(text: String, triggerAt: Long, chatId: String?, sound: Boolean = false, repeatDays: Set<DayOfWeek> = emptySet()): ReminderEntity {
        val now = clock()
        require(text.isNotBlank()) { "Reminder text is empty" }
        require(text.length <= MAX_TEXT) { "Reminder text is longer than $MAX_TEXT characters" }
        require(triggerAt > now) { "The reminder time is in the past" }
        require(triggerAt - now <= MAX_AHEAD_MS) { "Reminders can be set at most a year ahead" }
        val reminder = ReminderEntity(
            id = UUID.randomUUID().toString().take(ID_LENGTH),
            text = text.trim(),
            triggerAt = triggerAt,
            chatId = chatId,
            createdAt = now,
            sound = sound,
            repeatDays = repeatDays.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(",") { it.name },
        )
        dao.insert(reminder)
        alarms.schedule(reminder)
        return reminder
    }

    suspend fun cancel(id: String): Boolean {
        alarms.cancel(id)
        return dao.delete(id) > 0
    }

    suspend fun fire(id: String) {
        val reminder = dao.get(id) ?: return
        // Both the in-app timer and the systemd timer deliver each reminder; whichever comes second finds it not yet due.
        if (reminder.triggerAt > clock() + EARLY_TOLERANCE_MS) return
        val days = reminder.repeatDays?.split(',')?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }?.toSet().orEmpty()
        if (days.isEmpty()) {
            if (dao.delete(id) > 0) alarms.notify(reminder)
            return
        }
        // A repeating alarm moves on to its next day instead of disappearing.
        val time = Instant.ofEpochMilli(reminder.triggerAt).atZone(zone).toLocalTime()
        val next = DesktopClock.nextOccurrence(maxOf(clock(), reminder.triggerAt), time, days, zone)
        if (dao.reschedule(id, next) > 0) {
            alarms.notify(reminder)
            alarms.schedule(reminder.copy(triggerAt = next))
        }
    }

    suspend fun restore() {
        val now = clock()
        dao.listAll().forEach { reminder ->
            if (reminder.triggerAt <= now) fire(reminder.id) else alarms.schedule(reminder)
        }
    }

    companion object {
        const val MAX_TEXT = 500
        const val MISSED_AFTER_MS = 60_000L
        private const val EARLY_TOLERANCE_MS = 1_000L
        private const val ID_LENGTH = 8
        private const val MAX_AHEAD_MS = 366L * 24 * 60 * 60 * 1000
    }
}
