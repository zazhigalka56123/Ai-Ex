package ru.itmo.aiex.dialog.client

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.web.ActorLookup
import ru.itmo.aiex.iam.dto.UserView
import ru.itmo.aiex.iam.service.UserQuery
import ru.itmo.aiex.remote.AccountClient
import java.util.UUID

@Component
@Profile("microservice")
class RemoteAccountAdapter(private val accounts: AccountClient) :
    UserQuery,
    ActorLookup {
    override fun findActive(userId: UUID): UserView? = accounts.findActive(userId)

    override fun findActor(userId: UUID): Actor? = findActive(userId)?.let { Actor(it.id, it.roles) }
}
