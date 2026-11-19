package ru.itmo.aiex.notification

import feign.FeignException
import feign.Request
import feign.RequestTemplate
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException
import org.springframework.http.HttpMethod
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.server.MethodNotAllowedException
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.dto.UserView
import ru.itmo.aiex.notification.controller.NotificationController
import ru.itmo.aiex.notification.controller.NotificationExceptionHandler
import ru.itmo.aiex.notification.service.IdentityClient
import ru.itmo.aiex.notification.service.NotificationService
import ru.itmo.aiex.notification.web.TraceIdWebFilter
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

class NotificationHttpTest {
    private val service = mockk<NotificationService>()
    private val users = mockk<IdentityClient>()
    private val http = WebTestClient.bindToController(
        NotificationController(service, users, JsonMapper.builder().build()),
    ).controllerAdvice(NotificationExceptionHandler())
        .webFilter<WebTestClient.ControllerSpec>(TraceIdWebFilter()).build()

    @Test
    fun `реактивный ответ сохраняет заголовки пагинации и trace id`() {
        val owner = UUID.randomUUID()
        every { users.findActive(owner, any()) } returns UserView(owner, "User", setOf(RoleCode.USER), true)
        every { service.findNotifications(owner, 5, 10L, false) } returns Flux.empty()
        every { service.countNotifications(owner) } returns Mono.just(10L)

        http.get().uri("/api/v1/notifications?page=2&size=5")
            .header("X-User-Id", owner.toString()).header("X-Trace-Id", "request-12345")
            .exchange().expectStatus().isOk
            .expectHeader().valueEquals("X-Trace-Id", "request-12345")
            .expectHeader().valueEquals("X-Total-Count", "10")
            .expectHeader().valueEquals("X-Total-Pages", "2")
            .expectHeader().valueEquals("X-Page", "2")
            .expectHeader().valueEquals("X-Page-Size", "5")
            .expectBody().json("[]")
        verify { users.findActive(owner, "request-12345") }
    }

    @Test
    fun `ошибки также содержат сгенерированный trace id`() {
        for (traceId in listOf("", "invalid!")) {
            http.get().uri("/api/v1/notifications").header("X-Trace-Id", traceId)
                .exchange().expectStatus().isUnauthorized
                .expectHeader().valueMatches("X-Trace-Id", "[A-Za-z0-9-]{8,64}")
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.traceId").value<String> { assertThat(it).matches("[A-Za-z0-9-]{8,64}") }
        }
    }

    @Test
    fun `ошибочный UUID и пагинация возвращают problem detail`() {
        http.get().uri("/api/v1/notifications").header("X-User-Id", "invalid")
            .header("X-Trace-Id", "request-12345")
            .exchange().expectStatus().isBadRequest
            .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
            .jsonPath("$.traceId").isEqualTo("request-12345")
        http.get().uri("/api/v1/notifications?size=51").header("X-User-Id", UUID.randomUUID().toString())
            .exchange().expectStatus().isBadRequest
            .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
    }

    @Test
    fun `ответ 405 сохраняет заголовок Allow`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/notifications"))
        val problem = NotificationExceptionHandler().handle(MethodNotAllowedException("POST", listOf(HttpMethod.GET)), exchange)
        assertThat(problem.status).isEqualTo(405)
        assertThat(problem.properties).containsEntry("code", "METHOD_NOT_ALLOWED")
        assertThat(exchange.response.headers.allow).contains(HttpMethod.GET)
    }

    @Test
    fun `ошибки IAM сохраняют 4xx и скрывают внутренние детали`() {
        val owner = UUID.randomUUID()
        val request = Request.create(
            Request.HttpMethod.GET,
            "http://iam-service/internal/users/$owner",
            emptyMap(),
            null,
            Charsets.UTF_8,
            RequestTemplate(),
        )
        val error = FeignException.Forbidden("internal-secret", request, byteArrayOf(), emptyMap())
        every { users.findActive(owner, any()) } throws error
        http.get().uri("/api/v1/notifications").header("X-User-Id", owner.toString())
            .header("X-Trace-Id", "request-12345")
            .exchange().expectStatus().isForbidden
            .expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN")
            .jsonPath("$.traceId").isEqualTo("request-12345")
            .jsonPath("$.detail").isEqualTo("Недостаточно прав")
    }

    @Test
    fun `отказ зависимости возвращает 503 с trace id`() {
        val owner = UUID.randomUUID()
        every { users.findActive(owner, any()) } throws NoFallbackAvailableException(
            "IAM offline",
            IllegalStateException("internal-secret"),
        )
        http.get().uri("/api/v1/notifications").header("X-User-Id", owner.toString())
            .header("X-Trace-Id", "request-12345")
            .exchange().expectStatus().isEqualTo(503)
            .expectHeader().contentType("application/problem+json")
            .expectBody().jsonPath("$.code").isEqualTo("SERVICE_UNAVAILABLE")
            .jsonPath("$.traceId").isEqualTo("request-12345")
            .jsonPath("$.detail").isEqualTo("Зависимый сервис временно недоступен")
    }

    @Test
    fun `trace id доступен в Reactor context и атрибутах запроса`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/notifications").header("X-Trace-Id", "request-12345"),
        )
        val chain = WebFilterChain { current ->
            Mono.deferContextual { context ->
                assertThat(context.get<String>("traceId")).isEqualTo("request-12345")
                assertThat(current.getAttribute<String>("traceId")).isEqualTo("request-12345")
                Mono.empty()
            }
        }
        StepVerifier.create(TraceIdWebFilter().filter(exchange, chain)).verifyComplete()
    }
}
