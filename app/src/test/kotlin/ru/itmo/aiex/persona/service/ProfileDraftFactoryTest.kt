package ru.itmo.aiex.persona.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.itmo.aiex.agent.dto.DescribePersonaCommand
import ru.itmo.aiex.agent.dto.PersonaDescription
import ru.itmo.aiex.agent.service.PersonaDescriber
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.LlmUnavailableException
import ru.itmo.aiex.persona.testing.TestCorpus
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.util.UUID

class ProfileDraftFactoryTest {
    private val json = PersonaJson(JsonMapper.builder().addModule(KotlinModule.Builder().build()).build())
    private val describer = mockk<PersonaDescriber>()
    private val factory = ProfileDraftFactory(describer, json)
    private val personaId = UUID.randomUUID()

    @Test
    fun `черновик собирается из корпуса, резюме приходит от агента`() {
        every { describer.describe(any()) } returns PersonaDescription(UUID.randomUUID(), "Сдержанная. Ревнивая.", "mock")

        val snapshot = TestCorpus.snapshot()
        val draft = factory.create(personaId, "Маша", snapshot)

        assertThat(draft.traits.map { it.key }).contains("jealousy", "reply_speed")
        assertThat(draft.autoTags.map { it.code }).contains("cold", "jealous")
        assertThat(draft.style.samplePhrases).containsExactlyElementsOf(TestCorpus.COLD_PHRASES)
        assertThat(draft.summary).isEqualTo("Сдержанная. Ревнивая.")
        assertThat(json.readStyle(draft.styleJson)).isEqualTo(draft.style)
        assertThat(json.readStats(draft.statsJson)).isEqualTo(snapshot.stats)
    }

    @Test
    fun `агент получает подписанные черты и характерные фразы персоны`() {
        val command = slot<DescribePersonaCommand>()
        every { describer.describe(capture(command)) } returns PersonaDescription(UUID.randomUUID(), "Резюме.", "mock")

        factory.create(personaId, "Маша", TestCorpus.snapshot())

        assertThat(command.captured.personaId).isEqualTo(personaId)
        assertThat(command.captured.personaName).isEqualTo("Маша")
        assertThat(command.captured.traits.map { it.label }).contains("ревность", "скорость ответа")
        assertThat(command.captured.samplePhrases).containsExactlyElementsOf(TestCorpus.COLD_PHRASES)
    }

    @Test
    fun `отказ агента пробрасывается как 503 LLM_UNAVAILABLE`() {
        every { describer.describe(any()) } throws LlmUnavailableException("LLM недоступен")

        assertThatThrownBy { factory.create(personaId, "Маша", TestCorpus.snapshot()) }
            .isInstanceOf(LlmUnavailableException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.LLM_UNAVAILABLE)
    }

    @Test
    fun `корпус переживает круг сериализации в jsonb`() {
        val snapshot = TestCorpus.snapshot()
        assertThat(json.readSnapshot(json.write(snapshot))).isEqualTo(snapshot)
    }
}
