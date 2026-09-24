package ru.itmo.aiex.notification.application

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionalEventListener
import ru.itmo.aiex.common.events.ChatImportFailed
import ru.itmo.aiex.common.events.ChatImportParsed
import ru.itmo.aiex.common.events.ConsultationRequested
import ru.itmo.aiex.common.events.ConsultationStatusChanged
import ru.itmo.aiex.common.events.ModerationFlagResolved
import ru.itmo.aiex.common.events.PersonaArchived
import ru.itmo.aiex.common.events.PersonaProfileActivated
import ru.itmo.aiex.notification.domain.NotificationType
import java.util.UUID

@Component
class NotificationEventListeners(private val notifications: NotificationPort) {
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: ChatImportParsed) {
        send(
            event.ownerId,
            NotificationType.IMPORT_PARSED,
            "importId" to event.importId,
            "personaId" to event.personaId,
            "messageCount" to event.messageCount,
        )
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: ChatImportFailed) {
        send(
            event.ownerId,
            NotificationType.IMPORT_FAILED,
            "importId" to event.importId,
            "personaId" to event.personaId,
            "errorCode" to event.errorCode,
        )
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: PersonaProfileActivated) {
        send(
            event.ownerId,
            NotificationType.PERSONA_READY,
            "personaId" to event.personaId,
            "profileId" to event.profileId,
            "versionNo" to event.versionNo,
        )
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: PersonaArchived) {
        send(event.ownerId, NotificationType.PERSONA_ARCHIVED, "personaId" to event.personaId, "byAdmin" to event.byAdmin)
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: ConsultationRequested) {
        send(
            event.specialistUserId,
            NotificationType.CONSULTATION_REQUESTED,
            "sessionId" to event.sessionId,
            "clientId" to event.clientId,
            "startsAt" to event.startsAt.toString(),
        )
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: ConsultationStatusChanged) {
        send(event.clientId, NotificationType.CONSULTATION_STATUS_CHANGED, "sessionId" to event.sessionId, "status" to event.status)
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: ModerationFlagResolved) {
        val reporter = event.reporterId ?: return
        send(reporter, NotificationType.FLAG_RESOLVED, "flagId" to event.flagId, "messageId" to event.messageId, "status" to event.status)
    }

    private fun send(recipientId: UUID, type: NotificationType, vararg payload: Pair<String, Any?>) {
        notifications.send(NotificationCommand(recipientId, type, mapOf(*payload)))
    }
}
