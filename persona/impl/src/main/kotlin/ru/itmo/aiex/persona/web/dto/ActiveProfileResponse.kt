package ru.itmo.aiex.persona.web.dto

import ru.itmo.aiex.persona.application.ActiveProfileRef
import java.util.UUID

data class ActiveProfileResponse(val id: UUID, val versionNo: Int)

fun ActiveProfileRef.toResponse() = ActiveProfileResponse(id, versionNo)
