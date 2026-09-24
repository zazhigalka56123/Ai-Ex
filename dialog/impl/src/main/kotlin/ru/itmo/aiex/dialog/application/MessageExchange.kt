package ru.itmo.aiex.dialog.application

import ru.itmo.aiex.dialog.domain.Message

data class MessageExchange(val userMessage: Message, val reply: Message)
