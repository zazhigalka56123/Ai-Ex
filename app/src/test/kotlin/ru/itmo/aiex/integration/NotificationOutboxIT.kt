package ru.itmo.aiex.integration

import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import ru.itmo.aiex.notification.entity.NotificationType
import ru.itmo.aiex.notification.service.NotificationCommand
import ru.itmo.aiex.testing.AbstractIntegrationTest
import java.util.UUID

class NotificationOutboxIT : AbstractIntegrationTest() {
    @Autowired lateinit var outbox: NotificationOutbox

    @Autowired lateinit var dispatcher: NotificationOutboxDispatcher

    @Autowired lateinit var transactions: PlatformTransactionManager

    @Test
    fun `неудачная доставка остаётся в очереди и повторяется с тем же id`() {
        val id = outbox.sendNotification(NotificationCommand(UUID.randomUUID(), NotificationType.PERSONA_READY, emptyMap()))
        every { notificationClient.deliver(any(), any()) } throws IllegalStateException("offline")
        dispatcher.dispatch()
        assertThat(jdbcTemplate.queryForObject("SELECT attempts FROM notification_outbox WHERE id = ?", Int::class.java, id)).isEqualTo(1)
        every { notificationClient.deliver(id, any()) } returns id
        jdbcTemplate.update("UPDATE notification_outbox SET next_attempt_at = now()")
        dispatcher.dispatch()
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification_outbox", Long::class.java)).isZero()
    }

    @Test
    fun `откат бизнес транзакции откатывает уведомление`() {
        TransactionTemplate(transactions).executeWithoutResult { status ->
            outbox.sendNotification(NotificationCommand(UUID.randomUUID(), NotificationType.PERSONA_READY, emptyMap()))
            status.setRollbackOnly()
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notification_outbox", Long::class.java)).isZero()
    }
}
