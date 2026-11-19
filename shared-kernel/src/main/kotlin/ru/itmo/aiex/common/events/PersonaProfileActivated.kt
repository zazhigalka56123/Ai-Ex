package ru.itmo.aiex.common.events

import ru.itmo.aiex.common.id.Ids
import java.time.Instant
import java.util.UUID

data class PersonaProfileActivated(
    val personaId: UUID,
    val ownerId: UUID,
    val profileId: UUID,
    val versionNo: Int,
    override val occurredAt: Instant,
    override val eventId: UUID = Ids.next(),
) : DomainEvent
