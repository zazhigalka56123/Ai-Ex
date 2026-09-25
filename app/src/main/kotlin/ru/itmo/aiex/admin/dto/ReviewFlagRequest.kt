package ru.itmo.aiex.admin.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import ru.itmo.aiex.admin.entity.FlagStatus
data class ReviewFlagRequest(
    @field:Schema(description = "IN_REVIEW, RESOLVED или REJECTED; RESOLVED и REJECTED - терминальные")
    val status: FlagStatus,
    @field:Size(max = 4000)
    @field:Schema(example = "Ответ персоны нарушает правила, персона архивирована")
    val resolution: String? = null,
    @field:Schema(description = "Архивировать персону сообщения (только вместе с RESOLVED)")
    val archivePersona: Boolean = false,
)
