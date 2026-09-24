package ru.itmo.aiex.ingest.domain.parsing

import java.time.Instant

data class ParsedMessage(val author: String, val text: String, val sentAt: Instant?, val localHour: Int?) {
    override fun toString(): String = "ParsedMessage(author=$author, chars=${text.length}, sentAt=$sentAt)"
}
