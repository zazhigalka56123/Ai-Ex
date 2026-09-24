package ru.itmo.aiex.dialog.domain.port

import ru.itmo.aiex.common.paging.TimeIdPosition
import ru.itmo.aiex.dialog.domain.Message
import ru.itmo.aiex.dialog.domain.MessageScope
import java.util.UUID

interface MessageRepository {
    fun insert(message: Message): Message

    fun findById(id: UUID): Message?

    fun findWithConversation(id: UUID): Message?

    fun findSlice(conversationId: UUID, before: TimeIdPosition?, scope: MessageScope, limit: Int): List<Message>

    fun markFlagged(id: UUID): Int

    fun count(): Long

    fun countFlagged(): Long
}
