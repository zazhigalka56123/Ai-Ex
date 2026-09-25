package ru.itmo.aiex.notification.repository

import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.notification.entity.Notification
import ru.itmo.aiex.notification.entity.NotificationStatus
import java.util.UUID

interface NotificationRepository {
    fun insert(notification: Notification): Notification

    fun findPage(recipientId: UUID, status: NotificationStatus?, page: PageQuery): PageView<Notification>

    fun countByStatus(status: NotificationStatus): Long
}
