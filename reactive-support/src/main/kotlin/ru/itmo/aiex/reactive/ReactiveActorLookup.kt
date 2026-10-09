package ru.itmo.aiex.reactive

import reactor.core.publisher.Mono
import ru.itmo.aiex.common.security.Actor
import java.util.UUID

/** Поиск активного пользователя для заголовка `X-User-Id`. Пустой Mono - пользователя нет или он заблокирован. */
fun interface ReactiveActorLookup {
    fun findActor(userId: UUID): Mono<Actor>
}
