package ru.itmo.aiex.ingest.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionalEventListener
import ru.itmo.aiex.common.events.PersonaArchived
import ru.itmo.aiex.ingest.domain.port.ImportedMessageRepository

@Component
class PersonaArchivedListener(private val messages: ImportedMessageRepository) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: PersonaArchived) {
        val deleted = messages.deleteByPersona(event.personaId)
        log.info("Персона {} архивирована: удалено сообщений импортов {}", event.personaId, deleted)
    }
}
