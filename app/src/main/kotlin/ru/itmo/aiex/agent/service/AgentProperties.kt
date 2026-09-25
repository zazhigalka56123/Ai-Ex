package ru.itmo.aiex.agent.service

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("aiex.agent")
data class AgentProperties(val historyWindow: Int = 20, val maxOutputTokens: Int = 512, val maxInputTokens: Int = 4000) {
    init {
        require(historyWindow in 1..MAX_WINDOW) { "aiex.agent.history-window должен быть в диапазоне 1..$MAX_WINDOW" }
        require(maxOutputTokens > 0) { "aiex.agent.max-output-tokens должен быть > 0" }
        require(maxInputTokens > 0) { "aiex.agent.max-input-tokens должен быть > 0" }
    }

    private companion object {
        const val MAX_WINDOW = 200
    }
}
