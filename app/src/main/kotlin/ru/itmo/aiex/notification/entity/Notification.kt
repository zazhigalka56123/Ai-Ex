package ru.itmo.aiex.notification.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "notifications", schema = "notification")
class Notification(
    @Id
    val id: UUID,
    @Column(name = "recipient_id", nullable = false, updatable = false)
    val recipientId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48, updatable = false)
    val type: NotificationType,
    @field:NotBlank
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    val payload: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    var status: NotificationStatus = NotificationStatus.PENDING
        protected set

    @field:PositiveOrZero
    @Column(nullable = false)
    var attempts: Int = 0
        protected set

    @Column(name = "sent_at")
    var sentAt: Instant? = null
        protected set

    fun markSent(now: Instant) {
        attempts++
        status = NotificationStatus.SENT
        sentAt = now
    }

    fun markFailed() {
        attempts++
        status = NotificationStatus.FAILED
    }

    override fun equals(other: Any?): Boolean = this === other || (other is Notification && other.id == id)

    override fun hashCode(): Int = id.hashCode()
}
