package ru.itmo.aiex.iam

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import reactor.test.StepVerifier
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.dto.UserResponse
import ru.itmo.aiex.iam.service.JpaScheduler
import ru.itmo.aiex.iam.service.ReactiveUserService
import ru.itmo.aiex.iam.service.RegisterUserCommand
import ru.itmo.bootstrap.account.AccountServiceApplication
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@Testcontainers
@SpringBootTest(
    classes = [AccountServiceApplication::class],
    properties = [
        "spring.config.name=account-test",
        "spring.profiles.active=microservice",
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.liquibase.change-log=classpath:db/changelog/account-service.yaml",
        "spring.liquibase.contexts=test",
        "spring.jpa.hibernate.ddl-auto=validate",
    ],
)
class ReactiveUserIT {
    @Autowired
    private lateinit var context: ApplicationContext

    @Autowired
    private lateinit var service: ReactiveUserService

    private lateinit var client: WebTestClient

    @BeforeEach
    fun prepare() {
        client = WebTestClient.bindToApplicationContext(context).build()
    }

    @Test
    fun `сервис на WebFlux, а JPA выполняется после подписки в отдельном пуле`() {
        assertThat(context.containsBean("dispatcherServlet")).isFalse()
        val thread = AtomicReference<String>()
        val creation = service.registerUser(RegisterUserCommand(email(), "Маша", emptySet())).doOnNext { thread.set(Thread.currentThread().name) }
        assertThat(thread.get()).isNull()

        StepVerifier.create(creation).assertNext { assertThat(it.displayName).isEqualTo("Маша") }.verifyComplete()
        assertThat(thread.get()).startsWith("${JpaScheduler.THREAD_PREFIX}-")
    }

    @Test
    fun `регистрация отдаёт 201, повтор email - 409, невалидное тело - 400`() {
        val email = email()
        client.post().uri("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to email, "displayName" to "Маша"))
            .exchange()
            .expectStatus().isCreated
            .expectHeader().exists("Location")
            .expectBody().jsonPath("$.roles[0]").isEqualTo("USER")

        client.post().uri("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to email, "displayName" to "Маша"))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.code").isEqualTo(ErrorCode.EMAIL_TAKEN.name)

        client.post().uri("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("email" to "not-an-email", "displayName" to ""))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo(ErrorCode.VALIDATION_FAILED.name)
            .jsonPath("$.errors[?(@.field == 'email')]").exists()
            .jsonPath("$.errors[?(@.field == 'displayName')]").exists()

        client.post().uri("/api/v1/users").contentType(MediaType.APPLICATION_JSON).bodyValue("{\"email\":")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `пользователь видит только себя, администратор - всех со страницами в заголовках`() {
        val admin = register(setOf(RoleCode.ADMIN, RoleCode.USER))
        val user = register()

        client.get().uri("/api/v1/users/${user.id}").exchange().expectStatus().isUnauthorized
        client.get().uri("/api/v1/users/${user.id}").header("X-User-Id", "broken").exchange().expectStatus().isUnauthorized
        client.get().uri("/api/v1/users/${user.id}").header("X-User-Id", user.id.toString()).exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.email").isEqualTo(user.email)
        client.get().uri("/api/v1/users/${admin.id}").header("X-User-Id", user.id.toString()).exchange().expectStatus().isNotFound

        client.get().uri("/api/v1/users?size=1").header("X-User-Id", user.id.toString()).exchange().expectStatus().isForbidden
        client.get().uri("/api/v1/users?size=1&sort=email,asc").header("X-User-Id", admin.id.toString()).exchange()
            .expectStatus().isOk
            .expectHeader().exists("X-Total-Count")
            .expectHeader().valueEquals("X-Page-Size", "1")
            .expectHeader().exists("Link")
            .expectBody().jsonPath("$.length()").isEqualTo(1)
        listOf("size=51", "page=-1", "sort=status,asc", "status=UNKNOWN").forEach { query ->
            client.get().uri("/api/v1/users?$query").header("X-User-Id", admin.id.toString()).exchange().expectStatus().isBadRequest
        }
    }

    @Test
    fun `имя меняет сам пользователь, статус - только администратор`() {
        val admin = register(setOf(RoleCode.ADMIN, RoleCode.USER))
        val user = register()

        client.patch().uri("/api/v1/users/${user.id}").header("X-User-Id", user.id.toString())
            .contentType(MediaType.APPLICATION_JSON).bodyValue(mapOf("displayName" to "Мария"))
            .exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.displayName").isEqualTo("Мария")
        client.patch().uri("/api/v1/users/${user.id}").header("X-User-Id", user.id.toString())
            .contentType(MediaType.APPLICATION_JSON).bodyValue(mapOf("status" to "BLOCKED"))
            .exchange()
            .expectStatus().isForbidden
        client.patch().uri("/api/v1/users/${user.id}").header("X-User-Id", admin.id.toString())
            .contentType(MediaType.APPLICATION_JSON).bodyValue(mapOf("status" to "BLOCKED"))
            .exchange()
            .expectStatus().isOk

        client.get().uri("/api/v1/users/${user.id}").header("X-User-Id", user.id.toString()).exchange().expectStatus().isUnauthorized
    }

    @Test
    fun `внутренний API требует токен и отдаёт актора и метрики`() {
        val user = register()

        client.get().uri("/internal/users/${user.id}").exchange().expectStatus().isForbidden
        client.get().uri("/internal/users/${user.id}").header("X-Internal-Token", TOKEN).exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.active").isEqualTo(true)
        client.get().uri("/internal/users/${UUID.randomUUID()}").header("X-Internal-Token", TOKEN).exchange().expectStatus().isNoContent
        client.get().uri("/internal/metrics").header("X-Internal-Token", TOKEN).exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$['users.active']").exists()
    }

    @Test
    fun `ошибка бизнес-правила приходит в Mono, а не бросается при сборке цепочки`() {
        val user = register()
        val other = register()
        val lookup = service.getUser(Actor(user.id, setOf(RoleCode.USER)), other.id)

        StepVerifier.create(lookup)
            .expectErrorSatisfies { assertThat((it as AiExException).code).isEqualTo(ErrorCode.USER_NOT_FOUND) }
            .verify()
    }

    private fun register(roles: Set<RoleCode> = emptySet()): UserResponse =
        requireNotNull(service.registerUser(RegisterUserCommand(email(), "Тест", roles)).block())

    private fun email() = "${UUID.randomUUID()}@test.local"

    companion object {
        private const val TOKEN = "ai-ex-local-token"

        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:17-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
