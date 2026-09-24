package ru.itmo.aiex.ingest.domain.parsing

import java.time.DateTimeException
import java.time.LocalDateTime

object PlainTextLine {
    data class Line(val time: LocalDateTime?, val author: String, val text: String)

    private val LINE =
        Regex("""^(?:\[(\d{4})-(\d{2})-(\d{2})[ T](\d{1,2}):(\d{2})(?::(\d{2}))?]\s*)?([^:\[\]]{1,64}?):\s+(.*)$""")

    @Suppress("MagicNumber")
    fun parse(line: String): Line? {
        val g = LINE.matchEntire(line.trim())?.groupValues ?: return null
        val author = g[7].trim().takeIf { it.isNotEmpty() } ?: return null
        val time =
            if (g[1].isEmpty()) {
                null
            } else {
                try {
                    LocalDateTime.of(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt())
                } catch (_: DateTimeException) {
                    return null
                }
            }
        return Line(time, author, g[8])
    }
}
