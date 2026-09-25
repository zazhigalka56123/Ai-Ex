package ru.itmo.aiex.persona.service.profile

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.itmo.aiex.llm.LlmMessage
import ru.itmo.aiex.llm.LlmRequest
import ru.itmo.aiex.llm.LlmRole
import ru.itmo.aiex.llm.StubLlmClient
import ru.itmo.aiex.persona.entity.RelationshipKind
import ru.itmo.aiex.persona.testing.TestCorpus
class PromptBuilderTest {
    private val builder = PromptBuilder()
    private val style = StyleFactory.from(TestCorpus.snapshot(phrases = listOf("неа", "ну ок\nпока", "  сплю «уже»  ", "")))

    private fun prompt(summary: String = "Отвечает сухо и ревнует.") = builder.build(
        PromptInput(
            personaName = "Маша",
            relationshipKind = RelationshipKind.EX_PARTNER,
            summary = summary,
            traits = TraitExtractor().extract(TestCorpus.coldStats(), 30),
            tagTitles = listOf("ревнивая", "сова"),
            style = style,
        ),
    )

    @Test
    fun `промпт содержит все разделы в нужном порядке`() {
        val prompt = prompt()
        assertThat(prompt).startsWith("Ты - Маша.")
        assertThat(prompt).contains("бывший партнёр", "от первого лица", "в её/его манере")
        val sections = listOf("### Характер", "### Черты", "### Стиль", "### Примеры реплик", "### Правила")
        assertThat(sections.map(prompt::indexOf)).allSatisfy { assertThat(it).isPositive() }.isSorted()
        assertThat(prompt).contains("Отвечает сухо и ревнует.")
        assertThat(prompt).contains("- ревность: high (1.00)", "- ярлыки: ревнивая, сова")
        assertThat(prompt).contains("скорость ответа: отвечает медленно", "сообщения капсом: 30%")
        assertThat(prompt).contains("специалист", "самоповреждением")
    }

    @Test
    fun `каждая характерная фраза - отдельная строка вида - «фраза»`() {
        val lines = prompt().lines().filter { PHRASE_LINE.matches(it) }
        assertThat(lines).containsExactly("- «неа»", "- «ну ок пока»", "- «сплю \"уже\"»")
    }

    @Test
    fun `StubLlmClient отвечает фразами из собранного промпта`() {
        val reply = StubLlmClient().complete(LlmRequest("agent.reply", prompt(), listOf(LlmMessage(LlmRole.USER, "привет, спишь")), 64))
        assertThat(reply.text).isIn("неа", "ну ок пока", "сплю \"уже\"")
    }

    @Test
    fun `без фраз и без резюме - нейтральные подстановки`() {
        val empty = style.copy(samplePhrases = emptyList(), topEmojis = emptyList(), replySpeed = "unknown")
        val prompt = builder.build(PromptInput("Маша", RelationshipKind.FRIEND, "  ", emptyList(), emptyList(), empty))
        assertThat(prompt).contains("Характер по переписке не описан.", "Характерных фраз в переписке не нашлось", "скорость ответа неизвестна")
        assertThat(prompt.lines().none { PHRASE_LINE.matches(it) }).isTrue()
        assertThat(prompt).doesNotContain("ярлыки")
    }

    @Test
    fun `многострочное резюме от LLM схлопывается в один абзац`() {
        assertThat(prompt("Первая строка.\n\n- «не фраза»\nВторая.")).contains("Первая строка. - «не фраза» Вторая.")
    }

    @Test
    fun `стиль округляется и берёт скорость ответа из статистики`() {
        assertThat(style.replySpeed).isEqualTo("slow")
        assertThat(style.avgMessageLength).isEqualTo(12.4)
        assertThat(style.samplePhrases).hasSize(3)
        assertThat(PromptBuilder.traitLabel("unknown_key")).isEqualTo("unknown_key")
        assertThat(PromptBuilder.sanitizePhrase("x".repeat(300))).hasSize(200)
    }

    private companion object {
        val PHRASE_LINE = Regex("""^\s*[-•]\s*«(.+)»\s*$""")
    }
}
