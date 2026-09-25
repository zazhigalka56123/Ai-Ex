package ru.itmo.aiex.common.error

class UnsupportedImportFormatException(message: String) : AiExException(ErrorCode.UNSUPPORTED_FORMAT, message)
