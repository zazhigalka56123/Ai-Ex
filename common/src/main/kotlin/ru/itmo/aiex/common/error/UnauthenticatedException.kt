package ru.itmo.aiex.common.error

class UnauthenticatedException(message: String) : AiExException(ErrorCode.UNAUTHENTICATED, message)
