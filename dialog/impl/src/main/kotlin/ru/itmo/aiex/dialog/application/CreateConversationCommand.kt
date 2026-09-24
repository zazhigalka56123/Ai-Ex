package ru.itmo.aiex.dialog.application

import java.util.UUID

data class CreateConversationCommand(val personaId: UUID, val title: String?)
