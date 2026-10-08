package ru.itmo.aiex.admin.service

import ru.itmo.aiex.dialog.dto.MessageView
internal fun MessageView.toFlagged() = FlaggedMessage(messageId = id, conversationId = conversationId, personaId = personaId)
