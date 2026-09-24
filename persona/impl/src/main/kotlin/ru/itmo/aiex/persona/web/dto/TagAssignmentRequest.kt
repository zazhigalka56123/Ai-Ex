package ru.itmo.aiex.persona.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class TagAssignmentRequest(
    @field:NotBlank
    @field:Size(max = 48)
    @field:Schema(example = "jealous")
    val code: String,
    @field:DecimalMin("0.0")
    @field:DecimalMax("1.0")
    @field:Schema(description = "Выраженность тега 0..1, по умолчанию 1.0", example = "0.7")
    val weight: BigDecimal? = null,
)
