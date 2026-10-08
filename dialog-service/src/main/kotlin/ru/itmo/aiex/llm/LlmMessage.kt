package ru.itmo.aiex.llm

data class LlmMessage(val role: LlmRole, val content: String) {
    override fun toString(): String = "LlmMessage(role=$role, chars=${content.length})"
}
