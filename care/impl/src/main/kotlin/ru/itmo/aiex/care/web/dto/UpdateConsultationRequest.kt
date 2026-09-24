package ru.itmo.aiex.care.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import ru.itmo.aiex.care.domain.ConsultationChange
import ru.itmo.aiex.care.domain.SessionStatus

data class UpdateConsultationRequest(
    @field:Schema(description = "Клиент: CANCELLED. Специалист: CONFIRMED, DONE, CANCELLED. Администратор: DONE, CANCELLED")
    val status: SessionStatus? = null,
    @field:Size(max = 10_000)
    @field:Schema(description = "Только специалист, в статусах CONFIRMED/DONE")
    val summary: String? = null,
    @field:Size(max = 10_000)
    @field:Schema(description = "Только специалист, в статусах CONFIRMED/DONE")
    val recommendations: String? = null,
    @field:Min(1)
    @field:Max(5)
    @field:Schema(description = "Только клиент, в статусе DONE")
    val rating: Int? = null,
    @field:Size(max = 500)
    @field:Schema(description = "Только вместе со статусом CANCELLED")
    val cancelReason: String? = null,
) {
    fun toChange() = ConsultationChange(
        status = status,
        summary = summary,
        recommendations = recommendations,
        rating = rating?.toShort(),
        cancelReason = cancelReason,
    )
}
