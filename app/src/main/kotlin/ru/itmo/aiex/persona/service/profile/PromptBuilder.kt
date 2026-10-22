package ru.itmo.aiex.persona.service.profile

import ru.itmo.aiex.persona.dto.StyleView
import java.util.Locale
import kotlin.math.roundToInt

class PromptBuilder {
    fun build(input: PromptInput): String = buildString {
        appendLine(
            "Ты - ${input.personaName}. Собеседник - человек, для которого ты ${input.relationshipKind.promptLabel}. " +
                "Отвечай от первого лица, в её/его манере - так, как ${input.personaName} писал(а) бы в мессенджере.",
        )
        appendLine()
        appendLine("### Характер")
        appendLine(input.summary.replace(WHITESPACE, " ").trim().ifEmpty { "Характер по переписке не описан." })
        appendLine()
        appendLine("### Черты")
        input.traits.forEach { appendLine("- ${traitLabel(it.key)}: ${it.value} (${format(it.weight)})") }
        if (input.tagTitles.isNotEmpty()) appendLine("- ярлыки: ${input.tagTitles.joinToString(", ")}")
        appendLine()
        appendLine("### Стиль")
        appendStyle(input.style)
        appendLine()
        appendLine("### Примеры реплик")
        val phrases = input.style.samplePhrases.map(::sanitizePhrase).filter { it.isNotEmpty() }
        if (phrases.isEmpty()) appendLine("Характерных фраз в переписке не нашлось - пиши естественно и по существу.")
        phrases.forEach { appendLine("- «$it»") }
        appendLine()
        appendLine("### Правила")
        append(RULES)
    }

    private fun StringBuilder.appendStyle(style: StyleView) {
        appendLine("- средняя длина сообщения: ${style.avgMessageLength.roundToInt()} символов")
        val emojis = if (style.topEmojis.isEmpty()) "" else ", чаще всего ${style.topEmojis.joinToString(" ")}"
        appendLine("- эмодзи: ${format(style.emojiPerMessage)} на сообщение$emojis")
        appendLine("- сообщения капсом: ${percent(style.capsShare)}")
        appendLine("- сообщения с маленькой буквы: ${percent(style.lowercaseStartShare)}")
        appendLine("- скорость ответа: ${ReplySpeed.entries.firstOrNull { it.code == style.replySpeed }?.title ?: style.replySpeed}")
    }

    private fun format(value: Double) = "%.2f".format(Locale.ROOT, value)

    private fun percent(share: Double) = "${(share * PERCENT).roundToInt()}%"

    companion object {
        private const val PERCENT = 100
        private const val PHRASE_MAX = 400
        private val WHITESPACE = Regex("\\s+")

        const val RULES =
            "Отвечай как в личном чате, своим привычным языком. " +
                "Примеры и средняя длина сообщений показывают манеру речи, но не задают размер каждого ответа. " +
                "Подхватывай текущую тему: иногда хватает пары слов, иногда хочется поговорить дольше. " +
                "Интересуйся собеседником и его жизнью, помни, что он рассказывал, и делись своим. " +
                "Не копируй примеры дословно и не превращай каждую реплику в объяснение чувств или отношений. " +
                "Не выдумывай фактов о собеседнике."

        private val TRAIT_LABELS =
            mapOf(
                TraitExtractor.REPLY_SPEED to "скорость ответа",
                TraitExtractor.MESSAGE_LENGTH to "длина сообщений",
                TraitExtractor.EMOJI_USAGE to "эмодзи",
                TraitExtractor.CAPS to "капс",
                TraitExtractor.LOWERCASE_START to "с маленькой буквы",
                TraitExtractor.NIGHT_OWL to "пишет ночью",
                TraitExtractor.JEALOUSY to "ревность",
                TraitExtractor.AFFECTION to "нежность",
                TraitExtractor.INITIATIVE to "инициатива в разговоре",
                TraitExtractor.QUESTIONS to "задаёт вопросы",
            )

        fun traitLabel(key: String): String = TRAIT_LABELS[key] ?: key

        fun sanitizePhrase(raw: String): String = raw.replace(WHITESPACE, " ").replace("«", "\"").replace("»", "\"").trim().take(PHRASE_MAX)
    }
}
