package ru.itmo.aiex.ingest.domain.parsing

import ru.itmo.aiex.ingest.domain.ImportSource

data class ParsedChat(val source: ImportSource, val chatName: String?, val messages: List<ParsedMessage>, val skipped: Int, val attachments: Int) {
    val authors: List<String> get() = messages.map { it.author }.distinct()
}
