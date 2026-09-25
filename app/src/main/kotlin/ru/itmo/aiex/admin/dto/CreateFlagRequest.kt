package ru.itmo.aiex.admin.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import ru.itmo.aiex.common.moderation.FlagReason
import java.util.UUID

data class CreateFlagRequest(
    val messageId: UUID,
    @field:Schema(example = "ABUSE")
    val reason: FlagReason,
    @field:Size(max = 500)
    @field:Schema(example = "Персона оскорбляет")
    val comment: String? = null,
)
