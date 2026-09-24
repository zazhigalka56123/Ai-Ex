package ru.itmo.aiex.dialog.application

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.agent.api.GenerateReplyCommand
import ru.itmo.aiex.agent.api.GeneratedReply
import ru.itmo.aiex.agent.api.GuardrailTarget
import ru.itmo.aiex.agent.api.HistoryMessage
import ru.itmo.aiex.agent.api.ReplyGenerator
import ru.itmo.aiex.agent.api.Speaker
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.events.DomainEventPublisher
import ru.itmo.aiex.common.events.MessageAutoFlagged
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.time.nowMicros
import ru.itmo.aiex.dialog.domain.Conversation
import ru.itmo.aiex.dialog.domain.Message
import ru.itmo.aiex.dialog.domain.MessageScope
import ru.itmo.aiex.dialog.domain.MessageSender
import ru.itmo.aiex.dialog.domain.port.ConversationRepository
import ru.itmo.aiex.dialog.domain.port.MessageRepository
import ru.itmo.aiex.persona.api.PersonaAccess
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class MessageExchangeService(
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    @param:Lazy private val personas: PersonaAccess,
    private val replyGenerator: ReplyGenerator,
    private val events: DomainEventPublisher,
    private val transactions: DialogTransactions,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(propagation = Propagation.NEVER)
    fun send(actor: Actor, conversationId: UUID, text: String): MessageExchange {
        val conversation = transactions.read { requireWritable(conversationId, actor) }
        val persona = personas.assertOwned(conversation.personaId, actor.userId)
        if (!persona.canChat) throw personaNotReady(persona)

        val accepted = transactions.write { acceptUserMessage(conversationId, actor, text) }
        val reply =
            replyGenerator.generate(
                GenerateReplyCommand(conversationId = conversationId, personaId = conversation.personaId, history = accepted.history),
            )
        return transactions.write { storeReply(conversationId, accepted.userMessageId, reply) }
    }

    private class AcceptedMessage(val userMessageId: UUID, val history: List<HistoryMessage>)

    private fun acceptUserMessage(conversationId: UUID, actor: Actor, text: String): AcceptedMessage {
        val conversation = requireWritable(conversationId, actor)
        val now = clock.nowMicros()
        val userMessage = messages.insert(Message.fromUser(conversation, text, now))
        conversations.bumpCounters(conversationId, 1, now)

        val earlier = messages.findSlice(conversationId, userMessage.position, MessageScope.ALL, replyGenerator.historyWindow - 1)
        val history = (earlier.asReversed() + userMessage).map { it.toHistoryMessage() }
        return AcceptedMessage(userMessage.id, history)
    }

    private fun storeReply(conversationId: UUID, userMessageId: UUID, generated: GeneratedReply): MessageExchange {
        val conversation = checkNotNull(conversations.findById(conversationId)) { "Беседа $conversationId исчезла во время генерации ответа" }
        val userMessage = checkNotNull(messages.findById(userMessageId)) { "Сообщение $userMessageId исчезло во время генерации ответа" }

        val repliedAt = maxOf(clock.nowMicros(), userMessage.createdAt.plus(1, ChronoUnit.MICROS))
        val reply = Message.fromPersona(conversation, generated.text.take(Message.MAX_BODY_LENGTH), repliedAt, generated.agentRunId)

        generated.guardrailHits.forEach { hit ->
            val target = if (hit.target == GuardrailTarget.USER_MESSAGE) userMessage else reply
            target.flag()
            events.publish(MessageAutoFlagged(target.id, conversationId, hit.reason, hit.details, repliedAt))
            log.info("Сообщение {} беседы {} помечено guardrails: {}", target.id, conversationId, hit.reason)
        }
        messages.insert(reply)
        conversations.bumpCounters(conversationId, 1, repliedAt)
        return MessageExchange(userMessage, reply)
    }

    private fun requireWritable(conversationId: UUID, actor: Actor): Conversation {
        val conversation =
            conversations.findById(conversationId)?.takeIf { it.isOwnedBy(actor.userId) }
                ?: throw NotFoundException.of(ErrorCode.CONVERSATION_NOT_FOUND, conversationId)
        conversation.ensureAcceptsMessages()
        return conversation
    }

    private fun Message.toHistoryMessage() = HistoryMessage(if (sender == MessageSender.USER) Speaker.USER else Speaker.PERSONA, body, createdAt)
}
