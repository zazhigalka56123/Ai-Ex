package ru.itmo.aiex.notification.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.notification.domain.Notification
import ru.itmo.aiex.notification.domain.NotificationStatus
import ru.itmo.aiex.notification.domain.NotificationType
import ru.itmo.aiex.notification.domain.port.NotificationDeliveryException
import ru.itmo.aiex.notification.domain.port.NotificationRepository
import ru.itmo.aiex.notification.domain.port.NotificationSender
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class NotificationServiceTest {
    private val now = Instant.parse("2026-09-22T03:00:00Z")
    private val stored = slot<Notification>()
    private val repository =
        mockk<NotificationRepository> {
            every { insert(capture(stored)) } answers { firstArg() }
            every { findPage(any(), any(), any()) } returns PageView(emptyList(), 0, 20, 0)
        }
    private val sender = mockk<NotificationSender>()
    private val service = NotificationService(repository, sender, JsonMapper.builder().build(), Clock.fixed(now, ZoneOffset.UTC))
    private val recipient = UUID.randomUUID()
    private val personaId = UUID.randomUUID()

    @Test
    fun `доставка удалась - SENT, одна попытка, payload сериализован в JSON`() {
        every { sender.deliver(any()) } returns Unit

        val id = service.send(NotificationCommand(recipient, NotificationType.PERSONA_READY, mapOf("personaId" to personaId, "versionNo" to 2)))

        with(stored.captured) {
            assertThat(this.id).isEqualTo(id)
            assertThat(recipientId).isEqualTo(recipient)
            assertThat(status).isEqualTo(NotificationStatus.SENT)
            assertThat(attempts).isEqualTo(1)
            assertThat(sentAt).isEqualTo(now)
            assertThat(payload).isEqualTo("""{"personaId":"$personaId","versionNo":2}""")
        }
    }

    @Test
    fun `сбой канала доставки - FAILED, попытка учтена, исключение не пробрасывается`() {
        every { sender.deliver(any()) } throws NotificationDeliveryException("канал недоступен")

        service.send(NotificationCommand(recipient, NotificationType.IMPORT_FAILED, mapOf("errorCode" to "BROKEN_FILE")))

        with(stored.captured) {
            assertThat(status).isEqualTo(NotificationStatus.FAILED)
            assertThat(attempts).isEqualTo(1)
            assertThat(sentAt).isNull()
        }
    }

    @Test
    fun `новое уведомление - PENDING без попыток`() {
        val notification = Notification(UUID.randomUUID(), recipient, NotificationType.FLAG_RESOLVED, "{}", now)
        assertThat(notification.status).isEqualTo(NotificationStatus.PENDING)
        assertThat(notification.attempts).isZero()
        val other = Notification(UUID.randomUUID(), recipient, NotificationType.FLAG_RESOLVED, "{}", now)
        assertThat(notification).isEqualTo(notification).isNotEqualTo(other)
    }

    @Test
    fun `список запрашивается только для текущего пользователя`() {
        val actor = Actor(recipient, emptySet())
        assertThat(service.list(actor, NotificationStatus.SENT, PageQuery(0, 20)).items).isEmpty()
        verify { repository.findPage(recipient, NotificationStatus.SENT, PageQuery(0, 20)) }
    }
}
