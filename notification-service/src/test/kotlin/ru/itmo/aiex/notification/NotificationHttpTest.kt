package ru.itmo.aiex.notification

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.dto.UserView
import ru.itmo.aiex.notification.controller.NotificationController
import ru.itmo.aiex.notification.repository.NotificationRepository
import ru.itmo.aiex.notification.service.IdentityClient
import ru.itmo.aiex.notification.web.TraceIdWebFilter
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

class NotificationHttpTest {
    private val repository = mockk<NotificationRepository>()
    private val users = mockk<IdentityClient>()
    private val http = WebTestClient.bindToController(
        NotificationController(repository, mockk(), users, JsonMapper.builder().build()),
    ).webFilter<WebTestClient.ControllerSpec>(TraceIdWebFilter()).build()

    @Test
    fun `реактивный ответ сохраняет заголовки пагинации и trace id`() {
        val owner = UUID.randomUUID()
        every { users.findActive(owner) } returns UserView(owner, "User", setOf(RoleCode.USER), true)
        every { repository.page(owner, null, 5, 10L) } returns Flux.empty()
        every { repository.count(owner, null) } returns Mono.just(10L)

        http.get().uri("/api/v1/notifications?page=2&size=5")
            .header("X-User-Id", owner.toString()).header("X-Trace-Id", "request-12345")
            .exchange().expectStatus().isOk
            .expectHeader().valueEquals("X-Trace-Id", "request-12345")
            .expectHeader().valueEquals("X-Total-Count", "10")
            .expectHeader().valueEquals("X-Total-Pages", "2")
            .expectHeader().valueEquals("X-Page", "2")
            .expectHeader().valueEquals("X-Page-Size", "5")
            .expectBody().json("[]")
    }

    @Test
    fun `ошибки также содержат сгенерированный trace id`() {
        for (traceId in listOf("", "invalid!")) {
            http.get().uri("/api/v1/notifications").header("X-Trace-Id", traceId)
                .exchange().expectStatus().isUnauthorized
                .expectHeader().valueMatches("X-Trace-Id", "[A-Za-z0-9-]{8,64}")
        }
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
