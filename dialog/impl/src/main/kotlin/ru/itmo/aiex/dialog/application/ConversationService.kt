package ru.itmo.aiex.dialog.application

import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.care.api.ConsultationQuery
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.ForbiddenException
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.id.Ids
import ru.itmo.aiex.common.paging.CursorCodec
import ru.itmo.aiex.common.paging.CursorPage
import ru.itmo.aiex.common.paging.CursorQuery
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.time.nowMicros
import ru.itmo.aiex.dialog.domain.Conversation
import ru.itmo.aiex.dialog.domain.ConversationAccess
import ru.itmo.aiex.dialog.domain.ConversationAccessPolicy
import ru.itmo.aiex.dialog.domain.ConversationStatus
import ru.itmo.aiex.dialog.domain.Message
import ru.itmo.aiex.dialog.domain.MessageScope
import ru.itmo.aiex.dialog.domain.port.ConversationRepository
import ru.itmo.aiex.dialog.domain.port.MessageRepository
import ru.itmo.aiex.persona.api.PersonaAccess
import java.time.Clock
import java.util.UUID

@Service
@Transactional(propagation = Propagation.NEVER)
class ConversationService(
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    @param:Lazy private val personas: PersonaAccess,
    @param:Lazy private val consultations: ConsultationQuery,
    private val transactions: DialogTransactions,
    private val clock: Clock,
) {
    fun create(actor: Actor, command: CreateConversationCommand): Conversation {
        val persona = personas.assertOwned(command.personaId, actor.userId)
        if (!persona.canChat) throw personaNotReady(persona)
        val title = command.title?.trim()?.takeIf { it.isNotEmpty() } ?: Conversation.defaultTitle(persona.name)
        val conversation = Conversation(Ids.next(), actor.userId, persona.id, title, clock.nowMicros())
        return transactions.write { conversations.save(conversation) }
    }

    fun list(actor: Actor, status: ConversationStatus?, personaId: UUID?, page: PageQuery): PageView<Conversation> =
        transactions.read { conversations.findPage(actor.userId, status, personaId, page) }

    fun get(actor: Actor, conversationId: UUID): Conversation {
        val conversation = find(conversationId)
        if (scopeFor(conversation, actor) != MessageScope.ALL) throw notFound(conversationId)
        return conversation
    }

    fun archive(actor: Actor, conversationId: UUID) {
        transactions.write {
            val conversation = conversations.findById(conversationId)?.takeIf { it.isOwnedBy(actor.userId) } ?: throw notFound(conversationId)
            if (conversation.archive()) conversations.save(conversation)
        }
    }

    fun listMessages(actor: Actor, conversationId: UUID, query: CursorQuery): CursorPage<Message> {
        val scope = scopeFor(find(conversationId), actor)
        val before = query.cursor?.let(CursorCodec::decodeTimeId)
        val rows = transactions.read { messages.findSlice(conversationId, before, scope, query.limit + 1) }
        return CursorPage.fromOverfetch(rows, query.limit) { CursorCodec.encode(it.position) }
    }

    fun getMessage(actor: Actor, conversationId: UUID, messageId: UUID): Message {
        val scope = scopeFor(find(conversationId), actor)
        return transactions
            .read { messages.findById(messageId) }
            ?.takeIf { it.conversationId == conversationId && (scope == MessageScope.ALL || it.flagged) }
            ?: throw NotFoundException.of(ErrorCode.MESSAGE_NOT_FOUND, messageId)
    }

    private fun find(conversationId: UUID): Conversation =
        transactions.read { conversations.findById(conversationId) } ?: throw notFound(conversationId)

    private fun scopeFor(conversation: Conversation, actor: Actor): MessageScope = when (
        val access = ConversationAccessPolicy.decide(conversation.userId, actor) {
            consultations.isConversationSharedWith(conversation.id, actor.userId)
        }
    ) {
        is ConversationAccess.Granted -> access.scope
        ConversationAccess.Forbidden -> throw ForbiddenException("Беседа ${conversation.id} не расшарена вам в активной консультации")
        ConversationAccess.Hidden -> throw notFound(conversation.id)
    }

    private fun notFound(conversationId: UUID) = NotFoundException.of(ErrorCode.CONVERSATION_NOT_FOUND, conversationId)
}
