package ru.itmo.aiex.reactive

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.server.MethodNotAllowedException
import org.springframework.web.server.MissingRequestValueException
import org.springframework.web.server.NotAcceptableStatusException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException
import org.springframework.web.server.UnsupportedMediaTypeStatusException
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.FieldViolation
import ru.itmo.aiex.common.web.AiExHeaders
import tools.jackson.core.JacksonException
import java.net.URI
import java.time.Clock
import java.time.temporal.ChronoUnit

/** Ошибки WebFlux-сервисов в том же формате ProblemDetail, что и у Spring MVC-сервисов. */
@RestControllerAdvice
class ReactiveExceptionHandler(private val clock: Clock) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(AiExException::class)
    fun handleDomain(ex: AiExException, exchange: ServerWebExchange): ResponseEntity<ProblemDetail> =
        problem(ex.code, ex.message, exchange, ex.violations, ex)

    @ExceptionHandler(WebExchangeBindException::class)
    fun handleBind(ex: WebExchangeBindException, exchange: ServerWebExchange): ResponseEntity<ProblemDetail> {
        val violations =
            ex.bindingResult.fieldErrors.map { FieldViolation(it.field, constraintCode(it.code), it.defaultMessage ?: INVALID) } +
                ex.bindingResult.globalErrors.map { FieldViolation(it.objectName, constraintCode(it.code), it.defaultMessage ?: INVALID) }
        return problem(ErrorCode.VALIDATION_FAILED, "Запрос не прошёл валидацию", exchange, violations, ex)
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidation(ex: HandlerMethodValidationException, exchange: ServerWebExchange): ResponseEntity<ProblemDetail> {
        val violations =
            ex.parameterValidationResults.flatMap { result ->
                val name = result.methodParameter.parameterName ?: "parameter"
                result.resolvableErrors.map { FieldViolation(name, constraintCode(it.codes?.lastOrNull()), it.defaultMessage ?: INVALID) }
            }
        return problem(ErrorCode.VALIDATION_FAILED, "Запрос не прошёл валидацию", exchange, violations, ex)
    }

    @ExceptionHandler(ServerWebInputException::class)
    fun handleInput(ex: ServerWebInputException, exchange: ServerWebExchange): ResponseEntity<ProblemDetail> {
        val jackson = generateSequence<Throwable>(ex) { it.cause }.filterIsInstance<JacksonException>().firstOrNull()
        val violations =
            when {
                jackson != null -> jsonViolation(jackson)
                ex is MissingRequestValueException -> listOf(FieldViolation(ex.name, "required", "Обязательное значение не передано"))
                else -> listOfNotNull(ex.methodParameter?.parameterName?.let { FieldViolation(it, "type", ex.reason ?: INVALID) })
            }
        val detail = if (jackson != null) "Тело запроса не читается как JSON нужной формы" else ex.reason ?: ErrorCode.VALIDATION_FAILED.title
        return problem(ErrorCode.VALIDATION_FAILED, detail, exchange, violations, ex)
    }

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLock(ex: OptimisticLockingFailureException, exchange: ServerWebExchange): ResponseEntity<ProblemDetail> =
        problem(ErrorCode.CONCURRENT_MODIFICATION, "Ресурс изменён параллельным запросом, повторите операцию", exchange, emptyList(), ex)

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(ex: DataIntegrityViolationException, exchange: ServerWebExchange): ResponseEntity<ProblemDetail> =
        problem(ErrorCode.CONSTRAINT_VIOLATED, ErrorCode.CONSTRAINT_VIOLATED.title, exchange, emptyList(), ex)

    @ExceptionHandler(ResponseStatusException::class)
    fun handleStatus(ex: ResponseStatusException, exchange: ServerWebExchange): ResponseEntity<ProblemDetail> {
        val code =
            when {
                ex is MethodNotAllowedException -> ErrorCode.METHOD_NOT_ALLOWED
                ex is UnsupportedMediaTypeStatusException -> ErrorCode.UNSUPPORTED_MEDIA_TYPE
                ex is NotAcceptableStatusException -> ErrorCode.NOT_ACCEPTABLE
                ex.statusCode.value() == HttpStatus.NOT_FOUND.value() -> ErrorCode.NOT_FOUND
                ex.statusCode.value() == HttpStatus.SERVICE_UNAVAILABLE.value() -> ErrorCode.SERVICE_UNAVAILABLE
                ex.statusCode.is4xxClientError -> ErrorCode.VALIDATION_FAILED
                else -> ErrorCode.INTERNAL_ERROR
            }
        return problem(code, ex.reason ?: code.title, exchange, emptyList(), ex)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, exchange: ServerWebExchange): ResponseEntity<ProblemDetail> =
        problem(ErrorCode.INTERNAL_ERROR, "Внутренняя ошибка сервера. Сообщите traceId администратору", exchange, emptyList(), ex)

    private fun jsonViolation(ex: JacksonException): List<FieldViolation> {
        val path = ex.path.joinToString(".") { ref -> ref.propertyName ?: "[${ref.index}]" }.replace(".[", "[")
        return if (path.isBlank()) emptyList() else listOf(FieldViolation(path, "json", "Поле отсутствует или имеет неверный тип"))
    }

    private fun problem(
        code: ErrorCode,
        detail: String,
        exchange: ServerWebExchange,
        violations: List<FieldViolation>,
        ex: Exception,
    ): ResponseEntity<ProblemDetail> {
        val path = exchange.request.path.value()
        if (code.httpStatus >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            log.error("{} {}: {}", code, path, ex.message, ex)
        } else {
            log.info("{} {}: {}", code, path, detail)
        }
        val body =
            ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(code.httpStatus), detail).apply {
                type = URI.create("$ERROR_TYPE_BASE/${code.slug}")
                title = code.title
                instance = URI.create(path)
                setProperty("code", code.name)
                setProperty("traceId", exchange.request.headers.getFirst(AiExHeaders.TRACE_ID))
                setProperty("timestamp", clock.instant().truncatedTo(ChronoUnit.SECONDS).toString())
                if (violations.isNotEmpty()) setProperty("errors", violations)
            }
        return ResponseEntity.status(code.httpStatus).body(body)
    }

    private fun constraintCode(raw: String?): String = when (raw) {
        null -> "invalid"
        "NotBlank", "NotNull", "NotEmpty" -> "required"
        "Size", "Length" -> "size"
        "Min", "Max", "Positive", "PositiveOrZero", "DecimalMin", "DecimalMax" -> "range"
        "Email" -> "email"
        "Pattern" -> "pattern"
        else -> raw.replaceFirstChar { it.lowercase() }
    }

    private companion object {
        const val ERROR_TYPE_BASE = "https://ai-ex.itmo.ru/errors"
        const val INVALID = "некорректное значение"
    }
}
