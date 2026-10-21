package dev.huecoder.md3agent.system.reminders

import dev.huecoder.md3agent.data.reminders.ReminderRepository
import java.time.DayOfWeek

enum class ClockResult { DONE, FAILED }

/** Alarms and timers without a system clock app: loud reminders with Stop and Snooze. */
class DesktopClock(private val reminders: ReminderRepository, private val clock: () -> Long = System::currentTimeMillis) {
    suspend fun setAlarm(hour: Int, minute: Int, label: String?, days: Set<DayOfWeek>): ClockResult = TODO()

    suspend fun setTimer(seconds: Int, label: String?): ClockResult = TODO()
}
