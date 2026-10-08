package ru.itmo.aiex.ingest.entity

enum class ImportErrorCode {
    MALFORMED_FILE,
    EMPTY_CORPUS,
    AUTHOR_NOT_DETECTED,
    TOO_MANY_MESSAGES,
    PERSONA_BUSY,
    PROFILE_REBUILD_FAILED,
    INTERNAL,
}
