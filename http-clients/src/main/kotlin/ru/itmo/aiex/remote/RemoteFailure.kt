package ru.itmo.aiex.remote

import feign.FeignException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.ErrorCode
import java.util.function.Predicate

object RemoteFailure {
    fun domainException(error: Throwable): AiExException? {
        val causes = generateSequence(error) { it.cause }.toList()
        causes.filterIsInstance<AiExException>().firstOrNull()?.let { return it }
        return if (causes.any { it is FeignException || it is NoFallbackAvailableException || it is CallNotPermittedException }) {
            AiExException(ErrorCode.SERVICE_UNAVAILABLE, "Зависимый сервис временно недоступен", cause = error)
        } else {
            null
        }
    }
}

class RemoteFailurePredicate : Predicate<Throwable> {
    override fun test(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.filterIsInstance<AiExException>().firstOrNull()?.code?.httpStatus
            ?.let { it >= ErrorCode.INTERNAL_ERROR.httpStatus } ?: true
}
