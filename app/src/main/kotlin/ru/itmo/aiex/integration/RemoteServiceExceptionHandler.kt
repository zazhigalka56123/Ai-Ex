package ru.itmo.aiex.integration

import feign.FeignException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import ru.itmo.aiex.common.error.ErrorCode
import java.net.URI

@Order(-1)
@RestControllerAdvice
class RemoteServiceExceptionHandler {
    @ExceptionHandler(NoFallbackAvailableException::class, FeignException::class)
    fun unavailable(ex: Exception, request: HttpServletRequest): ProblemDetail {
        val remote = generateSequence<Throwable>(ex) { it.cause }.filterIsInstance<FeignException>().firstOrNull()
        val status = remote?.status()?.takeIf { it in CLIENT_ERRORS } ?: HttpStatus.SERVICE_UNAVAILABLE.value()
        val code = ErrorCode.fromHttpStatus(status)
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), code.title).apply {
            type = URI.create("https://ai-ex.itmo.ru/errors/${code.slug}")
            title = code.title
            setProperty("code", code.name)
            setProperty("traceId", request.getAttribute("traceId"))
        }
    }

    private companion object {
        val CLIENT_ERRORS = HttpStatus.BAD_REQUEST.value() until HttpStatus.INTERNAL_SERVER_ERROR.value()
    }
}
