package ru.itmo.aiex.admin.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class MetricsResponse(
    @field:Schema(description = "Агрегаты модулей `<модуль>.<метрика>`, по алфавиту ключей", example = "{\"flags.open\": 3, \"users.active\": 42}")
    val metrics: Map<String, Long>,
    val generatedAt: Instant,
)
