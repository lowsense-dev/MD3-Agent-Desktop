package dev.huecoder.md3agent.tools.reminders

import dev.huecoder.md3agent.tools.ToolArgumentException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek

class AlarmDaysTest {
    @Test
    fun `reads full names and short prefixes`() {
        assertEquals(DayOfWeek.MONDAY, SetAlarmTool.parseDay("monday"))
        assertEquals(DayOfWeek.TUESDAY, SetAlarmTool.parseDay(" Tu "))
        assertEquals(DayOfWeek.SUNDAY, SetAlarmTool.parseDay("SUN"))
    }

    @Test
    fun `rejects empty, one letter and unknown days`() {
        listOf("", " ", "S", "T", "xyz").forEach { day ->
            assertThrows(day, ToolArgumentException::class.java) { SetAlarmTool.parseDay(day) }
        }
    }
}
