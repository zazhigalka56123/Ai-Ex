package ru.itmo.aiex.dialog.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateConversationRequest(
    @field:Schema(
        description = "Своя персона в статусе READY",
        example = "0192ac8f-0000-7000-8000-000000000001",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    val personaId: UUID,
    @field:Size(min = 1, max = 128)
    @field:Schema(description = "Название; по умолчанию «Беседа с <имя персоны>»", example = "Три часа ночи")
    val title: String? = null,
)
