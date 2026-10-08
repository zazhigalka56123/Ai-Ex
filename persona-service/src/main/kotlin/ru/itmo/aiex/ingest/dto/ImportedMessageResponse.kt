package ru.itmo.aiex.ingest.dto

import ru.itmo.aiex.ingest.entity.ImportedMessage
import ru.itmo.aiex.ingest.entity.MessageAuthor
import java.time.Instant

data class ImportedMessageResponse(val ordinal: Int, val author: MessageAuthor, val body: String, val sentAt: Instant?)

fun ImportedMessage.toResponse() = ImportedMessageResponse(ordinal, author, body, sentAt)
