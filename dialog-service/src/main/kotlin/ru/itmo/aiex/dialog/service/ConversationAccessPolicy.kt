package ru.itmo.aiex.dialog.service

import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.dialog.entity.MessageScope
import java.util.UUID

object ConversationAccessPolicy {
    fun decide(ownerId: UUID, actor: Actor, isSharedWithSpecialist: () -> Boolean): ConversationAccess = when {
        ownerId == actor.userId -> ConversationAccess.Granted(MessageScope.ALL)
        actor.isSpecialist && isSharedWithSpecialist() -> ConversationAccess.Granted(MessageScope.ALL)
        actor.isAdmin -> ConversationAccess.Granted(MessageScope.FLAGGED_ONLY)
        actor.isSpecialist -> ConversationAccess.Forbidden
        else -> ConversationAccess.Hidden
    }
}
