package ru.itmo.aiex.notification.web

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.UnauthenticatedException
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.notification.client.NotificationUserClient
import ru.itmo.aiex.remote.RemoteFailure
import java.util.UUID

@Component
class NotificationActorResolver(private val users: NotificationUserClient) {
    fun resolve(raw: String?): Mono<Actor> = Mono.defer {
        val userId = raw?.trim()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: throw UnauthenticatedException("Заголовок X-User-Id должен содержать UUID")
        Mono.fromCallable {
            val user = users.findActive(userId)
            if (user == null || !user.active) throw UnauthenticatedException("Пользователь $userId не найден или заблокирован")
            Actor(user.id, user.roles)
        }
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap { error ->
                if (error is AiExException) {
                    error
                } else {
                    RemoteFailure.domainException(error)
                        ?: ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Сервис пользователей недоступен", error)
                }
            }
    }
}
