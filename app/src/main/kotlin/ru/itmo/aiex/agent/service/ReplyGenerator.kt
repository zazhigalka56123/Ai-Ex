package ru.itmo.aiex.agent.service

import ru.itmo.aiex.agent.dto.GenerateReplyCommand
import ru.itmo.aiex.agent.dto.GeneratedReply

interface ReplyGenerator {
    val historyWindow: Int

    fun generate(command: GenerateReplyCommand): GeneratedReply
}
