package ru.itmo.aiex.common.error

class FileTooLargeException(message: String) : AiExException(ErrorCode.FILE_TOO_LARGE, message)
