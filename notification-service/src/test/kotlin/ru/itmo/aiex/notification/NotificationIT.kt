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
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer
import reactor.test.StepVerifier
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.dto.UserView
import ru.itmo.aiex.notification.entity.NotificationType
import ru.itmo.aiex.notification.repository.NotificationRepository
import ru.itmo.aiex.notification.service.IdentityClient
import ru.itmo.aiex.notification.service.NotificationCommand
import ru.itmo.aiex.notification.service.NotificationService
import ru.itmo.aiex.security.JwtTokens
import java.util.UUID

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "ai-ex.security.jwt-secret=test-only-jwt-secret-at-least-32-bytes",
        "spring.config.import=", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml",
    ],
)
@AutoConfigureWebTestClient
class NotificationIT {
    @Autowired lateinit var database: DatabaseClient

    @Autowired lateinit var service: NotificationService

    @Autowired lateinit var repository: NotificationRepository

    @Autowired lateinit var tokens: JwtTokens

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
        StepVerifier.create(service.deliver(id, command).then(service.deliver(id, command)).then(repository.countByRecipient(owner)))
            .expectNext(1L).verifyComplete()
        StepVerifier.create(repository.findById(id)).assertNext {
            assertThat(it.recipientId).isEqualTo(owner)
            assertThat(it.createdAt).isNotNull()
        }.verifyComplete()
    }

    @Test
    fun `HTTP выдаёт только свои уведомления и проверяет авторизацию`() {
        val owner = UUID.randomUUID()
        val other = UUID.randomUUID()
        val missing = UUID.randomUUID()
        every { users.findActive(owner, any()) } returns UserView(owner, "User", setOf(RoleCode.USER), true)
        every { users.findActive(other, any()) } returns UserView(other, "Other", setOf(RoleCode.USER), true)
        every { users.findActive(missing, any()) } returns null
        service.deliver(UUID.randomUUID(), NotificationCommand(owner, NotificationType.PERSONA_READY, mapOf("versionNo" to 2))).block()
        service.deliver(UUID.randomUUID(), NotificationCommand(other, NotificationType.IMPORT_PARSED, emptyMap())).block()
        http.get().uri("/api/v1/notifications").header("Authorization", "Bearer ${tokens.userToken(owner, setOf(RoleCode.USER))}").exchange()
            .expectStatus().isOk.expectHeader().valueEquals("X-Total-Count", "1")
            .expectBody().jsonPath("$[0].payload.versionNo").isEqualTo(2)
            .jsonPath("$[0].status").doesNotExist()
            .jsonPath("$[0].attempts").doesNotExist()
            .jsonPath("$[0].sentAt").doesNotExist()
        StepVerifier.create(service.collectMetrics())
            .assertNext { assertThat(it).containsExactlyEntriesOf(mapOf("notifications.sent" to 2L)) }.verifyComplete()
        http.get().uri("/api/v1/notifications").header("X-Trace-Id", "request-12345")
            .exchange().expectStatus().isUnauthorized
            .expectHeader().contentType("application/problem+json")
            .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
            .expectBody().jsonPath("$.traceId").isEqualTo("request-12345")
            .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
            .jsonPath("$.instance").isEqualTo("/api/v1/notifications")
        http.get().uri("/api/v1/notifications").header("X-User-Id", owner.toString())
            .exchange().expectStatus().isUnauthorized
        http.get().uri("/internal/metrics").header("Authorization", "Bearer ${tokens.userToken(owner, setOf(RoleCode.USER))}")
            .exchange().expectStatus().isForbidden
        http.get().uri("/api/v1/notifications").header("Authorization", "Bearer ${tokens.serviceToken()}")
            .exchange().expectStatus().isForbidden
        http.get().uri("/api/v1/notifications").header("Authorization", "Bearer ${tokens.userToken(other, setOf(RoleCode.USER))}").exchange()
            .expectStatus().isOk.expectHeader().valueEquals("X-Total-Count", "1")
            .expectBody().jsonPath("$[0].type").isEqualTo("IMPORT_PARSED")
        http.get().uri(
            "/api/v1/notifications",
        ).header("Authorization", "Bearer ${tokens.userToken(missing, setOf(RoleCode.USER))}").exchange().expectStatus().isUnauthorized
        http.get().uri(
            "/api/v1/notifications?size=0",
        ).header("Authorization", "Bearer ${tokens.userToken(owner, setOf(RoleCode.USER))}").exchange().expectStatus().isBadRequest
    }

    @Test
    fun `пагинация стабильна при одинаковом времени и сохраняет JSON массив`() {
        val owner = UUID.randomUUID()
        val first = UUID(0, 1)
        val second = UUID(0, 2)
        every { users.findActive(owner, any()) } returns UserView(owner, "User", setOf(RoleCode.USER), true)
        val command = NotificationCommand(owner, NotificationType.PERSONA_READY, emptyMap())
        service.deliver(first, command).then(service.deliver(second, command)).block()
        database.sql("UPDATE notifications SET created_at = '2026-01-01T00:00:00Z'").fetch().rowsUpdated().block()

        for ((sort, ids) in listOf("asc" to listOf(first, second), "desc" to listOf(second, first))) {
            ids.forEachIndexed { page, id ->
                http.get().uri("/api/v1/notifications?size=1&page=$page&sort=createdAt,$sort")
                    .header("Authorization", "Bearer ${tokens.userToken(owner, setOf(RoleCode.USER))}").exchange()
                    .expectStatus().isOk.expectHeader().valueEquals("X-Total-Count", "2")
                    .expectHeader().valueEquals("X-Total-Pages", "2")
                    .expectBody().jsonPath("$.length()").isEqualTo(1)
                    .jsonPath("$[0].id").isEqualTo(id.toString())
            }
        }
        http.get().uri(
            "/api/v1/notifications?page=2&size=1",
        ).header("Authorization", "Bearer ${tokens.userToken(owner, setOf(RoleCode.USER))}").exchange()
            .expectStatus().isOk.expectHeader().valueEquals("X-Total-Count", "2").expectBody().json("[]")
    }

    @Test
    fun `OpenAPI различает массив уведомлений и ошибку`() {
        http.get().uri("/v3/api-docs").exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.paths['/api/v1/notifications'].get.responses['200'].content['application/json'].schema.type").isEqualTo("array")
            .jsonPath("$.paths['/api/v1/notifications'].get.responses['503'].content['application/problem+json'].schema['\$ref']")
            .isEqualTo("#/components/schemas/ProblemDetail")
            .jsonPath("$.components.schemas.NotificationResponse.properties.id.format").isEqualTo("uuid")
            .jsonPath("$.components.schemas.NotificationResponse.properties.status").doesNotExist()
            .jsonPath("$.components.schemas.NotificationResponse.properties.attempts").doesNotExist()
            .jsonPath("$.components.schemas.NotificationResponse.properties.sentAt").doesNotExist()
    }

    companion object {
        val postgres = PostgreSQLContainer("postgres:17-alpine").waitingFor(Wait.forListeningPort()).apply { start() }

        @JvmStatic @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") { "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}" }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.liquibase.url", postgres::getJdbcUrl)
            registry.add("spring.liquibase.user", postgres::getUsername)
            registry.add("spring.liquibase.password", postgres::getPassword)
        }
    }
}
