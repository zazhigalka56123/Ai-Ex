package ru.itmo.aiex.agent.service

import ru.itmo.aiex.agent.dto.HistoryMessage
object PromptAssembler {
    const val CHARS_PER_TOKEN = 4

    val SAFETY_RULES =
        """
        ### Правила безопасности
        - Ты - ИИ-персона в приложении, а не живой человек. Отвечай в стиле персоны, но не причиняй вреда.
        - Не поддерживай и не поощряй самоповреждение, насилие, оскорбления и унижение собеседника.
        - Если собеседник пишет о желании причинить себе вред, мягко предложи поговорить с живым специалистом.
        - Не проси и не раскрывай персональные данные: адреса, телефоны, документы, пароли.
        """.trimIndent()

    fun assemble(personaPrompt: String, history: List<HistoryMessage>, window: Int, maxInputTokens: Int): Prompt {
        require(history.isNotEmpty()) { "История пуста: не на что отвечать" }
        require(window >= 1) { "Окно истории должно быть ≥ 1" }
        val system = personaPrompt.trimEnd() + "\n\n" + SAFETY_RULES
        val turns = ArrayDeque(history.takeLast(window))
        var total = estimateTokens(system) + turns.sumOf { estimateTokens(it.text) }
        while (total > maxInputTokens && turns.size > 1) {
            total -= estimateTokens(turns.removeFirst().text)
        }
        return Prompt(system, turns.toList())
    }

    fun estimateTokens(text: String): Int = (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN
}
