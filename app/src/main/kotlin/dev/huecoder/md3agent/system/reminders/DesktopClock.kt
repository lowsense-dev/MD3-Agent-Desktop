package dev.huecoder.md3agent.system.reminders

import dev.huecoder.md3agent.data.reminders.ReminderRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class ClockResult { DONE, FAILED }

/** Alarms and timers without a system clock app: loud reminders with Stop and Snooze. */
class DesktopClock(
    private val reminders: ReminderRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun setAlarm(hour: Int, minute: Int, label: String?, days: Set<DayOfWeek>): ClockResult {
        val at = nextOccurrence(clock(), LocalTime.of(hour, minute), days, zone)
        reminders.create(label?.takeIf { it.isNotBlank() } ?: ALARM_TEXT, at, chatId = null, sound = true, repeatDays = days)
        return ClockResult.DONE
    }

    suspend fun setTimer(seconds: Int, label: String?): ClockResult {
        reminders.create(label?.takeIf { it.isNotBlank() } ?: TIMER_TEXT, clock() + seconds * MS_PER_SECOND, chatId = null, sound = true)
        return ClockResult.DONE
    }

    companion object {
        private const val ALARM_TEXT = "Alarm"
        private const val TIMER_TEXT = "Timer"
        private const val MS_PER_SECOND = 1_000L

        /** The first moment after [now] at [time] on one of [days] (any day when empty). */
        fun nextOccurrence(now: Long, time: LocalTime, days: Set<DayOfWeek>, zone: ZoneId): Long {
            val start = Instant.ofEpochMilli(now).atZone(zone)
            var candidate: ZonedDateTime = start.with(time).withSecond(0).withNano(0)
            repeat(DAYS_TO_SEARCH) {
                if (candidate.toInstant().toEpochMilli() > now && (days.isEmpty() || candidate.dayOfWeek in days)) return candidate.toInstant().toEpochMilli()
                candidate = candidate.plusDays(1).with(time)
            }
            error("No matching day for $days")
        }

        private const val DAYS_TO_SEARCH = 8
    }
}
