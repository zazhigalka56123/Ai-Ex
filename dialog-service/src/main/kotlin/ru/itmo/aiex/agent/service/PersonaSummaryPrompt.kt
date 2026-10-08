package ru.itmo.aiex.agent.service

import ru.itmo.aiex.agent.dto.DescribePersonaCommand
import ru.itmo.aiex.agent.dto.HistoryMessage
import ru.itmo.aiex.agent.dto.Speaker
import java.time.Instant
import java.util.Locale

object PersonaSummaryPrompt {
    const val MAX_OUTPUT_TOKENS = 200
    const val MAX_CHARS = 600
    const val TEMPERATURE = 0.3

    private val WHITESPACE = Regex("\\s+")

    fun assemble(command: DescribePersonaCommand, now: Instant): Prompt {
        val system = buildString {
            appendLine("Ты - внимательный аналитик переписки. По статистике и примерам реплик опиши характер человека")
            appendLine("по имени ${command.personaName} в 2–3 предложениях, в третьем лице, без диагнозов и оценок. Не цитируй реплики дословно.")
            appendLine()
            appendLine("### Черты")
            command.traits.forEach { appendLine("- ${it.label}: ${it.value} (${"%.2f".format(Locale.ROOT, it.weight)})") }
            appendLine()
            appendLine("### Примеры реплик")
            command.samplePhrases.forEach { appendLine("- «$it»") }
        }
        val question = "Опиши характер и манеру общения человека по имени ${command.personaName} в 2–3 предложениях."
        return Prompt(system, listOf(HistoryMessage(Speaker.USER, question, now)))
    }

    fun normalize(raw: String): String = raw.replace(WHITESPACE, " ").trim().take(MAX_CHARS)
}
