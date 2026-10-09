package ru.itmo.aiex.notification.client

import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.reactive.ReactiveActorLookup
import ru.itmo.aiex.remote.RemoteFailure
import java.util.UUID

/** Пользователи живут в account-service; блокирующий Feign-вызов уходит с event loop на boundedElastic. */
@Component
class NotificationActorLookup(private val users: NotificationUserClient) : ReactiveActorLookup {
    override fun findActor(userId: UUID): Mono<Actor> = Mono.fromCallable { users.findActive(userId)?.takeIf { it.active } }
        .subscribeOn(Schedulers.boundedElastic())
        .map { Actor(it.id, it.roles) }
        .onErrorMap({ it !is AiExException }) { error ->
            RemoteFailure.domainException(error) ?: AiExException(ErrorCode.SERVICE_UNAVAILABLE, "Сервис пользователей недоступен", cause = error)
        }
}
