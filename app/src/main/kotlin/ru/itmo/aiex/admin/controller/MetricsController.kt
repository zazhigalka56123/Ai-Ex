package ru.itmo.aiex.admin.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.itmo.aiex.admin.service.MetricsBoard
import ru.itmo.aiex.admin.dto.MetricsResponse
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.web.ApiPaths
import ru.itmo.aiex.common.web.openapi.ApiErrors
@RestController
@RequestMapping("${ApiPaths.V1}/admin/metrics")
@Tag(name = "Метрики", description = "Агрегированные метрики модулей для администратора")
class MetricsController(private val board: MetricsBoard) {
    @GetMapping
    @Operation(
        operationId = "getMetrics",
        summary = "Метрики системы",
        description = "Только администратор: агрегаты всех модулей (`users.*`, `consultations.*`, `flags.*`, …) без персональных данных.",
    )
    @ApiResponse(responseCode = "200", description = "Снимок метрик")
    @ApiErrors(ErrorCode.FORBIDDEN)
    fun get(actor: Actor): MetricsResponse = board.snapshot(actor).let { MetricsResponse(it.metrics, it.generatedAt) }
}
