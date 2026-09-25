package ru.itmo.aiex.care.dto

import ru.itmo.aiex.care.entity.SpecialistSlot
import java.time.Instant
import java.util.UUID

data class SlotResponse(
    val id: UUID,
    val specialistId: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
    val durationMin: Int,
    val createdAt: Instant,
)

fun SpecialistSlot.toResponse() = SlotResponse(
    id = id,
    specialistId = specialist.id,
    startsAt = startsAt,
    endsAt = endsAt,
    durationMin = durationMin,
    createdAt = createdAt,
)
