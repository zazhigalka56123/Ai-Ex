package ru.itmo.aiex.care.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import ru.itmo.aiex.care.application.SpecialistCard
import ru.itmo.aiex.care.domain.SpecialistStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class SpecialistResponse(
    val id: UUID,
    val userId: UUID,
    @field:Schema(description = "Имя пользователя из iam; null, если пользователь заблокирован")
    val displayName: String?,
    val headline: String,
    val bio: String,
    val pricePerHour: BigDecimal,
    val status: SpecialistStatus,
    @field:Schema(description = "Число активных записей к специалисту")
    val bookedCount: Int,
    val specializations: List<SpecializationRefResponse>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun SpecialistCard.toResponse() = SpecialistResponse(
    id = specialist.id,
    userId = specialist.userId,
    displayName = displayName,
    headline = specialist.headline,
    bio = specialist.bio,
    pricePerHour = specialist.pricePerHour,
    status = specialist.status,
    bookedCount = specialist.bookedCount,
    specializations = specialist.specializations.sortedBy { it.code }.map { it.toRef() },
    createdAt = specialist.createdAt,
    updatedAt = specialist.updatedAt,
)
