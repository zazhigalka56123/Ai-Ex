package ru.itmo.aiex.care.service

import java.util.UUID

data class BookConsultationCommand(val slotId: UUID, val sharedConversationId: UUID? = null)
