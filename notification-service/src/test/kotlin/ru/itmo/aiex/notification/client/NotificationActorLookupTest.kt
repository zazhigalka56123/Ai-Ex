package ru.itmo.aiex.notification.client

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.dto.UserView
import java.util.UUID

class NotificationActorLookupTest {
    private val users = mockk<NotificationUserClient>()
    private val lookup = NotificationActorLookup(users)

    @Test
    fun `блокирующий Feign выполняется на boundedElastic`() {
        val userId = UUID.randomUUID()
        var lookupThread = ""
        every { users.findActive(userId) } answers {
            lookupThread = Thread.currentThread().name
            UserView(userId, "Маша", setOf(RoleCode.USER), true)
        }

        StepVerifier.create(lookup.findActor(userId)).assertNext { actor ->
            assertThat(actor.userId).isEqualTo(userId)
            assertThat(actor.roles).containsExactly(RoleCode.USER)
        }.verifyComplete()
        assertThat(lookupThread).startsWith("boundedElastic-")
    }

    @Test
    fun `неизвестный и заблокированный пользователь не дают актора`() {
        val missing = UUID.randomUUID()
        val blocked = UUID.randomUUID()
        every { users.findActive(missing) } returns null
        every { users.findActive(blocked) } returns UserView(blocked, "Аня", emptySet(), false)

        StepVerifier.create(lookup.findActor(missing)).verifyComplete()
        StepVerifier.create(lookup.findActor(blocked)).verifyComplete()
    }

    @Test
    fun `недоступность Feign не даёт доступ к данным`() {
        val userId = UUID.randomUUID()
        every { users.findActive(userId) } throws IllegalStateException("account unavailable")

        StepVerifier.create(lookup.findActor(userId)).expectErrorSatisfies { error ->
            assertThat((error as AiExException).code).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE)
        }.verify()
    }
}
