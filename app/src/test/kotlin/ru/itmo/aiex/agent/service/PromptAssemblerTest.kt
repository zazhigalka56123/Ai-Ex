package ru.itmo.aiex.agent.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.itmo.aiex.agent.dto.HistoryMessage
import ru.itmo.aiex.agent.dto.Speaker
import java.time.Duration
import java.time.Instant

class PromptAssemblerTest {
    private val start = Instant.parse("2026-09-22T00:00:00Z")

    private fun history(count: Int, length: Int = 8): List<HistoryMessage> = (1..count).map { i ->
        val speaker = if (i % 2 == 1 || i == count) Speaker.USER else Speaker.PERSONA
        HistoryMessage(speaker, "m$i".padEnd(length, '.'), start.plusSeconds(i.toLong()))
    }

    @Test
    fun `в промпт уходит только окно последних сообщений, последнее - сообщение пользователя`() {
        val prompt = PromptAssembler.assemble("Ты - Маша.", history(30), window = 20, maxInputTokens = 100_000)
        assertThat(prompt.turns).hasSize(20)
        assertThat(prompt.turns.first().text).startsWith("m11")
        assertThat(prompt.turns.last().text).startsWith("m30")
        assertThat(prompt.turns.last().speaker).isEqualTo(Speaker.USER)
        assertThat(prompt.system).doesNotContain("### Что было раньше")
    }

    @Test
    fun `системная часть - профиль персоны, безопасность, правила ответа и текущий момент`() {
        val prompt = PromptAssembler.assemble("Ты - Маша.\n- «ну привет»\n", history(1), window = 5, maxInputTokens = 100_000)
        assertThat(prompt.system).startsWith("Ты - Маша.\n- «ну привет»\n\n### Правила безопасности")
        assertThat(prompt.system).contains(PromptAssembler.REPLY_RULES, "Проявляй интерес", "У тебя своя жизнь")
        assertThat(prompt.system).contains("### Сейчас\n- Это первое сообщение в разговоре")
    }

    @Test
    fun `старый профиль с длинными примерами и психологическим описанием сокращается для чата`() {
        val savedPrompt = """
            Ты - Олечка. Собеседник - бывший партнёр.

            ### Характер
            Она пишет длинные разборы и обозначает границы.

            ### Черты
            - нежность: low (0.10)
            - длина сообщений: long (1.00)
            - ярлыки: язвительная

            ### Стиль
            - средняя длина сообщения: 500 символов
            - эмодзи: 0.00 на сообщение
            - сообщения с маленькой буквы: 100%

            ### Примеры реплик
            - «ну привет»
            - «${"я объясняю границы. ".repeat(15)}»

            ### Правила
            На каждый вопрос отвечай развёрнуто.
        """.trimIndent()

        val prompt = PromptAssembler.assemble(savedPrompt, history(1), window = 5, maxInputTokens = 100_000)

        assertThat(prompt.system).contains("Ты - Олечка", "- ярлыки: язвительная", "- «ну привет»")
        assertThat(prompt.system)
            .doesNotContain("Она пишет длинные разборы", "На каждый вопрос отвечай развёрнуто", "средняя длина сообщения: 500", "я объясняю границы")
    }

    @Test
    fun `длинные прежние ответы не задают стиль новой реплике`() {
        val messages = listOf(
            HistoryMessage(Speaker.USER, "скучаю", start),
            HistoryMessage(Speaker.PERSONA, "длинный монолог ".repeat(25), start.plusSeconds(1)),
            HistoryMessage(Speaker.USER, "как дела?", start.plusSeconds(2)),
            HistoryMessage(Speaker.PERSONA, "нормально", start.plusSeconds(3)),
            HistoryMessage(Speaker.USER, "пойдём гулять?", start.plusSeconds(4)),
        )

        val prompt = PromptAssembler.assemble("Ты - Олечка.", messages, window = 10, maxInputTokens = 100_000)

        assertThat(prompt.turns.map { it.text }).containsExactly("как дела?", "нормально", "пойдём гулять?")
    }

