package ru.itmo.aiex.care.entity

import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

object CareFixtures {
    val NOW: Instant = Instant.parse("2026-10-01T09:00:00Z")

    fun specialization(code: String = "breakup") = Specialization(code, code.replaceFirstChar { it.uppercase() })

    fun specialist(userId: UUID = UUID.randomUUID()) = Specialist(UUID.randomUUID(), userId, "Психолог", "Био", BigDecimal("2500.00"), NOW).apply {
        replaceSpecializations(listOf(specialization()), NOW)
    }

    fun slot(specialist: Specialist = specialist(), startsAt: Instant = NOW.plusSeconds(3600), durationMin: Int = 60) =
        SpecialistSlot(UUID.randomUUID(), specialist, startsAt, durationMin, NOW)

    fun session(specialist: Specialist = specialist(), clientId: UUID = UUID.randomUUID(), conversationId: UUID? = null) =
        ConsultationSession(UUID.randomUUID(), clientId, specialist, slot(specialist), conversationId, NOW)

    fun actor(userId: UUID = UUID.randomUUID(), vararg roles: RoleCode = arrayOf(RoleCode.USER)) = Actor(userId, roles.toSet())
}
