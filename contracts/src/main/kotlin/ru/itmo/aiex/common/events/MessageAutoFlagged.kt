package ru.itmo.aiex.common.events

import ru.itmo.aiex.common.id.Ids
import ru.itmo.aiex.common.moderation.FlagReason
import java.time.Instant
import java.util.UUID

data class MessageAutoFlagged(
    val messageId: UUID,
    val conversationId: UUID,
    val reason: FlagReason,
    val details: String,
    override val occurredAt: Instant,
    override val eventId: UUID = Ids.next(),
) : DomainEvent
