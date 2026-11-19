package ru.itmo.aiex.integration

import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import ru.itmo.aiex.common.events.ChatImportFailed
import ru.itmo.aiex.common.events.ChatImportParsed
import ru.itmo.aiex.common.events.ConsultationRequested
import ru.itmo.aiex.common.events.ConsultationStatusChanged
import ru.itmo.aiex.common.events.ModerationFlagResolved
import ru.itmo.aiex.common.events.PersonaArchived
import ru.itmo.aiex.common.events.PersonaProfileActivated
import ru.itmo.aiex.notification.entity.NotificationType
import ru.itmo.aiex.notification.service.NotificationCommand
import ru.itmo.aiex.notification.service.NotificationPort
import java.util.UUID

@Component
class NotificationEventListeners(private val notifications: NotificationPort) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    fun onChatImportParsed(event: ChatImportParsed) {
        notifyRecipient(
            event.ownerId,
            NotificationType.IMPORT_PARSED,
            "importId" to event.importId,
            "personaId" to event.personaId,
            "messageCount" to event.messageCount,
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    fun onChatImportFailed(event: ChatImportFailed) {
        notifyRecipient(
            event.ownerId,
            NotificationType.IMPORT_FAILED,
            "importId" to event.importId,
            "personaId" to event.personaId,
            "errorCode" to event.errorCode,
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    fun onPersonaProfileActivated(event: PersonaProfileActivated) {
        notifyRecipient(
            event.ownerId,
            NotificationType.PERSONA_READY,
            "personaId" to event.personaId,
            "profileId" to event.profileId,
            "versionNo" to event.versionNo,
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    fun onPersonaArchived(event: PersonaArchived) {
        notifyRecipient(event.ownerId, NotificationType.PERSONA_ARCHIVED, "personaId" to event.personaId, "byAdmin" to event.byAdmin)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    fun onConsultationRequested(event: ConsultationRequested) {
        notifyRecipient(
            event.specialistUserId,
            NotificationType.CONSULTATION_REQUESTED,
            "sessionId" to event.sessionId,
            "clientId" to event.clientId,
            "startsAt" to event.startsAt.toString(),
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    fun onConsultationStatusChanged(event: ConsultationStatusChanged) {
        notifyRecipient(event.clientId, NotificationType.CONSULTATION_STATUS_CHANGED, "sessionId" to event.sessionId, "status" to event.status)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    fun onModerationFlagResolved(event: ModerationFlagResolved) {
        val reporter = event.reporterId ?: return
        notifyRecipient(reporter, NotificationType.FLAG_RESOLVED, "flagId" to event.flagId, "messageId" to event.messageId, "status" to event.status)
    }

    private fun notifyRecipient(recipientId: UUID, type: NotificationType, vararg payload: Pair<String, Any?>) {
        notifications.sendNotification(NotificationCommand(recipientId, type, mapOf(*payload)))
    }
}
