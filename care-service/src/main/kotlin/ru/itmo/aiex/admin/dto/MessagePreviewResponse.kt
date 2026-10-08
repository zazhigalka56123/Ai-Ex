package ru.itmo.aiex.admin.dto

import io.swagger.v3.oas.annotations.media.Schema
import ru.itmo.aiex.dialog.dto.SenderKind
import java.time.Instant

@Schema(description = "Флагнутое сообщение - единственное, что администратор видит из беседы")
data class MessagePreviewResponse(val sender: SenderKind, val body: String, val createdAt: Instant)
