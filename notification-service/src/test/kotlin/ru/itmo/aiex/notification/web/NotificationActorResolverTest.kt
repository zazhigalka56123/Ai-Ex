package ru.itmo.aiex.notification.web

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import reactor.test.StepVerifier
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.dto.UserView
import ru.itmo.aiex.notification.client.NotificationUserClient
import java.util.UUID

class NotificationActorResolverTest {
    private val users = mockk<NotificationUserClient>()
    private val resolver = NotificationActorResolver(users)

    @Test
    fun `блокирующий Feign выполняется на boundedElastic`() {
        val userId = UUID.randomUUID()
        var lookupThread = ""
        every { users.findActive(userId) } answers {
            lookupThread = Thread.currentThread().name
            UserView(userId, "Маша", setOf(RoleCode.USER), true)
        }

        StepVerifier.create(resolver.resolve(userId.toString())).assertNext { actor ->
            assertThat(actor.userId).isEqualTo(userId)
            assertThat(actor.roles).containsExactly(RoleCode.USER)
        }.verifyComplete()
        assertThat(lookupThread).startsWith("boundedElastic-")
    }

    @Test
    fun `недоступность Feign не даёт доступ к данным`() {
        val userId = UUID.randomUUID()
        every { users.findActive(userId) } throws IllegalStateException("account unavailable")

        StepVerifier.create(resolver.resolve(userId.toString())).expectErrorSatisfies { error ->
            assertThat(error).isInstanceOf(ResponseStatusException::class.java)
            assertThat((error as ResponseStatusException).statusCode.value()).isEqualTo(503)
        }.verify()
    }
}
