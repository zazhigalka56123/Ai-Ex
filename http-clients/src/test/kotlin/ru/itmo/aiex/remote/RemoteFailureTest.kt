package ru.itmo.aiex.remote

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.ErrorCode
import java.net.ConnectException

class RemoteFailureTest {
    @Test
    fun `circuit breaker сохраняет ошибку домена под обёрткой`() {
        val domain = AiExException(ErrorCode.PERSONA_NOT_FOUND, "Персона не найдена")
        val wrapped = NoFallbackAvailableException("No fallback", domain)

        assertThat(RemoteFailure.domainException(wrapped)).isSameAs(domain)
    }

    @Test
    fun `circuit breaker преобразует сбой соединения в 503`() {
        val wrapped = NoFallbackAvailableException("No fallback", ConnectException("Connection refused"))

        assertThat(RemoteFailure.domainException(wrapped)?.code).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE)
        assertThat(RemoteFailure.domainException(IllegalStateException("Local failure"))).isNull()
    }

    @Test
    fun `ошибки клиента не открывают цепь`() {
        val predicate = RemoteFailurePredicate()

        assertThat(predicate.test(AiExException(ErrorCode.PERSONA_NOT_FOUND, "missing"))).isFalse()
        assertThat(predicate.test(AiExException(ErrorCode.SERVICE_UNAVAILABLE, "unavailable"))).isTrue()
        assertThat(predicate.test(ConnectException("Connection refused"))).isTrue()
    }
}
