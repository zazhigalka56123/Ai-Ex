package ru.itmo.aiex.care.application

import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import ru.itmo.aiex.care.domain.ConsultationSession
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.dialog.api.DialogQuery

@Component
class ConsultationBooking(private val consultations: ConsultationService, @param:Lazy private val dialogs: DialogQuery) {
    fun book(actor: Actor, command: BookConsultationCommand): ConsultationSession {
        actor.requireRole(RoleCode.USER)
        command.sharedConversationId?.let { conversationId ->
            if (!dialogs.isConversationOwnedBy(conversationId, actor.userId)) {
                throw NotFoundException.of(ErrorCode.CONVERSATION_NOT_FOUND, conversationId)
            }
        }
        return consultations.book(actor, command)
    }
}
