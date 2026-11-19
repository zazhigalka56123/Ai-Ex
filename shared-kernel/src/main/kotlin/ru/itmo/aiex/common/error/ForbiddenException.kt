package ru.itmo.aiex.common.error

class ForbiddenException(message: String) : AiExException(ErrorCode.FORBIDDEN, message)
