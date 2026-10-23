package ru.itmo.aiex.common.error

class IllegalStateTransitionException(code: ErrorCode, val from: String, val to: String, message: String = "Переход $from -> $to недопустим") :
    AiExException(code, message)
