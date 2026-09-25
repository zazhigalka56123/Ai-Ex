package ru.itmo.aiex.iam.controller

import org.springframework.stereotype.Component
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.iam.service.UserQuery
import ru.itmo.aiex.common.web.ActorLookup
import java.util.UUID

@Component
class IamActorLookup(private val users: UserQuery) : ActorLookup {
    override fun findActor(userId: UUID): Actor? = users.findActive(userId)?.let { Actor(it.id, it.roles) }
}
