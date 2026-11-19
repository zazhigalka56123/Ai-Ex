package ru.itmo.aiex.common.error

open class ConflictException(code: ErrorCode, message: String, cause: Throwable? = null) : AiExException(code, message, cause = cause)
