package ru.itmo.aiex.remote

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener
import ru.itmo.aiex.common.events.ChatImportFailed
import ru.itmo.aiex.common.events.ChatImportParsed
import ru.itmo.aiex.common.events.ConsultationRequested
import ru.itmo.aiex.common.events.ConsultationStatusChanged
import ru.itmo.aiex.common.events.DomainEvent
import ru.itmo.aiex.common.events.MessageAutoFlagged
import ru.itmo.aiex.common.events.ModerationFlagRaised
import ru.itmo.aiex.common.events.ModerationFlagResolved
import ru.itmo.aiex.common.events.PersonaArchived
import ru.itmo.aiex.common.events.PersonaProfileActivated
import java.util.UUID

@Component
@Profile("microservice")
class RemoteEventRelay(private val notifications: NotificationClient, private val dialogs: DialogClient, private val care: CareClient) {
    @TransactionalEventListener(fallbackExecution = true)
    fun on(event: ChatImportParsed) {
        send(
            event,
            event.ownerId,
            "IMPORT_PARSED",
            "importId" to event.importId,
            "personaId" to event.personaId,
            "messageCount" to event.messageCount,
        )
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun on(event: ChatImportFailed) {
        send(event, event.ownerId, "IMPORT_FAILED", "importId" to event.importId, "personaId" to event.personaId, "errorCode" to event.errorCode)
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun on(event: PersonaProfileActivated) {
        send(event, event.ownerId, "PERSONA_READY", "personaId" to event.personaId, "profileId" to event.profileId, "versionNo" to event.versionNo)
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun on(event: PersonaArchived) {
        try {
            dialogs.personaArchived(event)
        } finally {
            send(event, event.ownerId, "PERSONA_ARCHIVED", "personaId" to event.personaId, "byAdmin" to event.byAdmin)
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun on(event: ConsultationRequested) {
        send(
            event,
            event.specialistUserId,
            "CONSULTATION_REQUESTED",
            "sessionId" to event.sessionId,
            "clientId" to event.clientId,
            "startsAt" to event.startsAt.toString(),
        )
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun on(event: ConsultationStatusChanged) {
        send(event, event.clientId, "CONSULTATION_STATUS_CHANGED", "sessionId" to event.sessionId, "status" to event.status)
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun on(event: ModerationFlagResolved) {
        val reporter = event.reporterId ?: return
        send(event, reporter, "FLAG_RESOLVED", "flagId" to event.flagId, "messageId" to event.messageId, "status" to event.status)
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun on(event: MessageAutoFlagged) {
        care.messageAutoFlagged(event)
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun on(event: ModerationFlagRaised) {
        dialogs.moderationRaised(event)
    }

    private fun send(event: DomainEvent, recipientId: UUID, type: String, vararg payload: Pair<String, Any?>) {
        notifications.send(NotificationDelivery(event.eventId, recipientId, type, mapOf(*payload)))
    }
}
