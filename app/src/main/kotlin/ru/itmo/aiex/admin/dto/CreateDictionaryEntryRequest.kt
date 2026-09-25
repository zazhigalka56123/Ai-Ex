package ru.itmo.aiex.admin.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateDictionaryEntryRequest(
    @field:NotBlank
    @field:Size(max = 48)
    @field:Pattern(regexp = "[A-Za-z0-9_-]+")
    @field:Schema(description = "Код; сохраняется в нижнем регистре и после создания не меняется", example = "insomnia")
    val code: String,
    @field:NotBlank
    @field:Size(max = 96)
    @field:Schema(example = "Бессонница")
    val title: String,
)
