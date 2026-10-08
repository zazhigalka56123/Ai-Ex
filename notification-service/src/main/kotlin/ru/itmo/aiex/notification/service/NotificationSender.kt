package ru.itmo.aiex.notification.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import ru.itmo.aiex.notification.entity.Notification

fun interface NotificationSender {
    fun deliver(notification: Notification): Mono<Void>
}

@Component
class LoggingNotificationSender : NotificationSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun deliver(notification: Notification): Mono<Void> = Mono.fromRunnable {
        log.info("Уведомление {} типа {} доставлено получателю {}", notification.id, notification.type, notification.recipientId)
    }
}

class NotificationDeliveryException(message: String) : RuntimeException(message)
