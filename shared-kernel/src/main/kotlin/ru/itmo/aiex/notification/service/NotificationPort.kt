package ru.itmo.aiex.notification.service

import java.util.UUID

fun interface NotificationPort {
    fun sendNotification(command: NotificationCommand): UUID
}
