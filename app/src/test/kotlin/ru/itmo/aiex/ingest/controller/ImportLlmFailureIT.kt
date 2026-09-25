package ru.itmo.aiex.ingest.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.post
import ru.itmo.aiex.ingest.testing.IngestIntegrationTest
import ru.itmo.aiex.llm.LlmClient
import ru.itmo.aiex.llm.LlmException
import ru.itmo.aiex.llm.LlmRequest
import ru.itmo.aiex.llm.StubLlmClient
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ImportLlmFailureIT : IngestIntegrationTest() {
    @MockkBean(relaxed = true)
    private lateinit var llm: LlmClient

    private val stub = StubLlmClient()

    @BeforeEach
    fun stubModelName() {
        every { llm.model } returns StubLlmClient.MODEL
    }

    @Test
    fun `LLM недоступен - импорт PARSED с PROFILE_REBUILD_FAILED, персона TRAINING, повтор после восстановления - READY`() {
        every { llm.complete(any()) } throws LlmException(LlmException.Reason.UNAVAILABLE, "провайдер лежит")
        val owner = createUser()
        val persona = createPersona(owner)

        upload(owner, persona, "telegram/personal_chat.json").andExpect {
            status { isAccepted() }
            jsonPath("$.status") { value("PARSED") }
            jsonPath("$.errorCode") { value("PROFILE_REBUILD_FAILED") }
            jsonPath("$.messageCount") { value(25) }
        }
        assertThat(persona(owner, persona)["status"].asString()).isEqualTo("TRAINING")

        upload(owner, persona, "whatsapp/android_ru.txt").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PERSONA_INVALID_STATE") }
        }

        every { llm.complete(any()) } answers { stub.complete(firstArg<LlmRequest>()) }
        mockMvc.post("/api/v1/personas/$persona/profile:rebuild") { header(USER_HEADER, owner) }.andExpect {
            status { isAccepted() }
            jsonPath("$.versionNo") { value(1) }
            jsonPath("$.status") { value("READY") }
        }
        assertThat(persona(owner, persona)["status"].asString()).isEqualTo("READY")
    }

    @Test
    fun `два параллельных импорта одной DRAFT-персоны - проходит ровно один, второй получает 409`() {
        every { llm.complete(any()) } answers {
            Thread.sleep(SLOW_LLM_MS)
            stub.complete(firstArg<LlmRequest>())
        }
        val owner = createUser()
        val persona = createPersona(owner)
        val start = CountDownLatch(1)
        val statuses = Collections.synchronizedList(mutableListOf<Int>())
        val codes = Collections.synchronizedList(mutableListOf<String>())
        val pool = Executors.newFixedThreadPool(2)
        listOf("telegram/personal_chat.json", "whatsapp/android_ru.txt").forEach { fixture ->
            pool.submit {
                start.await()
                val response = upload(owner, persona, fixture).andReturn().response
                statuses += response.status
                if (response.status == CONFLICT) codes += response.contentAsString
            }
        }
        start.countDown()
        pool.shutdown()
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue()

        assertThat(statuses).containsExactlyInAnyOrder(ACCEPTED, CONFLICT)
        assertThat(codes.single()).containsAnyOf("CONCURRENT_MODIFICATION", "PERSONA_INVALID_STATE")
        assertThat(persona(owner, persona)["status"].asString()).isEqualTo("READY")
        assertThat(countRows("SELECT count(*) FROM persona.persona_profile_versions WHERE persona_id = ?", persona)).isEqualTo(1)
        assertThat(countRows("SELECT count(*) FROM ingest.chat_imports WHERE persona_id = ? AND error_code = 'PERSONA_BUSY'", persona)).isEqualTo(1)
    }

    private companion object {
        const val SLOW_LLM_MS = 700L
        const val ACCEPTED = 202
        const val CONFLICT = 409
    }
}
