package ru.itmo.aiex.common.events

import ru.itmo.aiex.common.id.Ids
import java.time.Instant
import java.util.UUID

data class ModerationFlagResolved(
    val flagId: UUID,
    val messageId: UUID,
    val status: String,
    val reporterId: UUID?,
    override val occurredAt: Instant,
    override val eventId: UUID = Ids.next(),
) : DomainEvent
