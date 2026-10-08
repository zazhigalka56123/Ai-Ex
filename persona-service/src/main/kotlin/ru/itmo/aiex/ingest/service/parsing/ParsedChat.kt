package ru.itmo.aiex.ingest.service.parsing

import ru.itmo.aiex.ingest.entity.ImportSource
data class ParsedChat(val source: ImportSource, val chatName: String?, val messages: List<ParsedMessage>, val skipped: Int, val attachments: Int) {
    val authors: List<String> get() = messages.map { it.author }.distinct()
}
