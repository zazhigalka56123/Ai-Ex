package ru.itmo.aiex.care.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class BookConsultationRequest(
    val slotId: UUID,
    @field:Schema(description = "Своя беседа, которую клиент открывает специалисту на время активной консультации")
    val sharedConversationId: UUID? = null,
)
