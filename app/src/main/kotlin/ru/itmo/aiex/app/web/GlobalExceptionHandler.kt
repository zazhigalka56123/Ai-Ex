package ru.itmo.aiex.app.web

import jakarta.persistence.EntityNotFoundException
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.TypeMismatchException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.springframework.web.servlet.resource.NoResourceFoundException
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.FieldViolation
import tools.jackson.core.JacksonException
import java.net.URI
import java.time.Clock
import java.time.temporal.ChronoUnit
import org.hibernate.exception.ConstraintViolationException as HibernateConstraintViolation

@RestControllerAdvice
class GlobalExceptionHandler(private val clock: Clock) : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(AiExException::class)
    fun handleDomain(ex: AiExException, request: WebRequest): ResponseEntity<Any> = problem(ex.code, ex.message, request, ex.violations, ex)

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException, request: WebRequest): ResponseEntity<Any> {
        val violations =
            ex.constraintViolations.map {
                val field = it.propertyPath.toString().substringAfterLast('.')
                FieldViolation(field, constraintCode(it.constraintDescriptor.annotation.annotationClass.simpleName), it.message)
            }
        return problem(ErrorCode.VALIDATION_FAILED, "Нарушены ограничения валидации", request, violations, ex)
    }

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFound(ex: EntityNotFoundException, request: WebRequest): ResponseEntity<Any> =
        problem(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.title, request, emptyList(), ex)

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLock(ex: OptimisticLockingFailureException, request: WebRequest): ResponseEntity<Any> =
        problem(ErrorCode.CONCURRENT_MODIFICATION, "Ресурс изменён параллельным запросом, повторите операцию", request, emptyList(), ex)

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(ex: DataIntegrityViolationException, request: WebRequest): ResponseEntity<Any> {
        val constraint = generateSequence<Throwable>(ex) { it.cause }.filterIsInstance<HibernateConstraintViolation>().firstOrNull()?.constraintName
        val detail = if (constraint != null) "Нарушено ограничение $constraint" else ErrorCode.CONSTRAINT_VIOLATED.title
        return problem(ErrorCode.CONSTRAINT_VIOLATED, detail, request, emptyList(), ex)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: WebRequest): ResponseEntity<Any> =
        problem(ErrorCode.INTERNAL_ERROR, "Внутренняя ошибка сервера. Сообщите traceId администратору", request, emptyList(), ex)

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val code = errorCodeFor(ex, statusCode)
        val detail =
            when (ex) {
                is MethodArgumentNotValidException, is HandlerMethodValidationException -> "Запрос не прошёл валидацию"
                is HttpMessageNotReadableException -> "Тело запроса не читается как JSON нужной формы"
                is MaxUploadSizeExceededException -> "Файл больше допустимого размера"
                else -> (body as? ProblemDetail)?.detail ?: ex.message ?: code.title
            }
        return problem(code, detail, request, violationsFor(ex), ex, headers)
    }

    private fun errorCodeFor(ex: Exception, statusCode: HttpStatusCode): ErrorCode = when (ex) {
        is NoResourceFoundException -> ErrorCode.NOT_FOUND

        is HttpRequestMethodNotSupportedException -> ErrorCode.METHOD_NOT_ALLOWED

        is HttpMediaTypeNotSupportedException -> ErrorCode.UNSUPPORTED_MEDIA_TYPE

        is HttpMediaTypeNotAcceptableException -> ErrorCode.NOT_ACCEPTABLE

        is MaxUploadSizeExceededException -> ErrorCode.FILE_TOO_LARGE

        else ->
            when (statusCode.value()) {
                HttpStatus.NOT_FOUND.value() -> ErrorCode.NOT_FOUND
                HttpStatus.PAYLOAD_TOO_LARGE.value() -> ErrorCode.FILE_TOO_LARGE
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value() -> ErrorCode.UNSUPPORTED_MEDIA_TYPE
                in CLIENT_ERRORS -> ErrorCode.VALIDATION_FAILED
                else -> ErrorCode.INTERNAL_ERROR
            }
    }

    private fun violationsFor(ex: Exception): List<FieldViolation> = when (ex) {
        is MethodArgumentNotValidException ->
            ex.bindingResult.fieldErrors.map {
                FieldViolation(it.field, constraintCode(it.code), it.defaultMessage ?: "некорректное значение")
            } +
                ex.bindingResult.globalErrors.map {
                    FieldViolation(it.objectName, constraintCode(it.code), it.defaultMessage ?: "некорректное значение")
                }

        is HandlerMethodValidationException ->
            ex.parameterValidationResults.flatMap { result ->
                val name = result.methodParameter.parameterName ?: "parameter"
                result.resolvableErrors.map { error ->
                    FieldViolation(name, constraintCode(error.codes?.lastOrNull()), error.defaultMessage ?: "некорректное значение")
                }
            }

        is MissingServletRequestParameterException ->
            listOf(FieldViolation(ex.parameterName, "required", "Обязательный параметр не передан"))

        is MissingServletRequestPartException ->
            listOf(FieldViolation(ex.requestPartName, "required", "Обязательная часть multipart-запроса не передана"))

        is MissingRequestHeaderException ->
            listOf(FieldViolation(ex.headerName, "required", "Обязательный заголовок не передан"))

        is TypeMismatchException ->
            listOf(
                FieldViolation(
                    ex.propertyName ?: "parameter",
                    "type",
                    "Значение '${ex.value}' не приводится к типу ${ex.requiredType?.simpleName}",
                ),
            )

        is HttpMessageNotReadableException -> jsonViolation(ex)

        else -> emptyList()
    }

    private fun jsonViolation(ex: HttpMessageNotReadableException): List<FieldViolation> {
        val jackson = generateSequence<Throwable>(ex) { it.cause }.filterIsInstance<JacksonException>().firstOrNull() ?: return emptyList()
        val path =
            jackson.path
                .joinToString(".") { ref -> ref.propertyName ?: "[${ref.index}]" }
                .replace(".[", "[")
        return if (path.isBlank()) emptyList() else listOf(FieldViolation(path, "json", "Поле отсутствует или имеет неверный тип"))
    }

    private fun problem(
        code: ErrorCode,
        detail: String,
        request: WebRequest,
        violations: List<FieldViolation>,
        ex: Exception,
        headers: HttpHeaders = HttpHeaders(),
    ): ResponseEntity<Any> {
        val path = (request as? ServletWebRequest)?.request?.requestURI
        if (code.httpStatus >= HttpStatus.INTERNAL_SERVER_ERROR.value() && code != ErrorCode.LLM_UNAVAILABLE) {
            log.error("{} {}: {}", code, path, ex.message, ex)
        } else {
            log.info("{} {}: {}", code, path, detail)
        }
        val body =
            ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(code.httpStatus), detail).apply {
                type = URI.create("$ERROR_TYPE_BASE/${code.slug}")
                title = code.title
                if (path != null) instance = URI.create(path)
                setProperty("code", code.name)
                setProperty("traceId", MDC.get(TraceIdFilter.MDC_KEY))
                setProperty("timestamp", clock.instant().truncatedTo(ChronoUnit.SECONDS).toString())
                if (violations.isNotEmpty()) setProperty("errors", violations)
            }
        val responseHeaders = HttpHeaders()
        responseHeaders.addAll(headers)
        responseHeaders.remove(HttpHeaders.CONTENT_TYPE)
        return ResponseEntity.status(code.httpStatus).headers(responseHeaders).body(body)
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
        val CLIENT_ERRORS = 400..499
    }
}
