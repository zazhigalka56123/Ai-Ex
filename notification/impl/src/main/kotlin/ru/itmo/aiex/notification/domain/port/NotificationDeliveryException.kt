package ru.itmo.aiex.notification.domain.port

class NotificationDeliveryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
