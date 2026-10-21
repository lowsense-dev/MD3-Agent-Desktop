package dev.huecoder.md3agent.feature.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object RelativeTime {
    private const val DATE_SKELETON = "d MMM"

    fun format(epochMillis: Long, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String {
        val moment = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val formatter = if (moment.toLocalDate() == LocalDate.now(zone)) {
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        } else {
            DateTimeFormatter.ofPattern(if (locale.language == "en") "MMM d" else DATE_SKELETON)
        }
        return moment.format(formatter.withLocale(locale))
    }
}
