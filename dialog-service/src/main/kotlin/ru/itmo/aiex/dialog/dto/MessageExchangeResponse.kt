package ru.itmo.aiex.dialog.dto

import io.swagger.v3.oas.annotations.media.Schema
import ru.itmo.aiex.dialog.service.MessageExchange
data class MessageExchangeResponse(
    @field:Schema(description = "Сохранённое сообщение пользователя")
    val userMessage: MessageResponse,
    @field:Schema(description = "Ответ персоны")
    val reply: MessageResponse,
)

fun MessageExchange.toResponse() = MessageExchangeResponse(userMessage = userMessage.toResponse(), reply = reply.toResponse())
