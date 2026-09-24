package ru.itmo.aiex.dialog.domain

sealed interface ConversationAccess {
    data class Granted(val scope: MessageScope) : ConversationAccess

    data object Forbidden : ConversationAccess

    data object Hidden : ConversationAccess
}
