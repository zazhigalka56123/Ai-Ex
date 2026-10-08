package ru.itmo.aiex.care.service

import java.util.UUID

interface ConsultationQuery {
    fun isConversationSharedWith(conversationId: UUID, specialistUserId: UUID): Boolean

    fun hasActiveSession(userId: UUID, specialistId: UUID): Boolean
}
