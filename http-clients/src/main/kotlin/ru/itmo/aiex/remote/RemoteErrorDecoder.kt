package ru.itmo.aiex.remote

import feign.Response
import feign.codec.ErrorDecoder
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.FieldViolation
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class RemoteErrorDecoder(private val mapper: JsonMapper) : ErrorDecoder {
    override fun decode(methodKey: String, response: Response): Exception {
        val body = runCatching { response.body()?.asInputStream()?.use(mapper::readTree) }.getOrNull()
        val code = body?.string("code")?.let { runCatching { ErrorCode.valueOf(it) }.getOrNull() } ?: codeFor(response.status())
        val detail = body?.string("detail") ?: code.title
        val violations = body?.get("errors")?.takeIf { it.isArray }?.mapNotNull { violation ->
            val field = violation.string("field") ?: return@mapNotNull null
            FieldViolation(field, violation.string("code") ?: "invalid", violation.string("message") ?: "Некорректное значение")
        }.orEmpty()
        return AiExException(code, detail, violations)
    }

    private fun codeFor(status: Int): ErrorCode = when (status) {
        ErrorCode.UNAUTHENTICATED.httpStatus -> ErrorCode.UNAUTHENTICATED
        ErrorCode.FORBIDDEN.httpStatus -> ErrorCode.FORBIDDEN
        ErrorCode.NOT_FOUND.httpStatus -> ErrorCode.NOT_FOUND
        ErrorCode.CONSTRAINT_VIOLATED.httpStatus -> ErrorCode.CONSTRAINT_VIOLATED
        in CLIENT_ERRORS -> ErrorCode.VALIDATION_FAILED
        else -> ErrorCode.SERVICE_UNAVAILABLE
    }

    private fun JsonNode.string(name: String): String? = get(name)?.takeIf { it.isString }?.asString()

    private companion object {
        val CLIENT_ERRORS = 400..499
    }
}
