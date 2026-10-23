package ru.itmo.aiex.common.error

class LlmUnavailableException(message: String, cause: Throwable? = null) : AiExException(ErrorCode.LLM_UNAVAILABLE, message, cause = cause)
