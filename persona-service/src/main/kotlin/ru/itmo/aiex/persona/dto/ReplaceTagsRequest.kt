package ru.itmo.aiex.persona.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Size

data class ReplaceTagsRequest(
    @field:Size(max = 10)
    @field:Valid
    val tags: List<TagAssignmentRequest> = emptyList(),
)
