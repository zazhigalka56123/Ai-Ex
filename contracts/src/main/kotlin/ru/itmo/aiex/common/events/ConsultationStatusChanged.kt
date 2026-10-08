package ru.itmo.aiex.common.events

import ru.itmo.aiex.common.id.Ids
import java.time.Instant
import java.util.UUID

data class ConsultationStatusChanged(
    val sessionId: UUID,
    val clientId: UUID,
    val specialistUserId: UUID,
    val status: String,
    override val occurredAt: Instant,
    override val eventId: UUID = Ids.next(),
) : DomainEvent
