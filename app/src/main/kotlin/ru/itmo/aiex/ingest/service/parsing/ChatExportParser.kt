package ru.itmo.aiex.ingest.service.parsing

import ru.itmo.aiex.ingest.entity.ImportSource
interface ChatExportParser {
    val source: ImportSource

    fun parse(content: ByteArray): ParsedChat
}
