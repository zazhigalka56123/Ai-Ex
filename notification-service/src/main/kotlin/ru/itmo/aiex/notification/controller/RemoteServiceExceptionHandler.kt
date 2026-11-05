package ru.itmo.aiex.notification.controller

import feign.FeignException
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@Order(-1)
@RestControllerAdvice
class RemoteServiceExceptionHandler {
    @ExceptionHandler(NoFallbackAvailableException::class, FeignException::class)
    fun unavailable(ex: Exception): ProblemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Зависимый сервис временно недоступен",
    )
}