    @Test
    fun `бюджет токенов переносит самые старые реплики из диалога в память`() {
        val all = history(10, length = 40)
        val rulesTokens = PromptAssembler.estimateTokens("Ты - Маша.\n\n" + PromptAssembler.SAFETY_RULES + "\n\n" + PromptAssembler.REPLY_RULES)
        val prompt = PromptAssembler.assemble("Ты - Маша.", all, window = 10, maxInputTokens = rulesTokens + 35)
        assertThat(prompt.turns).hasSize(3)
        assertThat(prompt.turns.map { it.text.take(3) }).containsExactly("m8.", "m9.", "m10")
        assertThat(prompt.system).contains("### Что было раньше в этом разговоре", "- собеседник: m1.", "- ты: m2.", "- собеседник: m7.")
    }

    @Test
    fun `в длинном разговоре свежие реплики идут диалогом, а ранние - краткой памятью`() {
        val prompt = PromptAssembler.assemble("Ты - Маша.", history(60), window = 60, maxInputTokens = 100_000)

        assertThat(prompt.turns).hasSize(PromptAssembler.RECENT_TURNS)
        assertThat(prompt.turns.first().text).startsWith("m41")
        assertThat(prompt.system).contains("- ты: m40", "- собеседник: m25", "Разговор идёт давно")
        assertThat(prompt.system).doesNotContain("m24.", "«m25")
    }

    @Test
    fun `после долгой паузы персона знает, что собеседник вернулся`() {
        val messages = listOf(
            HistoryMessage(Speaker.USER, "спокойной ночи", start),
            HistoryMessage(Speaker.PERSONA, "сладких снов", start.plusSeconds(5)),
            HistoryMessage(Speaker.USER, "привет, я вернулся", start.plusSeconds(5).plus(Duration.ofDays(3))),
        )

        val prompt = PromptAssembler.assemble("Ты - Маша.", messages, window = 10, maxInputTokens = 100_000)

        assertThat(prompt.system).contains("Собеседник вернулся в разговор после перерыва в 3 дня")
        assertThat(prompt.turns).hasSize(3)
    }

    @Test
    fun `без паузы персона просто продолжает разговор`() {
        val prompt = PromptAssembler.assemble("Ты - Маша.", history(4), window = 10, maxInputTokens = 100_000)

        assertThat(prompt.system).endsWith("### Сейчас\n- Продолжай разговор с того места, где он остановился.")
    }

    @Test
    fun `последнее сообщение пользователя остаётся, даже если одно не влезает в бюджет`() {
        val huge = HistoryMessage(Speaker.USER, "x".repeat(10_000), start)
        val prompt = PromptAssembler.assemble("Ты - Маша.", history(4) + huge, window = 20, maxInputTokens = 10)
        assertThat(prompt.turns).containsExactly(huge)
    }

    @Test
    fun `хеш - SHA-256 в hex и детерминирован, превью - первые 200 символов`() {
        val first = PromptAssembler.assemble("Ты - Маша. ".repeat(40), history(3), window = 20, maxInputTokens = 100_000)
        val second = PromptAssembler.assemble("Ты - Маша. ".repeat(40), history(3), window = 20, maxInputTokens = 100_000)
        assertThat(first.sha256).matches("[0-9a-f]{64}").isEqualTo(second.sha256)
        assertThat(first.preview).hasSize(200).isEqualTo(first.fullText.take(200))
        assertThat(first.fullText).contains("[user]\nm3")
        assertThat(first.toString()).doesNotContain("Маша")
    }

    @Test
    fun `пустая история и нулевое окно - ошибка программиста`() {
        assertThatThrownBy { PromptAssembler.assemble("p", emptyList(), 5, 100) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { PromptAssembler.assemble("p", history(1), 0, 100) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `обрезка не разрывает эмодзи`() {
        assertThat("ab😀".truncateSafely(3)).isEqualTo("ab")
        assertThat("abc".truncateSafely(3)).isEqualTo("abc")
        assertThat("abcd".truncateSafely(3)).isEqualTo("abc")
    }
}
