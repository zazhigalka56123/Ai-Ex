package ru.itmo.aiex.notification.repository

import jakarta.persistence.EntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.notification.entity.Notification
import ru.itmo.aiex.notification.entity.NotificationStatus

import ru.itmo.aiex.common.persistence.toPageView
import ru.itmo.aiex.common.persistence.toPageable
import java.util.UUID

@Repository
internal class NotificationRepositoryAdapter(private val jpa: NotificationJpaRepository, private val entityManager: EntityManager) :
    NotificationRepository {
    override fun insert(notification: Notification): Notification {
        entityManager.persist(notification)
        return notification
    }

    override fun findPage(recipientId: UUID, status: NotificationStatus?, page: PageQuery): PageView<Notification> {
        val requested = page.toPageable()
        val pageable = PageRequest.of(requested.pageNumber, requested.pageSize, requested.sort.and(Sort.by(Sort.Direction.DESC, "id")))
        val result =
            if (status == null) jpa.findAllByRecipientId(recipientId, pageable) else jpa.findAllByRecipientIdAndStatus(recipientId, status, pageable)
        return result.toPageView()
    }

    override fun countByStatus(status: NotificationStatus): Long = jpa.countByStatus(status)
}
