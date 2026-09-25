package ru.itmo.aiex.agent.service

import ru.itmo.aiex.agent.entity.AgentRun

import ru.itmo.aiex.agent.dto.HistoryMessage
import java.security.MessageDigest
import java.util.HexFormat

data class Prompt(val system: String, val turns: List<HistoryMessage>) {
    val fullText: String by lazy {
        buildString {
            append("[system]\n").append(system)
            turns.forEach { append("\n[").append(it.speaker.name.lowercase()).append("]\n").append(it.text) }
        }
    }

    val sha256: String by lazy { HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fullText.toByteArray(Charsets.UTF_8))) }

    val preview: String get() = fullText.truncateSafely(AgentRun.PROMPT_PREVIEW_LENGTH)

    val estimatedTokens: Int get() = PromptAssembler.estimateTokens(system) + turns.sumOf { PromptAssembler.estimateTokens(it.text) }

    override fun toString(): String = "Prompt(systemChars=${system.length}, turns=${turns.size})"
}
