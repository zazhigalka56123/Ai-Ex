package ru.itmo.aiex.common.error

open class AiExException(
    val code: ErrorCode,
    override val message: String,
    val violations: List<FieldViolation> = emptyList(),
    cause: Throwable? = null,
) : RuntimeException(message, cause)
