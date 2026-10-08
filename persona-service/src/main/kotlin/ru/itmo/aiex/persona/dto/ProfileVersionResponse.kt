package ru.itmo.aiex.persona.dto

import ru.itmo.aiex.persona.entity.PersonaProfileVersion
import java.time.Instant
import java.util.UUID

data class ProfileVersionResponse(val id: UUID, val versionNo: Int, val active: Boolean, val createdAt: Instant)

fun PersonaProfileVersion.toResponse() = ProfileVersionResponse(id, versionNo, active, createdAt)
