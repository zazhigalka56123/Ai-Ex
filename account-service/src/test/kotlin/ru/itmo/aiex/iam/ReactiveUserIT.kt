package ru.itmo.aiex.iam

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import reactor.test.StepVerifier
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.iam.controller.AccountInternalController
import ru.itmo.aiex.iam.controller.ReactiveUserController
import ru.itmo.aiex.iam.dto.CreateUserRequest
import ru.itmo.bootstrap.account.AccountServiceApplication
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@Testcontainers
@SpringBootTest(
    classes = [AccountServiceApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
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
    private lateinit var users: ReactiveUserController

    @Autowired
    private lateinit var lookup: AccountInternalController

    @Test
    fun `JPA выполняется после подписки в boundedElastic, повторный email возвращает конфликт`() {
        val request = CreateUserRequest("${UUID.randomUUID()}@test.local", "Маша")
        val thread = AtomicReference<String>()
        val creation = users.createUser(request).doOnNext { thread.set(Thread.currentThread().name) }
        assertThat(thread.get()).isNull()
        val created = requireNotNull(creation.block())
        assertThat(thread.get()).startsWith("boundedElastic-")
        val id = requireNotNull(created.body).id
        assertThat(lookup.findActiveUser(id).block()?.body?.displayName).isEqualTo("Маша")
        StepVerifier.create(users.createUser(request))
            .expectErrorSatisfies { assertThat((it as AiExException).code).isEqualTo(ErrorCode.EMAIL_TAKEN) }
            .verify()
    }

    @Test
    fun `отсутствующий пользователь не получает актор`() {
        StepVerifier.create(lookup.findActiveUser(UUID.randomUUID()))
            .assertNext { assertThat(it.statusCode.value()).isEqualTo(204) }
            .verifyComplete()
    }

    companion object {
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
