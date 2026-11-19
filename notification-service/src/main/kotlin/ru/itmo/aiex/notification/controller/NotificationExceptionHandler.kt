package ru.itmo.aiex.notification.controller

import feign.FeignException
import org.slf4j.LoggerFactory
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ServerWebExchange
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.FieldViolation
import ru.itmo.aiex.notification.web.TraceIdWebFilter
import java.net.URI
import java.time.Instant

@RestControllerAdvice
class NotificationExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(Exception::class)
    fun handle(ex: Exception, exchange: ServerWebExchange): ProblemDetail {
        val remote = generateSequence<Throwable>(ex) { it.cause }.filterIsInstance<FeignException>().firstOrNull()
        val status = when {
            ex is AiExException -> ex.code.httpStatus
            ex is ErrorResponse -> ex.statusCode.value()
            remote != null -> remote.status().takeIf { it in CLIENT_ERRORS } ?: HttpStatus.SERVICE_UNAVAILABLE.value()
            ex is NoFallbackAvailableException -> HttpStatus.SERVICE_UNAVAILABLE.value()
            ex is DataIntegrityViolationException -> HttpStatus.CONFLICT.value()
            else -> HttpStatus.INTERNAL_SERVER_ERROR.value()
        }
        val code = if (ex is AiExException) ex.code else ErrorCode.fromHttpStatus(status)
        val body = if (ex is ErrorResponse) {
            exchange.response.headers.addAll(ex.headers)
            ex.body
        } else {
            ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), if (ex is AiExException) ex.message else code.title)
        }
        if (HttpStatusCode.valueOf(status).is5xxServerError) log.error("Request {} failed", exchange.request.path, ex)
        body.apply {
            type = URI.create("https://ai-ex.itmo.ru/errors/${code.slug}")
            title = code.title
            instance = URI.create(exchange.request.path.value())
            setProperty("code", code.name)
            setProperty("traceId", exchange.getAttribute<String>(TraceIdWebFilter.CONTEXT_KEY))
            setProperty("timestamp", Instant.now().toString())
            val violations = violations(ex)
            if (violations.isNotEmpty()) setProperty("errors", violations)
        }
        return body
    }

    private fun violations(ex: Exception): List<FieldViolation> = when (ex) {
        is AiExException -> ex.violations

        is WebExchangeBindException -> ex.fieldErrors.map {
            FieldViolation(it.field, it.code ?: "invalid", it.defaultMessage ?: "Некорректное значение")
        }

        else -> emptyList()
    }

    private companion object {
        val CLIENT_ERRORS = HttpStatus.BAD_REQUEST.value() until HttpStatus.INTERNAL_SERVER_ERROR.value()
    }
}
