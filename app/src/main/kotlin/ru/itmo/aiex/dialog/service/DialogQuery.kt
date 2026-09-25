package ru.itmo.aiex.dialog.service

import ru.itmo.aiex.dialog.dto.MessageView

import ru.itmo.aiex.common.security.Actor
import java.util.UUID

interface DialogQuery {
    fun findMessage(messageId: UUID): MessageView?

    fun findMessageVisibleTo(messageId: UUID, actor: Actor): MessageView?

    fun isConversationOwnedBy(conversationId: UUID, userId: UUID): Boolean
}
