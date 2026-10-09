package ru.itmo.aiex.iam.service

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.iam.dto.UserResponse
import ru.itmo.aiex.iam.dto.UserView
import ru.itmo.aiex.iam.dto.toResponse
import ru.itmo.aiex.iam.entity.UserStatus
import ru.itmo.aiex.reactive.ReactiveActorLookup
import java.util.UUID

/**
 * Реактивный фасад над транзакционным [UserService]: бизнес-правила и транзакции остаются в одном месте,
 * а наружу сервис отдаёт `Mono`. Сущность превращается в DTO в том же JPA-потоке, пока роли ещё доступны.
 */
@Service
@Profile("microservice")
class ReactiveUserService(
    private val users: UserService,
    private val query: UserQuery,
    private val metrics: IamMetrics,
    private val jpa: JpaScheduler,
) : ReactiveActorLookup {
    fun registerUser(command: RegisterUserCommand): Mono<UserResponse> = jpa.call { users.registerUser(command).toResponse() }

    fun getUsers(actor: Actor, status: UserStatus?, page: PageQuery): Mono<PageView<UserResponse>> =
        jpa.call { users.getUsers(actor, status, page).map { it.toResponse() } }

    fun getUser(actor: Actor, userId: UUID): Mono<UserResponse> = jpa.call { users.getUser(actor, userId).toResponse() }

    fun updateUser(actor: Actor, userId: UUID, command: UpdateUserCommand): Mono<UserResponse> =
        jpa.call { users.updateUser(actor, userId, command).toResponse() }

    fun findActive(userId: UUID): Mono<UserView> = jpa.call { query.findActive(userId) }

    fun collectMetrics(): Mono<Map<String, Long>> = jpa.call { metrics.collectMetrics() }

    override fun findActor(userId: UUID): Mono<Actor> = findActive(userId).map { Actor(it.id, it.roles) }
}
