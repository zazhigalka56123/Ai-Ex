package ru.itmo.aiex.dialog.service

import ru.itmo.aiex.dialog.entity.MessageScope

sealed interface ConversationAccess {
    data class Granted(val scope: MessageScope) : ConversationAccess

    data object Forbidden : ConversationAccess

    data object Hidden : ConversationAccess
}
