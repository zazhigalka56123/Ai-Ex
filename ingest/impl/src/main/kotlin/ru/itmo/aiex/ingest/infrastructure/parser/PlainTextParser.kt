package ru.itmo.aiex.ingest.infrastructure.parser

import org.springframework.stereotype.Component
import ru.itmo.aiex.ingest.domain.ImportErrorCode
import ru.itmo.aiex.ingest.domain.ImportSource
import ru.itmo.aiex.ingest.domain.parsing.ChatExportParser
import ru.itmo.aiex.ingest.domain.parsing.ChatParseException
import ru.itmo.aiex.ingest.domain.parsing.ChatText
import ru.itmo.aiex.ingest.domain.parsing.ParsedChat
import ru.itmo.aiex.ingest.domain.parsing.ParsedChatBuilder
import ru.itmo.aiex.ingest.domain.parsing.PlainTextLine
import java.time.ZoneOffset

@Component
internal class PlainTextParser : ChatExportParser {
    override val source: ImportSource = ImportSource.PLAIN_TEXT

    override fun parse(content: ByteArray): ParsedChat {
        val builder = ParsedChatBuilder(source)
        ChatText.lines(content).filter { it.isNotBlank() }.forEach { raw ->
            val line = PlainTextLine.parse(raw)
            if (line == null) {
                builder.continuation(raw.trim())
            } else {
                builder.message(line.author, line.text, line.time?.toInstant(ZoneOffset.UTC), line.time?.hour)
            }
        }
        if (!builder.hasMessages) throw ChatParseException(ImportErrorCode.MALFORMED_FILE, "В файле нет ни одной строки вида «Имя: сообщение»")
        return builder.build()
    }
}
