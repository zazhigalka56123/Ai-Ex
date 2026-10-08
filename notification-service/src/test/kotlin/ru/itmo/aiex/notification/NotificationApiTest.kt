package ru.itmo.aiex.notification

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.test.StepVerifier
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.iam.dto.UserView
import ru.itmo.aiex.notification.client.NotificationUserClient
import ru.itmo.aiex.notification.dto.NotificationCommand
import ru.itmo.aiex.notification.entity.NotificationStatus
import ru.itmo.aiex.notification.entity.NotificationType
import ru.itmo.aiex.notification.repository.NotificationRepository
import ru.itmo.aiex.notification.service.NotificationDeliveryException
import ru.itmo.aiex.notification.service.NotificationSender
import ru.itmo.aiex.notification.service.NotificationService
import ru.itmo.bootstrap.notification.NotificationApplication
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@SpringBootTest(
    classes = [NotificationApplication::class],
    properties = [
        "spring.config.name=notification-test",
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.r2dbc.url=r2dbc:h2:mem:///notifications;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.sql.init.mode=always",
    ],
)
class NotificationApiTest {
    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var database: DatabaseClient

    @Autowired
    private lateinit var repository: NotificationRepository

    @Autowired
    private lateinit var mapper: JsonMapper

    @MockkBean
    private lateinit var users: NotificationUserClient

    private lateinit var client: WebTestClient
    private val me = UUID.randomUUID()
    private val other = UUID.randomUUID()

    @BeforeEach
    fun prepare() {
        database.sql("DELETE FROM notifications").fetch().rowsUpdated().block()
        every { users.findActive(me) } returns UserView(me, "Маша", emptySet(), true)
        every { users.findActive(other) } returns UserView(other, "Аня", emptySet(), true)
        client = WebTestClient.bindToApplicationContext(context).build()
    }

    @Test
    fun `повтор события возвращает прежний id без повторной доставки`() {
        val command = NotificationCommand(me, NotificationType.PERSONA_READY, mapOf("versionNo" to 2), UUID.randomUUID())
        val first = send(command)
        val second = send(command)
        assertThat(second).isEqualTo(first)

        client.get().uri("/api/v1/notifications").header("X-User-Id", me.toString()).exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("X-Total-Count", "1")
            .expectBody()
            .jsonPath("$[0].id").isEqualTo(first.toString())
            .jsonPath("$[0].payload.versionNo").isEqualTo(2)
            .jsonPath("$[0].status").isEqualTo("SENT")
            .jsonPath("$[0].attempts").isEqualTo(1)
    }

    @Test
    fun `пагинация и фильтр выбирают только уведомления текущего пользователя`() {
        repeat(3) { index -> send(NotificationCommand(me, NotificationType.IMPORT_PARSED, mapOf("messageCount" to index), UUID.randomUUID())) }
        send(NotificationCommand(other, NotificationType.FLAG_RESOLVED, emptyMap(), UUID.randomUUID()))

        client.get().uri("/api/v1/notifications?size=2").header("X-User-Id", me.toString()).exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("X-Total-Count", "3")
            .expectHeader().valueEquals("X-Total-Pages", "2")
            .expectHeader().valueEquals("X-Page-Size", "2")
            .expectHeader().exists("Link")
            .expectBody().jsonPath("$.length()").isEqualTo(2)

        client.get().uri("/api/v1/notifications?status=FAILED").header("X-User-Id", me.toString()).exchange()
            .expectStatus().isOk.expectHeader().valueEquals("X-Total-Count", "0")

        client.get().uri("/api/v1/notifications").header("X-User-Id", other.toString()).exchange()
            .expectStatus().isOk.expectHeader().valueEquals("X-Total-Count", "1")
    }

    @Test
    fun `некорректные параметры и неизвестный пользователь отклоняются`() {
        client.get().uri("/api/v1/notifications").exchange().expectStatus().isUnauthorized
        client.get().uri("/api/v1/notifications").header("X-User-Id", "broken").exchange().expectStatus().isUnauthorized
        listOf("size=51", "page=-1", "sort=type,asc", "status=UNKNOWN").forEach { query ->
            client.get().uri("/api/v1/notifications?$query").header("X-User-Id", me.toString()).exchange().expectStatus().isBadRequest
        }
        every { users.findActive(me) } returns null
        client.get().uri("/api/v1/notifications").header("X-User-Id", me.toString()).exchange().expectStatus().isUnauthorized
        every { users.findActive(me) } throws IllegalStateException("account unavailable")
        client.get().uri("/api/v1/notifications").header("X-User-Id", me.toString()).exchange().expectStatus().isEqualTo(503)
    }

    @Test
    fun `внутренний endpoint требует токен`() {
        client.post().uri("/internal/notifications")
            .bodyValue(NotificationCommand(me, NotificationType.PERSONA_ARCHIVED, emptyMap(), UUID.randomUUID()))
            .exchange().expectStatus().isForbidden
    }

    @Test
    fun `сбой доставки сохраняет FAILED и учитывает попытку`() {
        val clock = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC)
        val sender = NotificationSender { reactor.core.publisher.Mono.error(NotificationDeliveryException("Канал недоступен")) }
        val service = NotificationService(repository, sender, mapper, clock)
        val command = NotificationCommand(me, NotificationType.IMPORT_FAILED, mapOf("errorCode" to "INVALID_JSON"), UUID.randomUUID())
        val result = service.sendNotification(command)
            .flatMap { service.list(me, NotificationStatus.FAILED, PageQuery(0, 20)) }
        StepVerifier.create(result).assertNext { page ->
            assertThat(page.totalElements).isEqualTo(1)
            assertThat(page.items.single().attempts).isEqualTo(1)
            assertThat(page.items.single().sentAt).isNull()
        }.verifyComplete()
        StepVerifier.create(repository.countByStatus()).assertNext { metrics ->
            assertThat(metrics).containsEntry("notifications.failed", 1L).containsEntry("notifications.sent", 0L)
        }.verifyComplete()
    }

    private fun send(command: NotificationCommand): UUID = client.post().uri("/internal/notifications")
        .header("X-Internal-Token", "ai-ex-local-token")
        .bodyValue(command)
        .exchange().expectStatus().isOk
        .expectBody(UUID::class.java).returnResult().responseBody!!
}
