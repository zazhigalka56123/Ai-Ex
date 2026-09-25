package ru.itmo.aiex.notification.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.common.id.Ids
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.time.nowMicros
import ru.itmo.aiex.notification.entity.Notification
import ru.itmo.aiex.notification.entity.NotificationStatus
import ru.itmo.aiex.notification.repository.NotificationRepository
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.util.UUID

@Service
@Transactional(readOnly = true)
class NotificationService(
    private val notifications: NotificationRepository,
    private val sender: NotificationSender,
    private val jsonMapper: JsonMapper,
    private val clock: Clock,
) : NotificationPort {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun send(command: NotificationCommand): UUID {
        val notification =
            notifications.insert(
                Notification(Ids.next(), command.recipientId, command.type, jsonMapper.writeValueAsString(command.payload), clock.nowMicros()),
            )
        try {
            sender.deliver(notification)
            notification.markSent(clock.nowMicros())
        } catch (ex: NotificationDeliveryException) {
            notification.markFailed()
            log.warn("Уведомление {} типа {} не доставлено получателю {}", notification.id, notification.type, notification.recipientId, ex)
        }
        return notification.id
    }

    fun list(actor: Actor, status: NotificationStatus?, page: PageQuery): PageView<Notification> = notifications.findPage(actor.userId, status, page)
}
