package ru.itmo.aiex.persona.dto

import ru.itmo.aiex.persona.service.ActiveProfileRef
import java.util.UUID

data class ActiveProfileResponse(val id: UUID, val versionNo: Int)

fun ActiveProfileRef.toResponse() = ActiveProfileResponse(id, versionNo)
