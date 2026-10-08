package ru.itmo.aiex.care.client

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.dialog.dto.MessageView
import ru.itmo.aiex.dialog.service.DialogQuery
import ru.itmo.aiex.remote.DialogClient
import java.util.UUID

@Component
@Profile("microservice")
class RemoteDialogQuery(private val dialogs: DialogClient) : DialogQuery {
    override fun findMessage(messageId: UUID): MessageView? = dialogs.findMessage(messageId)

    override fun findMessageVisibleTo(messageId: UUID, actor: Actor): MessageView? = dialogs.findMessageVisibleTo(messageId, actor)

    override fun isConversationOwnedBy(conversationId: UUID, userId: UUID): Boolean = dialogs.isConversationOwnedBy(conversationId, userId)
}
