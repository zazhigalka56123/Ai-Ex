package ru.itmo.aiex.care.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant

data class CreateSlotRequest(
    @field:Future
    @field:Schema(example = "2026-10-01T10:00:00Z")
    val startsAt: Instant,
    @field:Min(15)
    @field:Max(240)
    @field:Schema(example = "60")
    val durationMin: Int,
)
