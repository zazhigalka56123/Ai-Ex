package ru.itmo.aiex.common.events

import ru.itmo.aiex.common.id.Ids
import java.time.Instant
import java.util.UUID

data class PersonaArchived(
    val personaId: UUID,
    val ownerId: UUID,
    val archivedBy: UUID,
    val byAdmin: Boolean,
    override val occurredAt: Instant,
    override val eventId: UUID = Ids.next(),
) : DomainEvent
