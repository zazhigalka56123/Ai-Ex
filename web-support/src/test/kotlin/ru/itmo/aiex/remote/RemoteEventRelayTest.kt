package ru.itmo.aiex.remote

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import ru.itmo.aiex.common.events.ChatImportParsed
import ru.itmo.aiex.common.events.PersonaArchived
import java.time.Instant
import java.util.UUID

class RemoteEventRelayTest {
    @Test
    fun `сбой архивации бесед не отменяет независимое уведомление`() {
        val notifications = mockk<NotificationClient>()
        val dialogs = mockk<DialogClient>()
        val event = PersonaArchived(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false, Instant.now())
        every { dialogs.personaArchived(event) } throws IllegalStateException("Dialog unavailable")
        every { notifications.send(any()) } returns UUID.randomUUID()
        val relay = RemoteEventRelay(notifications, dialogs, mockk())

        assertThatThrownBy { relay.on(event) }.isInstanceOf(IllegalStateException::class.java).hasMessage("Dialog unavailable")

        verify(exactly = 1) {
            notifications.send(
                NotificationDelivery(
                    event.eventId,
                    event.ownerId,
                    "PERSONA_ARCHIVED",
                    mapOf("personaId" to event.personaId, "byAdmin" to false),
                ),
            )
        }
    }

    @Test
    fun `уведомление отправляется только после успешного commit с идентификатором события`() {
        val notifications = mockk<NotificationClient>()
        val dialogs = mockk<DialogClient>(relaxed = true)
        val care = mockk<CareClient>(relaxed = true)
        val event = ChatImportParsed(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 12, Instant.now())
        val delivery = NotificationDelivery(
            event.eventId,
            event.ownerId,
            "IMPORT_PARSED",
            mapOf("importId" to event.importId, "personaId" to event.personaId, "messageCount" to 12),
        )
        every { notifications.send(delivery) } returns UUID.randomUUID()

        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles("microservice")
            context.register(TransactionConfiguration::class.java)
            context.beanFactory.registerSingleton("notificationClient", notifications)
            context.beanFactory.registerSingleton("dialogClient", dialogs)
            context.beanFactory.registerSingleton("careClient", care)
            context.register(RemoteEventRelay::class.java)
            context.refresh()
            val transactions = TransactionTemplate(context.getBean(TestTransactions::class.java))

            transactions.executeWithoutResult { status ->
                context.publishEvent(event)
                verify(exactly = 0) { notifications.send(any()) }
                status.setRollbackOnly()
            }
            verify(exactly = 0) { notifications.send(any()) }

            transactions.executeWithoutResult {
                context.publishEvent(event)
                verify(exactly = 0) { notifications.send(any()) }
            }
            verify(exactly = 1) { notifications.send(delivery) }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    class TransactionConfiguration {
        @Bean
        fun transactionManager(): TestTransactions = TestTransactions()
    }

    class TestTransactions : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()

        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit

        override fun doCommit(status: DefaultTransactionStatus) = Unit

        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }
}
