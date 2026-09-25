package ru.itmo.aiex.persona.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import ru.itmo.aiex.persona.entity.RelationshipKind
data class UpdatePersonaRequest(
    @field:Size(min = 1, max = 64)
    @field:Pattern(regexp = ".*\\S.*", message = "имя не может состоять из пробелов")
    val name: String? = null,
    val relationshipKind: RelationshipKind? = null,
    @field:Size(max = 500)
    @field:Schema(description = "Пустая строка очищает описание; отсутствие поля - не меняет")
    val description: String? = null,
)
