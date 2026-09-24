package ru.itmo.aiex.ingest.domain.parsing

import ru.itmo.aiex.ingest.domain.ImportErrorCode

class ChatParseException(val code: ImportErrorCode, message: String, cause: Throwable? = null) : RuntimeException(message, cause)
