package ru.itmo.aiex.dialog.client

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import ru.itmo.aiex.care.service.ConsultationQuery
import ru.itmo.aiex.remote.CareClient
import java.util.UUID

@Component
@Profile("microservice")
class RemoteConsultationQuery(private val care: CareClient) : ConsultationQuery {
    override fun isConversationSharedWith(conversationId: UUID, specialistUserId: UUID): Boolean =
        care.isConversationSharedWith(conversationId, specialistUserId)

    override fun hasActiveSession(userId: UUID, specialistId: UUID): Boolean = care.hasActiveSession(userId, specialistId)
}
