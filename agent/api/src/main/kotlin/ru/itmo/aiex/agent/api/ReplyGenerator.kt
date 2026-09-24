package ru.itmo.aiex.agent.api

interface ReplyGenerator {
    val historyWindow: Int

    fun generate(command: GenerateReplyCommand): GeneratedReply
}
