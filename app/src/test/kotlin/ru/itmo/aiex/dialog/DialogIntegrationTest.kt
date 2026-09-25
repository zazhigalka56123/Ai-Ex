package ru.itmo.aiex.dialog

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import ru.itmo.aiex.care.service.ConsultationQuery
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.persona.dto.PersonaProfileView
import ru.itmo.aiex.persona.dto.PersonaState
import ru.itmo.aiex.persona.dto.PersonaSummaryView
import ru.itmo.aiex.persona.dto.StyleView
import ru.itmo.aiex.persona.service.PersonaAccess
import ru.itmo.aiex.persona.service.PersonaProfileQuery
import ru.itmo.aiex.testing.AbstractIntegrationTest
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Suppress("UnnecessaryAbstractClass")
abstract class DialogIntegrationTest : AbstractIntegrationTest() {
    @MockkBean
    protected lateinit var personaAccess: PersonaAccess

    @MockkBean
    protected lateinit var profiles: PersonaProfileQuery

    @MockkBean
    protected lateinit var consultations: ConsultationQuery

    protected val phrases = listOf("ну привет", "опять ты", "я сплю вообще-то")

    @BeforeEach
    fun defaultCollaborators() {
        every { personaAccess.assertOwned(any(), any()) } answers { throw NotFoundException.of(ErrorCode.PERSONA_NOT_FOUND, firstArg<UUID>()) }
        every { consultations.isConversationSharedWith(any(), any()) } returns false
    }

    protected fun readyPersona(ownerId: UUID, name: String = "Маша", samplePhrases: List<String> = phrases): UUID {
        val personaId = UUID.randomUUID()
        val profileId = UUID.randomUUID()
        stubPersona(personaId, ownerId, name, PersonaState.READY, profileId)
        every { profiles.findActiveProfile(personaId) } returns
            PersonaProfileView(
                personaId = personaId,
                profileId = profileId,
                versionNo = 1,
                personaName = name,
                systemPrompt = "Ты - $name. Пишешь коротко и без заглавных.\n### Примеры реплик\n" + samplePhrases.joinToString("\n") { "- «$it»" },
                style = StyleView(14.0, 0.0, emptyList(), 0.0, 0.9, "slow", samplePhrases),
                traits = emptyList(),
                tags = emptyList(),
            )
        return personaId
    }

    protected fun stubPersona(personaId: UUID, ownerId: UUID, name: String, state: PersonaState, activeProfileId: UUID?) {
        every { personaAccess.assertOwned(personaId, ownerId) } returns PersonaSummaryView(personaId, ownerId, name, state, activeProfileId)
    }

    protected fun createConversation(ownerId: UUID, personaId: UUID, title: String? = null): UUID {
        val body = if (title == null) mapOf("personaId" to personaId) else mapOf("personaId" to personaId, "title" to title)
        val result =
            mockMvc
                .post("/api/v1/conversations") {
                    header(USER_HEADER, ownerId)
                    contentType = MediaType.APPLICATION_JSON
                    content = json(body)
                }.andExpect { status { isCreated() } }
                .andReturn()
        return UUID.fromString(result.json()["id"].asString())
    }

    protected fun send(actorId: UUID, conversationId: UUID, text: String): ResultActionsDsl =
        mockMvc.post("/api/v1/conversations/$conversationId/messages") {
            header(USER_HEADER, actorId)
            contentType = MediaType.APPLICATION_JSON
            content = json(mapOf("text" to text))
        }

    protected fun insertMessages(
        conversationId: UUID,
        count: Int,
        startAt: Instant = Instant.parse("2026-09-01T00:00:00Z"),
        step: Duration = Duration.ofSeconds(1),
        flagged: (Int) -> Boolean = { false },
    ): List<StoredMessage> {
        val messages = List(count) { i -> StoredMessage(UUID.randomUUID(), startAt.plus(step.multipliedBy(i.toLong()))) }
        jdbcTemplate.batchUpdate(
            "INSERT INTO dialog.messages (id, conversation_id, sender, body, created_at, flagged) VALUES (?, ?, 'USER', ?, ?, ?)",
            messages.mapIndexed { i, message ->
                arrayOf<Any>(message.id, conversationId, "сообщение $i", OffsetDateTime.ofInstant(message.createdAt, ZoneOffset.UTC), flagged(i))
            },
        )
        return messages
    }

    protected fun agentRuns(): List<Map<String, Any?>> = jdbcTemplate.queryForList("SELECT * FROM agent.agent_runs ORDER BY created_at")

    protected fun messageRows(conversationId: UUID): List<Map<String, Any?>> =
        jdbcTemplate.queryForList("SELECT * FROM dialog.messages WHERE conversation_id = ? ORDER BY created_at, id", conversationId)

    data class StoredMessage(val id: UUID, val createdAt: Instant)

    companion object {
        const val LLM_DOWN = "[[llm:down]]"
        const val LLM_TIMEOUT = "[[llm:timeout]]"
        const val STUB_MODEL = "stub-deterministic"

        val FEED_ORDER: Comparator<StoredMessage> = compareByDescending<StoredMessage> { it.createdAt }.thenByDescending { it.id.toString() }
    }
}
