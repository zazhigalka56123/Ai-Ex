package ru.itmo.aiex.notification.service

import ru.itmo.aiex.notification.entity.Notification
fun interface NotificationSender {
    fun deliver(notification: Notification)
}
