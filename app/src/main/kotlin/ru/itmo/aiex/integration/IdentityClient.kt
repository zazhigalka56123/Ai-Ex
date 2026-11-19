package ru.itmo.aiex.integration

import org.slf4j.MDC
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.web.ActorLookup
import ru.itmo.aiex.iam.dto.UserView
import ru.itmo.aiex.iam.service.UserQuery
import java.util.UUID

@FeignClient(name = "iam-service", dismiss404 = true)
interface IdentityClient {
    @GetMapping("/internal/metrics")
    fun metrics(@RequestHeader("X-Trace-Id", required = false) traceId: String? = MDC.get("traceId")): Map<String, Long>

    @GetMapping("/internal/users/{id}")
    fun findActive(@PathVariable id: UUID, @RequestHeader("X-Trace-Id", required = false) traceId: String? = MDC.get("traceId")): UserView?
}

@Component
class RemoteUserQuery(private val client: IdentityClient) : UserQuery {
    override fun findActive(userId: UUID): UserView? = client.findActive(userId)
}

@Component
class RemoteActorLookup(private val users: UserQuery) : ActorLookup {
    override fun findActor(userId: UUID): Actor? = users.findActive(userId)?.let { Actor(it.id, it.roles) }
}
