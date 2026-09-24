package ru.itmo.aiex.ingest.web.dto

import ru.itmo.aiex.ingest.domain.ImportedMessage
import ru.itmo.aiex.ingest.domain.MessageAuthor
import java.time.Instant

data class ImportedMessageResponse(val ordinal: Int, val author: MessageAuthor, val body: String, val sentAt: Instant?)

fun ImportedMessage.toResponse() = ImportedMessageResponse(ordinal, author, body, sentAt)
