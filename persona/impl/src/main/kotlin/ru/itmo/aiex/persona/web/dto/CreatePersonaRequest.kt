package ru.itmo.aiex.persona.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import ru.itmo.aiex.persona.domain.RelationshipKind

data class CreatePersonaRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 64)
    @field:Schema(example = "Маша")
    val name: String,
    @field:Schema(example = "EX_PARTNER")
    val relationshipKind: RelationshipKind,
    @field:Size(max = 500)
    @field:Schema(example = "Встречались три года, расстались весной")
    val description: String? = null,
    @field:Size(max = 10)
    @field:Schema(description = "Коды тегов из справочника; ставятся как ручные с весом 1.0", example = "[\"jealous\", \"night-owl\"]")
    val tagCodes: Set<@NotBlank String> = emptySet(),
)
