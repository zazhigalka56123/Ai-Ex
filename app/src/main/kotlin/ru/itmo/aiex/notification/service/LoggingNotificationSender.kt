package ru.itmo.aiex.notification.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import ru.itmo.aiex.notification.entity.Notification

@Component
internal class LoggingNotificationSender : NotificationSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun deliver(notification: Notification) {
        log.info("Уведомление {} типа {} доставлено получателю {}", notification.id, notification.type, notification.recipientId)
    }
}
