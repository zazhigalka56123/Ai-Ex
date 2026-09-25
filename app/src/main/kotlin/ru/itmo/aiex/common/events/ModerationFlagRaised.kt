package ru.itmo.aiex.common.events

import ru.itmo.aiex.common.id.Ids
import ru.itmo.aiex.common.moderation.FlagReason
import java.time.Instant
import java.util.UUID

data class ModerationFlagRaised(
    val flagId: UUID,
    val messageId: UUID,
    val reason: FlagReason,
    override val occurredAt: Instant,
    override val eventId: UUID = Ids.next(),
) : DomainEvent
