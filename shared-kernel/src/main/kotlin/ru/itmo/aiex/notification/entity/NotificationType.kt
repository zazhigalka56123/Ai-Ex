package ru.itmo.aiex.notification.entity

enum class NotificationType {
    IMPORT_PARSED,
    IMPORT_FAILED,
    PERSONA_READY,
    PERSONA_ARCHIVED,
    CONSULTATION_REQUESTED,
    CONSULTATION_STATUS_CHANGED,
    FLAG_RESOLVED,
}
