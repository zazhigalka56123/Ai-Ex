package ru.itmo.aiex.common.events

import ru.itmo.aiex.common.id.Ids
import java.time.Instant
import java.util.UUID

data class ConsultationRequested(
    val sessionId: UUID,
    val clientId: UUID,
    val specialistUserId: UUID,
    val startsAt: Instant,
    override val occurredAt: Instant,
    override val eventId: UUID = Ids.next(),
) : DomainEvent
