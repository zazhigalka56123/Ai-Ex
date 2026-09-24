package ru.itmo.aiex.dialog.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionalEventListener
import ru.itmo.aiex.common.events.ModerationFlagRaised
import ru.itmo.aiex.common.events.PersonaArchived
import ru.itmo.aiex.dialog.domain.port.ConversationRepository
import ru.itmo.aiex.dialog.domain.port.MessageRepository

@Component
class DialogEventListeners(private val conversations: ConversationRepository, private val messages: MessageRepository) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: ModerationFlagRaised) {
        val flagged = messages.markFlagged(event.messageId)
        log.info("Флаг {} -> сообщение {} помечено: {}", event.flagId, event.messageId, flagged > 0)
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: PersonaArchived) {
        val archived = conversations.archiveAllByPersona(event.personaId)
        log.info("Персона {} архивирована -> бесед переведено в архив: {}", event.personaId, archived)
    }
}
