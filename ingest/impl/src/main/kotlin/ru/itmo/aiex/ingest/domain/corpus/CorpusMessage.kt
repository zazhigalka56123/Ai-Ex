package ru.itmo.aiex.ingest.domain.corpus

import ru.itmo.aiex.ingest.domain.MessageAuthor
import java.time.Instant

data class CorpusMessage(val author: MessageAuthor, val body: String, val sentAt: Instant?, val localHour: Int?) {
    override fun toString(): String = "CorpusMessage(author=$author, chars=${body.length}, sentAt=$sentAt)"
}
