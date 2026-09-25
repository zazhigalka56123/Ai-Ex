package ru.itmo.aiex.persona.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import ru.itmo.aiex.common.error.LlmUnavailableException
import ru.itmo.aiex.llm.LlmClient
import ru.itmo.aiex.llm.LlmException
import ru.itmo.aiex.llm.LlmRequest
import ru.itmo.aiex.llm.StubLlmClient

import ru.itmo.aiex.persona.dto.PersonaState
import ru.itmo.aiex.persona.testing.PersonaIntegrationTest
import ru.itmo.aiex.persona.testing.TestCorpus
import java.util.UUID

class ProfileRebuildFailureIT : PersonaIntegrationTest() {
    @MockkBean(relaxed = true)
    private lateinit var llm: LlmClient

    @Autowired
    private lateinit var lifecycle: PersonaLifecycle

    @Autowired
    private lateinit var access: PersonaAccess

    @BeforeEach
    fun stubModelName() {
        every { llm.model } returns StubLlmClient.MODEL
    }

    @Test
    fun `LLM недоступен - 503, персона остаётся TRAINING, после восстановления profile_rebuild собирает профиль`() {
        every { llm.complete(any()) } throws LlmException(LlmException.Reason.UNAVAILABLE, "провайдер лежит")
        val owner = createUser()
        val persona = createPersona(owner)
        lifecycle.startTraining(persona, owner, UUID.randomUUID())

        assertThatThrownBy { lifecycle.rebuildFrom(persona, TestCorpus.snapshot()) }.isInstanceOf(LlmUnavailableException::class.java)
        assertThat(access.findSummary(persona)?.status).isEqualTo(PersonaState.TRAINING)
        assertThat(countRows("SELECT count(*) FROM persona.corpus_snapshots WHERE persona_id = ?", persona)).isEqualTo(1)
        assertThat(countRows("SELECT count(*) FROM persona.persona_profile_versions WHERE persona_id = ?", persona)).isZero()

        mockMvc.post("/api/v1/personas/$persona/profile:rebuild") { header(USER_HEADER, owner) }.andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("LLM_UNAVAILABLE") }
        }

        val stub = StubLlmClient()
        every { llm.complete(any()) } answers { stub.complete(firstArg<LlmRequest>()) }
        mockMvc.post("/api/v1/personas/$persona/profile:rebuild") { header(USER_HEADER, owner) }.andExpect {
            status { isAccepted() }
            jsonPath("$.versionNo") { value(1) }
            jsonPath("$.status") { value("READY") }
        }
        mockMvc.get("/api/v1/personas/$persona/profile") { header(USER_HEADER, owner) }.andExpect { status { isOk() } }
        assertThat(countRows("SELECT count(*) FROM persona.corpus_snapshots WHERE persona_id = ?", persona)).isEqualTo(1)
    }
}
