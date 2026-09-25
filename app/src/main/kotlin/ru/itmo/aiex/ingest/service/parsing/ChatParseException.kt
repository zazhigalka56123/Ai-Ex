package ru.itmo.aiex.ingest.service.parsing

import ru.itmo.aiex.ingest.entity.ImportErrorCode
class ChatParseException(val code: ImportErrorCode, message: String, cause: Throwable? = null) : RuntimeException(message, cause)
