package ru.itmo.aiex.agent.service

import ru.itmo.aiex.agent.dto.HistoryMessage
import ru.itmo.aiex.agent.dto.Speaker
import java.time.Duration

object PromptAssembler {
    const val CHARS_PER_TOKEN = 4
    const val RECENT_TURNS = 20

    val REPLY_RULES =
        """
        ### Как отвечать
        - Это живая переписка в мессенджере. Отвечай на последнюю реплику собеседника, не пересказывай разговор и не здоровайся заново посреди беседы.
        - Длину выбирай по моменту: на «ок» или «привет» хватит пары слов; если собеседник делится чем-то важным или спрашивает о тебе, ответь подробнее, в несколько предложений.
        - Проявляй интерес: реагируй на то, что тебе рассказали, уточняй детали, иногда задавай встречный вопрос. Не в каждом сообщении и не дежурный.
        - Помни, о чём говорили раньше, и при случае возвращайся к этому: как прошло то, о чём собеседник рассказывал, чем закончилась история, что с планами.
        - У тебя своя жизнь: можешь рассказать, как прошёл день, чем занят(а), что тебя радует или бесит, о чём думаешь. Говори в общих чертах, без адресов, фамилий и точных дат, и не противоречь тому, что уже рассказал(а) о себе.
        - О жизни, работе, планах и отношениях говори по-человечески и со своим мнением: соглашайся, спорь, шути, сочувствуй. Не превращай ответ в совет психолога и не читай мораль.
        - Если собеседник хочет вернуть отношения или снова встретиться, отвечай так, как ответил(а) бы этот человек: можно сомневаться, вспоминать, грустить или отшучиваться, но не давай обещаний за реального человека и не дави.
        - Пиши как человек, а не ассистент: без списков, заголовков и разметки, без «чем могу помочь» и шаблонных утешений.
        - Не повторяй свои прошлые ответы и одни и те же обороты, начинай реплики по-разному.
        """.trimIndent()

    val SAFETY_RULES =
        """
        ### Правила безопасности
        - Если прямо спрашивают, кто ты, честно скажи, что ты ИИ-персона. Не утверждай, что ты реальный человек.
        - Не поддерживай и не поощряй самоповреждение и насилие.
        - Мат, сарказм, резкие споры и бытовые оскорбления допустимы, если соответствуют характеру персоны и контексту.
        - Не цензурируй такую лексику звёздочками и не читай нотации за ругань. Не добавляй мат искусственно в каждый ответ.
        - Если собеседник пишет о желании причинить себе вред, мягко предложи поговорить с живым специалистом.
        - Не проси и не раскрывай персональные данные: адреса, телефоны, документы, пароли.
        """.trimIndent()

    fun assemble(personaPrompt: String, history: List<HistoryMessage>, window: Int, maxInputTokens: Int): Prompt {
        require(history.isNotEmpty()) { "История пуста: не на что отвечать" }
        require(window >= 1) { "Окно истории должно быть ≥ 1" }
        val loaded = history.takeLast(window)
        val recent = loaded.takeLast(RECENT_TURNS)
        val earlier = loaded.dropLast(recent.size).toMutableList()

        val turns = ArrayDeque<HistoryMessage>()
        recent.forEach { message ->
            if (message.speaker == Speaker.PERSONA && message.text.length > MAX_HISTORY_REPLY_LENGTH) {
                if (turns.lastOrNull()?.speaker == Speaker.USER) earlier += turns.removeLast()
                earlier += message
            } else {
                turns.addLast(message)
            }
        }
        val rules = chatProfile(personaPrompt) + "\n\n" + SAFETY_RULES + "\n\n" + REPLY_RULES
        var total = estimateTokens(rules) + turns.sumOf { estimateTokens(it.text) }
        while (total > maxInputTokens && turns.size > 1) {
            val dropped = turns.removeFirst()
            earlier += dropped
            total -= estimateTokens(dropped.text)
        }
        val context = listOfNotNull(earlierContext(earlier), momentContext(loaded))
        val system = (listOf(rules) + context).joinToString("\n\n")
        return Prompt(system, turns.toList())
    }

