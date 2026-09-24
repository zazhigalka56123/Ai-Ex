package ru.itmo.aiex.admin.application

import ru.itmo.aiex.admin.domain.FlaggedMessage
import ru.itmo.aiex.dialog.api.MessageView

internal fun MessageView.toFlagged() = FlaggedMessage(messageId = id, conversationId = conversationId, personaId = personaId)
