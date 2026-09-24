package ru.itmo.aiex.care.application

import java.util.UUID

data class BookConsultationCommand(val slotId: UUID, val sharedConversationId: UUID? = null)
