package ru.itmo.aiex.care.dto

import ru.itmo.aiex.care.entity.ConsultationSession
import ru.itmo.aiex.care.entity.SessionStatus
import java.time.Instant
import java.util.UUID

data class ConsultationResponse(
    val id: UUID,
    val clientId: UUID,
    val specialistId: UUID,
    val slotId: UUID,
    val startsAt: Instant,
    val durationMin: Int,
    val status: SessionStatus,
    val sharedConversationId: UUID?,
    val rating: Int?,
    val summary: String?,
    val recommendations: String?,
    val cancelReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun ConsultationSession.toResponse() = ConsultationResponse(
    id = id,
    clientId = userId,
    specialistId = specialist.id,
    slotId = slot.id,
    startsAt = startsAt,
    durationMin = durationMin,
    status = status,
    sharedConversationId = sharedConversationId,
    rating = rating?.toInt(),
    summary = summary,
    recommendations = recommendations,
    cancelReason = cancelReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
