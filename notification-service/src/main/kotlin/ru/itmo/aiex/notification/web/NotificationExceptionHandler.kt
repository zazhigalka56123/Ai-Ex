package ru.itmo.aiex.notification.web

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import ru.itmo.aiex.common.error.AiExException
import java.net.URI
import java.time.Clock
import java.time.temporal.ChronoUnit

@RestControllerAdvice
class NotificationExceptionHandler(private val clock: Clock) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(AiExException::class)
    fun domain(error: AiExException, exchange: ServerWebExchange): ResponseEntity<ProblemDetail> = problem(
        HttpStatusCode.valueOf(error.code.httpStatus),
        error.code.name,
        error.code.title,
        error.message,
        exchange,
    ).also { if (error.violations.isNotEmpty()) it.body!!.setProperty("errors", error.violations) }

    @ExceptionHandler(ResponseStatusException::class)
    fun web(error: ResponseStatusException, exchange: ServerWebExchange): ResponseEntity<ProblemDetail> {
        val code = when (HttpStatus.resolve(error.statusCode.value())) {
            HttpStatus.BAD_REQUEST -> "VALIDATION_FAILED"
            HttpStatus.UNAUTHORIZED -> "UNAUTHENTICATED"
            HttpStatus.FORBIDDEN -> "FORBIDDEN"
            HttpStatus.NOT_FOUND -> "NOT_FOUND"
            HttpStatus.SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE"
            else -> "INTERNAL_ERROR"
        }
        return problem(error.statusCode, code, error.body.title ?: code, error.reason ?: error.body.detail ?: code, exchange)
    }

    @ExceptionHandler(Exception::class)
    fun unexpected(error: Exception, exchange: ServerWebExchange): ResponseEntity<ProblemDetail> {
        log.error("Ошибка обработки {}", exchange.request.path, error)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Внутренняя ошибка сервера", "Внутренняя ошибка сервера", exchange)
    }

    private fun problem(
        status: HttpStatusCode,
        code: String,
        title: String,
        detail: String,
        exchange: ServerWebExchange,
    ): ResponseEntity<ProblemDetail> {
        val body = ProblemDetail.forStatusAndDetail(status, detail).apply {
            type = URI.create("https://ai-ex.itmo.ru/errors/${code.lowercase().replace('_', '-')}")
            this.title = title
            instance = exchange.request.uri.path.let(URI::create)
            setProperty("code", code)
            setProperty("timestamp", clock.instant().truncatedTo(ChronoUnit.SECONDS).toString())
            setProperty("traceId", exchange.request.headers.getFirst("X-Trace-Id"))
        }
        return ResponseEntity.status(status).body(body)
    }
}
