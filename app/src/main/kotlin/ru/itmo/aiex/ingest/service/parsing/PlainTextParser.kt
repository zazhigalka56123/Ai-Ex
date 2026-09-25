package ru.itmo.aiex.ingest.service.parsing

import org.springframework.stereotype.Component
import ru.itmo.aiex.ingest.entity.ImportErrorCode
import ru.itmo.aiex.ingest.entity.ImportSource

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
