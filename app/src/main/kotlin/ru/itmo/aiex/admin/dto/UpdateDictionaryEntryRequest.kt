package ru.itmo.aiex.admin.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateDictionaryEntryRequest(
    @field:NotBlank
    @field:Size(max = 96)
    val title: String,
)
