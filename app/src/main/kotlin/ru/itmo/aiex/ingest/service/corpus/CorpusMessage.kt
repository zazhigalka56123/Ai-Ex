package ru.itmo.aiex.ingest.service.corpus

import ru.itmo.aiex.ingest.entity.MessageAuthor
import java.time.Instant

data class CorpusMessage(val author: MessageAuthor, val body: String, val sentAt: Instant?, val localHour: Int?) {
    override fun toString(): String = "CorpusMessage(author=$author, chars=${body.length}, sentAt=$sentAt)"
}
