package ru.itmo.aiex.dialog.service

import java.util.UUID

data class CreateConversationCommand(val personaId: UUID, val title: String?)
