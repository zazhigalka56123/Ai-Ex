package ru.itmo.aiex.iam.controller

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import ru.itmo.aiex.iam.dto.CreateUserRequest
import ru.itmo.aiex.iam.entity.User
import ru.itmo.aiex.iam.service.UserService
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class ReactiveJpaTest {
    @Test
    fun `JPA вызывается лениво на boundedElastic`() {
        val service = mockk<UserService>()
        val thread = AtomicReference<String>()
        every { service.registerUser(any()) } answers {
            thread.set(Thread.currentThread().name)
            User(UUID.randomUUID(), "user@example.com", "User", Instant.now(), Instant.now())
        }
        val response = UserController(service).createUser(CreateUserRequest("user@example.com", "User"))
        assertThat(thread.get()).isNull()
        StepVerifier.create(response).assertNext { assertThat(it.statusCode.value()).isEqualTo(201) }.verifyComplete()
        assertThat(thread.get()).startsWith("boundedElastic-")
    }
}
