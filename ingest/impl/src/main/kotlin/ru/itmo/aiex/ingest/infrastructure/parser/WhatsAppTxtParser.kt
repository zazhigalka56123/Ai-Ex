package ru.itmo.aiex.ingest.infrastructure.parser

import org.springframework.stereotype.Component
import ru.itmo.aiex.ingest.domain.ImportErrorCode
import ru.itmo.aiex.ingest.domain.ImportSource
import ru.itmo.aiex.ingest.domain.parsing.ChatExportParser
import ru.itmo.aiex.ingest.domain.parsing.ChatParseException
import ru.itmo.aiex.ingest.domain.parsing.ChatText
import ru.itmo.aiex.ingest.domain.parsing.ParsedChat
import ru.itmo.aiex.ingest.domain.parsing.ParsedChatBuilder
import ru.itmo.aiex.ingest.domain.parsing.WhatsAppLine
import java.time.ZoneOffset

@Component
internal class WhatsAppTxtParser : ChatExportParser {
    override val source: ImportSource = ImportSource.WHATSAPP_TXT

    override fun parse(content: ByteArray): ParsedChat {
        val builder = ParsedChatBuilder(source)
        var headers = 0
        ChatText.lines(content).forEach { line ->
            val header = WhatsAppLine.parseHeader(line)
            when {
                header == null -> if (line.isNotBlank()) builder.continuation(line)

                header.author == null -> {
                    headers++
                    builder.system()
                }

                else -> {
                    headers++
                    builder.message(header.author, header.text, header.time.toInstant(ZoneOffset.UTC), header.time.hour)
                }
            }
        }
        if (headers == 0) throw ChatParseException(ImportErrorCode.MALFORMED_FILE, "В файле нет ни одной строки формата выгрузки WhatsApp")
        return builder.build()
    }
}
