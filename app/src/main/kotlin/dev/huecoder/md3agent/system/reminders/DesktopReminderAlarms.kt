package dev.huecoder.md3agent.system.reminders

import dev.huecoder.md3agent.R
import dev.huecoder.md3agent.data.db.ReminderEntity
import dev.huecoder.md3agent.data.reminders.ReminderRepository
import dev.huecoder.md3agent.platform.Log
import dev.huecoder.md3agent.ui.text.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

data class ActiveAlarm(val reminder: ReminderEntity, val missed: Boolean)

/**
 * Reminders on the desktop: a timer inside the running app, plus a systemd user timer (when available) so they fire with
 * the app closed. Plain reminders notify; alarms and timers also stay in [active] until stopped or snoozed.
 */
class DesktopReminderAlarms(
    private val notify: suspend (title: String, body: String, actions: List<Pair<String, String>>, onAction: (String) -> Unit) -> Int?,
    private val timers: SystemdTimers?,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : ReminderAlarms {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val _active = MutableStateFlow<List<ActiveAlarm>>(emptyList())

    /** Called when a reminder is due; the container passes it to ReminderRepository.fire. */
    var onDue: (String) -> Unit = {}

    var onOpenChat: (String?) -> Unit = {}

    /** Snooze asks for a new one-time alarm this far ahead with the same text. */
    var onSnooze: (ReminderEntity) -> Unit = {}

    val active: StateFlow<List<ActiveAlarm>> = _active.asStateFlow()

    override val canScheduleExact: Boolean get() = true

    override val canNotify: Boolean get() = true

    override fun schedule(reminder: ReminderEntity) {
        jobs.remove(reminder.id)?.cancel()
        jobs[reminder.id] = scope.launch {
            delay((reminder.triggerAt - clock()).coerceAtLeast(0))
            jobs.remove(reminder.id)
            onDue(reminder.id)
        }
        timers?.let { scope.launch(Dispatchers.IO) { runCatching { it.install(reminder.id, reminder.triggerAt) }.onFailure { e -> Log.w(TAG, "systemd timer failed: ${e.message}") } } }
    }

    override fun cancel(reminderId: String) {
        jobs.remove(reminderId)?.cancel()
        stop(reminderId)
        timers?.let { scope.launch(Dispatchers.IO) { runCatching { it.remove(reminderId) } } }
    }

    override fun notify(reminder: ReminderEntity) {
        val missed = reminder.triggerAt < clock() - ReminderRepository.MISSED_AFTER_MS
        if (reminder.sound) _active.update { list -> list.filterNot { it.reminder.id == reminder.id } + ActiveAlarm(reminder, missed) }
        val title = Strings.get(if (reminder.sound) R.string.alarm_notification_title else R.string.reminder_notification_title)
        val body = if (missed) reminder.text + "\n" + Strings.get(R.string.reminder_missed, TIME.format(Instant.ofEpochMilli(reminder.triggerAt).atZone(ZoneId.systemDefault()))) else reminder.text
        val actions = if (reminder.sound) {
            listOf(STOP to Strings.get(R.string.reminder_action_stop), SNOOZE to Strings.get(R.string.reminder_action_snooze))
        } else {
            listOf(OPEN to Strings.get(R.string.reminder_action_open))
        }
        scope.launch {
            notify(title, body, actions) { action ->
                when (action) {
                    STOP -> stop(reminder.id)
                    SNOOZE -> snooze(reminder.id)
                    else -> onOpenChat(reminder.chatId)
                }
            }
        }
    }

    fun stop(reminderId: String) = _active.update { list -> list.filterNot { it.reminder.id == reminderId } }

    fun snooze(reminderId: String) {
        val alarm = _active.value.firstOrNull { it.reminder.id == reminderId } ?: return
        stop(reminderId)
        onSnooze(alarm.reminder)
    }

    companion object {
        private const val TAG = "Reminders"
        private const val OPEN = "open"
        private const val STOP = "stop"
        private const val SNOOZE = "snooze"
        const val SNOOZE_MS = 10 * 60_000L
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
