package ru.itmo.aiex.notification.application

import java.util.UUID

fun interface NotificationPort {
    fun send(command: NotificationCommand): UUID
}
