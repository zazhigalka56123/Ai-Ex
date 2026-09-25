package ru.itmo.aiex.agent.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.itmo.aiex.agent.dto.HistoryMessage
import ru.itmo.aiex.agent.dto.Speaker
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
    }

    @Test
    fun `системная часть - профиль персоны плюс правила безопасности`() {
        val prompt = PromptAssembler.assemble("Ты - Маша.\n- «ну привет»\n", history(1), window = 5, maxInputTokens = 100_000)
        assertThat(prompt.system).startsWith("Ты - Маша.\n- «ну привет»\n\n### Правила безопасности")
        assertThat(prompt.system).endsWith(PromptAssembler.SAFETY_RULES)
    }

    @Test
    fun `бюджет токенов отбрасывает самые старые реплики, считая и системную часть`() {
        val all = history(10, length = 40)
        val systemTokens = PromptAssembler.estimateTokens("Ты - Маша.\n\n" + PromptAssembler.SAFETY_RULES)
        val prompt = PromptAssembler.assemble("Ты - Маша.", all, window = 10, maxInputTokens = systemTokens + 35)
        assertThat(prompt.turns).hasSize(3)
        assertThat(prompt.turns.map { it.text.take(3) }).containsExactly("m8.", "m9.", "m10")
        assertThat(prompt.estimatedTokens).isLessThanOrEqualTo(systemTokens + 35)
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
