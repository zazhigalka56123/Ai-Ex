package ru.itmo.aiex.web

import ru.itmo.aiex.common.security.Actor
import java.util.UUID

fun interface ActorLookup {
    fun findActor(userId: UUID): Actor?
}
