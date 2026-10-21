package dev.huecoder.md3agent.tools.reminders

import dev.huecoder.md3agent.data.settings.ToolSettings
import dev.huecoder.md3agent.llm.ToolSpec
import dev.huecoder.md3agent.system.reminders.DesktopClock
import dev.huecoder.md3agent.system.reminders.ClockResult
import dev.huecoder.md3agent.tools.Tool
import dev.huecoder.md3agent.tools.ToolArgumentException
import dev.huecoder.md3agent.tools.ToolOutcome
import dev.huecoder.md3agent.tools.integerProperty
import dev.huecoder.md3agent.tools.objectSchema
import dev.huecoder.md3agent.tools.optionalArray
import dev.huecoder.md3agent.tools.optionalInt
import dev.huecoder.md3agent.tools.optionalString
import dev.huecoder.md3agent.tools.stringArrayProperty
import dev.huecoder.md3agent.tools.stringProperty
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.DayOfWeek

private fun ClockResult.toOutcome(done: String): ToolOutcome = when (this) {
    ClockResult.DONE -> ToolOutcome(done)
    ClockResult.FAILED -> ToolOutcome.error("Could not schedule it")
}

private fun JsonObject.requireInt(name: String, range: IntRange): Int {
    val value = optionalInt(name) ?: throw ToolArgumentException("An integer parameter '$name' is required")
    if (value !in range) throw ToolArgumentException("$name must be between ${range.first} and ${range.last}")
    return value
}

class SetAlarmTool(private val clock: DesktopClock) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Sets an alarm with sound, shown in a window on top of everything with Stop and Snooze. Use it when the user wants to be woken up " +
            "or wants a loud alarm; for a quiet note at some moment use ${SetReminderTool.NAME} instead. Leave days empty for a one-time alarm.",
        parameters = objectSchema(required = listOf("hour", "minute")) {
            integerProperty("hour", "Hour in 24-hour local time", minimum = 0, maximum = 23)
            integerProperty("minute", "Minute", minimum = 0, maximum = 59)
            stringProperty("label", "Short alarm label")
            stringArrayProperty("days", "Days to repeat on, English weekday names like MONDAY")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val hour = arguments.requireInt("hour", 0..23)
        val minute = arguments.requireInt("minute", 0..59)
        val days = arguments.optionalArray("days").map { parseDay((it as? JsonPrimitive)?.contentOrNull.orEmpty()) }.toSet()
        val result = clock.setAlarm(hour, minute, arguments.optionalString("label"), days)
        val repeat = if (days.isEmpty()) "once" else days.sorted().joinToString { it.name.lowercase() }
        return result.toOutcome("Alarm set for %02d:%02d, %s".format(hour, minute, repeat))
    }

    companion object {
        const val NAME = "set_alarm"
        private const val DAY_PREFIX = 3
        private const val MIN_DAY_CHARS = 2

        fun parseDay(raw: String): DayOfWeek {
            val prefix = raw.trim().uppercase().take(DAY_PREFIX)
            val matches = DayOfWeek.entries.filter { prefix.length >= MIN_DAY_CHARS && it.name.startsWith(prefix) }
            return matches.singleOrNull() ?: throw ToolArgumentException("Unknown or ambiguous weekday \"$raw\", use full names like MONDAY")
        }
    }
}

class SetTimerTool(private val clock: DesktopClock) : Tool {
    override val spec = ToolSpec(
        name = NAME,
        description = "Starts a countdown timer; when it ends a sound plays and a window on top of everything shows it.",
        parameters = objectSchema(required = listOf("seconds")) {
            integerProperty("seconds", "Timer length in seconds", minimum = 1, maximum = MAX_SECONDS)
            stringProperty("label", "Short timer label")
        },
    )

    override suspend fun execute(arguments: JsonObject, settings: ToolSettings): ToolOutcome {
        val seconds = arguments.requireInt("seconds", 1..MAX_SECONDS)
        val result = clock.setTimer(seconds, arguments.optionalString("label"))
        return result.toOutcome("Timer started for $seconds s")
    }

    companion object {
        const val NAME = "set_timer"
        private const val MAX_SECONDS = 86_400
    }
}
