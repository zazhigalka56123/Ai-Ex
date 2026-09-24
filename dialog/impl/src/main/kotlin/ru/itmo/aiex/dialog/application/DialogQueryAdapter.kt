package ru.itmo.aiex.dialog.application

import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import ru.itmo.aiex.care.api.ConsultationQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.dialog.api.DialogQuery
import ru.itmo.aiex.dialog.api.MessageView
import ru.itmo.aiex.dialog.api.SenderKind
import ru.itmo.aiex.dialog.domain.ConversationAccess
import ru.itmo.aiex.dialog.domain.ConversationAccessPolicy
import ru.itmo.aiex.dialog.domain.Message
import ru.itmo.aiex.dialog.domain.MessageScope
import ru.itmo.aiex.dialog.domain.MessageSender
import ru.itmo.aiex.dialog.domain.port.ConversationRepository
import ru.itmo.aiex.dialog.domain.port.MessageRepository
import java.util.UUID

@Component
class DialogQueryAdapter(
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    @param:Lazy private val consultations: ConsultationQuery,
    private val transactions: DialogTransactions,
) : DialogQuery {
    override fun findMessage(messageId: UUID): MessageView? = transactions.read { messages.findWithConversation(messageId)?.toView() }

    override fun findMessageVisibleTo(messageId: UUID, actor: Actor): MessageView? {
        val message = findMessage(messageId) ?: return null
        val access =
            ConversationAccessPolicy.decide(message.ownerId, actor) { consultations.isConversationSharedWith(message.conversationId, actor.userId) }
        return message.takeIf { access is ConversationAccess.Granted && (access.scope == MessageScope.ALL || message.flagged) }
    }

    override fun isConversationOwnedBy(conversationId: UUID, userId: UUID): Boolean =
        transactions.read { conversations.existsByIdAndUserId(conversationId, userId) }

    private fun Message.toView() = MessageView(
        id = id,
        conversationId = conversation.id,
        personaId = conversation.personaId,
        ownerId = conversation.userId,
        sender = if (sender == MessageSender.USER) SenderKind.USER else SenderKind.PERSONA,
        body = body,
        createdAt = createdAt,
        flagged = flagged,
    )
}
