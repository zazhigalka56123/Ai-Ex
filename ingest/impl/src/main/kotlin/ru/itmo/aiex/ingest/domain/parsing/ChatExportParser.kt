package ru.itmo.aiex.ingest.domain.parsing

import ru.itmo.aiex.ingest.domain.ImportSource

interface ChatExportParser {
    val source: ImportSource

    fun parse(content: ByteArray): ParsedChat
}