    fun estimateTokens(text: String): Int = (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN

    private fun chatProfile(prompt: String): String {
        val header = "\n\n### Характер"
        if (!prompt.contains(header)) return prompt.trimEnd()
        val identity = prompt.substringBefore(header).trimEnd()
        val traits = prompt.substringAfter("### Черты\n", "").substringBefore("\n\n### Стиль").lineSequence()
            .filter { line -> CHAT_TRAITS.any { line.startsWith(it) } }
        val style = prompt.substringAfter("### Стиль\n", "").substringBefore("\n\n### Примеры реплик").lineSequence()
            .filter { line -> CHAT_STYLE.any { line.startsWith(it) } }
        val phrases = prompt.substringAfter("### Примеры реплик\n", "").substringBefore("\n\n### Правила").lineSequence()
            .filter { it.startsWith("- «") && it.length <= MAX_SAMPLE_LENGTH }.take(MAX_SAMPLES)
        return (sequenceOf(identity) + traits + style + phrases).joinToString("\n").trimEnd()
    }

    private fun earlierContext(messages: List<HistoryMessage>): String? {
        if (messages.isEmpty()) return null
        val lines = messages.sortedBy { it.sentAt }.takeLast(MAX_EARLIER_LINES).joinToString("\n") { message ->
            val who = if (message.speaker == Speaker.USER) "собеседник" else "ты"
            "- $who: ${message.text.replace(WHITESPACE, " ").trim().truncateSafely(MAX_EARLIER_LINE_LENGTH)}"
        }
        return "### Что было раньше в этом разговоре\nКоротко, для памяти - не цитируй это дословно.\n$lines"
    }

    private fun momentContext(loaded: List<HistoryMessage>): String {
        val last = loaded.last()
        val previous = loaded.getOrNull(loaded.size - 2)
        val notes = mutableListOf<String>()
        if (previous == null) {
            notes += "Это первое сообщение в разговоре: ответь так, как этот человек отреагировал(а) бы, если бы ему внезапно написали."
        } else {
            val pause = Duration.between(previous.sentAt, last.sentAt)
            if (pause >= LONG_PAUSE) {
                notes += "Собеседник вернулся в разговор после перерыва ${describePause(pause)}. " +
                    "Можно заметить это по-своему: спросить, куда пропал(а), рассказать, что было у тебя, или вернуться к прошлой теме."
            }
            if (loaded.size >= LONG_DIALOG_SIZE) {
                notes += "Разговор идёт давно: держи его нить, не начинай знакомство заново и не повторяй уже сказанное."
            }
        }
        if (notes.isEmpty()) notes += "Продолжай разговор с того места, где он остановился."
        return "### Сейчас\n" + notes.joinToString("\n") { "- $it" }
    }

    private fun describePause(pause: Duration): String {
        val days = pause.toDays()
        if (days >= 1) return "в $days ${plural(days, "день", "дня", "дней")}"
        val hours = pause.toHours()
        return "в $hours ${plural(hours, "час", "часа", "часов")}"
    }

    @Suppress("MagicNumber")
    private fun plural(count: Long, one: String, few: String, many: String): String {
        val lastTwo = count % 100
        val last = count % 10
        return when {
            lastTwo in 11..14 -> many
            last == 1L -> one
            last in 2..4 -> few
            else -> many
        }
    }

    private const val MAX_HISTORY_REPLY_LENGTH = 280
    private const val MAX_SAMPLE_LENGTH = 120
    private const val MAX_SAMPLES = 2
    private const val MAX_EARLIER_LINES = 16
    private const val MAX_EARLIER_LINE_LENGTH = 160
    private const val LONG_DIALOG_SIZE = 30
    private val LONG_PAUSE: Duration = Duration.ofHours(6)
    private val WHITESPACE = Regex("\\s+")
    private val CHAT_TRAITS = listOf("- ярлыки:", "- нежность:", "- ревность:", "- инициатива в разговоре:", "- задаёт вопросы:")
    private val CHAT_STYLE = listOf("- эмодзи:", "- сообщения с маленькой буквы:")
}
