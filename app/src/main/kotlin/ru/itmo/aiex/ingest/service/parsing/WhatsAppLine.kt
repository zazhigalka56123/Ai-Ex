package ru.itmo.aiex.ingest.service.parsing

import java.time.DateTimeException
import java.time.LocalDateTime

object WhatsAppLine {
    data class Header(val time: LocalDateTime, val author: String?, val text: String)

    private const val DATE_TIME = """(\d{1,2})([./])(\d{1,2})\2(\d{2,4}),?\s(\d{1,2}):(\d{2})(?::(\d{2}))?(?:[\s ]?([AaPp])\.?[Mm]\.?)?"""
    private val ANDROID = Regex("""^$DATE_TIME\s[-–]\s(.*)$""")
    private val IOS = Regex("""^\[$DATE_TIME]\s(.*)$""")

    private const val TWO_DIGIT_YEAR_BASE = 2000
    private const val TWO_DIGIT_YEAR_MAX = 99
    private const val HALF_DAY = 12
    private const val AUTHOR_MAX = 128
    private const val GROUP_REST = 9

    fun parseHeader(line: String): Header? {
        val match = ANDROID.matchEntire(line) ?: IOS.matchEntire(line) ?: return null
        val g = match.groupValues
        val time = toTime(g) ?: return null
        val rest = g[GROUP_REST]
        val separator = rest.indexOf(": ")
        val author = if (separator in 1..AUTHOR_MAX) rest.substring(0, separator).trim().takeIf { it.isNotEmpty() } else null
        return if (author == null) Header(time, null, rest) else Header(time, author, rest.substring(separator + 2))
    }

    @Suppress("MagicNumber")
    private fun toTime(g: List<String>): LocalDateTime? {
        val first = g[1].toInt()
        val second = g[3].toInt()
        val (day, month) = if (g[2] == ".") first to second else second to first
        val rawYear = g[4].toInt()
        val year = if (rawYear <= TWO_DIGIT_YEAR_MAX) TWO_DIGIT_YEAR_BASE + rawYear else rawYear
        var hour = g[5].toInt()
        when (g[8].lowercase()) {
            "a" -> if (hour == HALF_DAY) hour = 0
            "p" -> if (hour != HALF_DAY) hour += HALF_DAY
        }
        val seconds = g[7].ifEmpty { "0" }.toInt()
        return try {
            LocalDateTime.of(year, month, day, hour, g[6].toInt(), seconds)
        } catch (_: DateTimeException) {
            null
        }
    }
}
