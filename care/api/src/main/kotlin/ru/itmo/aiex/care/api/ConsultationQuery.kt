package ru.itmo.aiex.care.api

import java.util.UUID

interface ConsultationQuery {
    fun isConversationSharedWith(conversationId: UUID, specialistUserId: UUID): Boolean

    fun hasActiveSession(userId: UUID, specialistId: UUID): Boolean
}
