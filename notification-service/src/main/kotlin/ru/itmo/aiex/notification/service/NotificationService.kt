package ru.itmo.aiex.notification.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import ru.itmo.aiex.common.id.Ids
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.time.nowMicros
import ru.itmo.aiex.notification.dto.NotificationCommand
import ru.itmo.aiex.notification.entity.Notification
import ru.itmo.aiex.notification.entity.NotificationStatus
import ru.itmo.aiex.notification.repository.NotificationRepository
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.util.UUID

@Service
class NotificationService(
    private val notifications: NotificationRepository,
    private val sender: NotificationSender,
    private val mapper: JsonMapper,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun send(command: NotificationCommand): Mono<UUID> = Mono.defer {
        val notification = Notification(
            Ids.next(),
            command.recipientId,
            command.type,
            mapper.writeValueAsString(command.payload),
            clock.nowMicros(),
            eventId = command.eventId,
        )
        notifications.insert(notification).flatMap { inserted ->
            if (inserted) deliver(notification) else notifications.findByEventId(command.eventId).map { it.id }
        }
    }

    private fun deliver(notification: Notification): Mono<UUID> = sender.deliver(notification)
        .thenReturn(NotificationStatus.SENT)
        .onErrorResume(NotificationDeliveryException::class.java) { error ->
            log.warn("Уведомление {} не доставлено получателю {}", notification.id, notification.recipientId, error)
            Mono.just(NotificationStatus.FAILED)
        }
        .flatMap { status ->
            notifications.recordDelivery(notification.id, status, if (status == NotificationStatus.SENT) clock.nowMicros() else null)
                .thenReturn(notification.id)
        }

    fun list(recipientId: UUID, status: NotificationStatus?, page: PageQuery): Mono<PageView<Notification>> =
        notifications.findPage(recipientId, status, page)
}
