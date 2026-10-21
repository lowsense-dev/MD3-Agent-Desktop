package dev.huecoder.md3agent.system.reminders

import dev.huecoder.md3agent.data.db.ReminderEntity

/** What the reminder repository needs from the platform: wake up at a time, and tell the user. */
interface ReminderAlarms {
    val canScheduleExact: Boolean
    val canNotify: Boolean

    fun schedule(reminder: ReminderEntity)

    fun cancel(reminderId: String)

    fun notify(reminder: ReminderEntity)
}
