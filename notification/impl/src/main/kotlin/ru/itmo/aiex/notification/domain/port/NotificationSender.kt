package ru.itmo.aiex.notification.domain.port

import ru.itmo.aiex.notification.domain.Notification

fun interface NotificationSender {
    fun deliver(notification: Notification)
}
