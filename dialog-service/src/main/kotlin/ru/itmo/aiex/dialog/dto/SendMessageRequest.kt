package ru.itmo.aiex.dialog.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SendMessageRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 2000)
    @field:Schema(example = "привет, спишь?")
    val text: String,
)
