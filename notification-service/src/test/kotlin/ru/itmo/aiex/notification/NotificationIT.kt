package ru.itmo.aiex.notification

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.postgresql.PostgreSQLContainer
import reactor.test.StepVerifier
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.dto.UserView
import ru.itmo.aiex.notification.entity.NotificationType
import ru.itmo.aiex.notification.repository.NotificationRepository
import ru.itmo.aiex.notification.service.IdentityClient
import ru.itmo.aiex.notification.service.NotificationCommand
import ru.itmo.aiex.notification.service.NotificationService
import java.util.UUID

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.config.import=", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false", "spring.sql.init.mode=always",
    ],
)
@AutoConfigureWebTestClient
class NotificationIT {
    @Autowired lateinit var database: DatabaseClient

    @Autowired lateinit var service: NotificationService

    @Autowired lateinit var repository: NotificationRepository

    @Autowired lateinit var http: WebTestClient

    @MockkBean lateinit var users: IdentityClient

    @BeforeEach
    fun clean() {
        database.sql("DELETE FROM notifications").fetch().rowsUpdated().block()
    }

    @Test
    fun `повтор доставки не создаёт дубликат`() {
        val id = UUID.randomUUID()
        val owner = UUID.randomUUID()
        val command = NotificationCommand(owner, NotificationType.PERSONA_READY, mapOf("versionNo" to 2))
        StepVerifier.create(service.deliver(id, command).then(service.deliver(id, command)).then(repository.count(owner, null)))
            .expectNext(1L).verifyComplete()
    }

    @Test
    fun `HTTP выдаёт только свои уведомления и проверяет авторизацию`() {
        val owner = UUID.randomUUID()
        val other = UUID.randomUUID()
        every { users.findActive(owner) } returns UserView(owner, "User", setOf(RoleCode.USER), true)
        every { users.findActive(other) } returns null
        service.deliver(UUID.randomUUID(), NotificationCommand(owner, NotificationType.PERSONA_READY, mapOf("versionNo" to 2))).block()
        service.deliver(UUID.randomUUID(), NotificationCommand(other, NotificationType.IMPORT_PARSED, emptyMap())).block()
        http.get().uri("/api/v1/notifications").header("X-User-Id", owner.toString()).exchange()
            .expectStatus().isOk.expectHeader().valueEquals("X-Total-Count", "1")
            .expectBody().jsonPath("$[0].payload.versionNo").isEqualTo(2)
        http.get().uri("/api/v1/notifications").exchange().expectStatus().isUnauthorized
        http.get().uri("/api/v1/notifications").header("X-User-Id", other.toString()).exchange().expectStatus().isUnauthorized
        http.get().uri("/api/v1/notifications?size=0").header("X-User-Id", owner.toString()).exchange().expectStatus().isBadRequest
    }

    companion object {
        val postgres = PostgreSQLContainer("postgres:17-alpine").apply { start() }

        @JvmStatic @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") { "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}" }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
        }
    }
}
