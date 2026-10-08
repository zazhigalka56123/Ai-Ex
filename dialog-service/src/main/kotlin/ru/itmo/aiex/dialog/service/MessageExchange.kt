package ru.itmo.aiex.dialog.service

import ru.itmo.aiex.dialog.entity.Message
data class MessageExchange(val userMessage: Message, val reply: Message)
