package ru.itmo.aiex.ingest.domain

import java.time.Instant

data class MessageRow(val author: MessageAuthor, val body: String, val sentAt: Instant?, val ordinal: Int) {
    override fun toString(): String = "MessageRow(ordinal=$ordinal, author=$author, chars=${body.length})"
}
