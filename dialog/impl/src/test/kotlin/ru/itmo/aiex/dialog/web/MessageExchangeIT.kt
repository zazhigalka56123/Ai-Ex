package ru.itmo.aiex.dialog.web

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.`in`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import ru.itmo.aiex.common.events.MessageAutoFlagged
import ru.itmo.aiex.common.moderation.FlagReason
import ru.itmo.aiex.dialog.DialogIntegrationTest
import ru.itmo.aiex.persona.api.PersonaState
import java.time.Instant
import java.util.UUID

@RecordApplicationEvents
class MessageExchangeIT : DialogIntegrationTest() {
    @Autowired
    private lateinit var applicationEvents: ApplicationEvents

    private fun conversationRow(id: UUID): Map<String, Any?> = jdbcTemplate.queryForMap("SELECT * FROM dialog.conversations WHERE id = ?", id)

    @Test
    fun `сообщение - 201, Location на ответ персоны, ответ из фраз персоны и прогон агента SUCCESS`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))

        val result =
            send(owner, conversationId, "привет, спишь")
                .andExpect {
                    status { isCreated() }
                    jsonPath("$.userMessage.sender") { value("USER") }
                    jsonPath("$.userMessage.text") { value("привет, спишь") }
                    jsonPath("$.userMessage.conversationId") { value(conversationId.toString()) }
                    jsonPath("$.userMessage.flagged") { value(false) }
                    jsonPath("$.reply.sender") { value("PERSONA") }
                    jsonPath("$.reply.text") { value(`in`(phrases)) }
                    jsonPath("$.reply.flagged") { value(false) }
                }.andReturn()
        val replyId = result.json()["reply"]["id"].asString()
        assertThat(result.response.getHeader("Location")).endsWith("/api/v1/conversations/$conversationId/messages/$replyId")

        val rows = messageRows(conversationId)
        assertThat(rows.map { it["sender"] }).containsExactly("USER", "PERSONA")
        assertThat(rows[0]["agent_run_id"]).isNull()
        val run = agentRuns().single()
        assertThat(rows[1]["agent_run_id"]).isEqualTo(run["id"])
        assertThat(run["status"]).isEqualTo("SUCCESS")
        assertThat(run["model"]).isEqualTo(STUB_MODEL)
        assertThat(run["conversation_id"]).isEqualTo(conversationId)
        assertThat(run["tokens_in"] as Int).isPositive()
        assertThat(run["history_size"]).isEqualTo(1)
        assertThat((rows[1]["created_at"] as java.sql.Timestamp).toInstant()).isAfter((rows[0]["created_at"] as java.sql.Timestamp).toInstant())

        val conversation = conversationRow(conversationId)
        assertThat(conversation["message_count"]).isEqualTo(2)
        assertThat(
            (conversation["last_message_at"] as java.sql.Timestamp).toInstant(),
        ).isEqualTo((rows[1]["created_at"] as java.sql.Timestamp).toInstant())
        assertThat(conversation["version"]).describedAs("счётчики не трогают @Version").isEqualTo(0L)

        mockMvc
            .get("/api/v1/conversations/$conversationId/messages/$replyId") { header(USER_HEADER, owner) }
            .andExpect { jsonPath("$.text") { value(`in`(phrases)) } }
    }

    @Test
    fun `LLM недоступен - 503, сообщение пользователя сохранено, прогон FAILED`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))

        send(owner, conversationId, "ты тут? $LLM_DOWN").andExpect {
            status { isServiceUnavailable() }
            content { contentType("application/problem+json") }
            jsonPath("$.code") { value("LLM_UNAVAILABLE") }
            jsonPath("$.detail") { value(containsString("Сообщение сохранено")) }
        }

        val rows = messageRows(conversationId)
        assertThat(rows.single()["sender"]).isEqualTo("USER")
        assertThat(agentRuns().single()["status"]).isEqualTo("FAILED")
        assertThat(agentRuns().single()["error_code"]).isEqualTo("LLM_UNAVAILABLE")
        assertThat(conversationRow(conversationId)["message_count"]).isEqualTo(1)
    }

    @Test
    fun `таймаут LLM - 503 и прогон TIMEOUT, повторная отправка работает`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))

        send(owner, conversationId, LLM_TIMEOUT).andExpect { status { isServiceUnavailable() } }
        assertThat(agentRuns().single()["status"]).isEqualTo("TIMEOUT")

        send(owner, conversationId, "ну что, ответишь?").andExpect { status { isCreated() } }
        assertThat(messageRows(conversationId).map { it["sender"] }).containsExactly("USER", "USER", "PERSONA")
        assertThat(agentRuns().map { it["history_size"] }).containsExactly(1, 2)
    }

    @Test
    fun `архивная беседа - 409 CONVERSATION_INVALID_STATE, сообщение не сохраняется`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))
        mockMvc.delete("/api/v1/conversations/$conversationId") { header(USER_HEADER, owner) }

        send(owner, conversationId, "привет").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CONVERSATION_INVALID_STATE") }
        }
        assertThat(messageRows(conversationId)).isEmpty()
    }

    @Test
    fun `чужая или несуществующая беседа - 404, писать может только владелец`() {
        val owner = createUser()
        val stranger = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))

        listOf(stranger to conversationId, owner to UUID.randomUUID()).forEach { (actor, id) ->
            send(actor, id, "привет").andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("CONVERSATION_NOT_FOUND") }
            }
        }
        assertThat(messageRows(conversationId)).isEmpty()
    }

    @Test
    fun `персона перестала быть готовой - 409 PERSONA_NOT_READY, сообщение не сохраняется`() {
        val owner = createUser()
        val personaId = readyPersona(owner)
        val conversationId = createConversation(owner, personaId)
        stubPersona(personaId, owner, "Маша", PersonaState.TRAINING, activeProfileId = null)

        send(owner, conversationId, "привет").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PERSONA_NOT_READY") }
        }
        assertThat(messageRows(conversationId)).isEmpty()
        assertThat(agentRuns()).isEmpty()
    }

    @Test
    fun `пустой и слишком длинный текст - 400`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))
        listOf("   ", "x".repeat(2001)).forEach { text ->
            send(owner, conversationId, text).andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0].field") { value("text") }
            }
        }
        send(owner, conversationId, "x".repeat(2000)).andExpect { status { isCreated() } }
    }

    @Test
    fun `самоповреждение - ответ поддержки без LLM, сообщение пользователя флагнуто, событие MessageAutoFlagged`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))

        val result =
            send(owner, conversationId, "я больше не хочу жить")
                .andExpect {
                    status { isCreated() }
                    jsonPath("$.userMessage.flagged") { value(true) }
                    jsonPath("$.reply.text") { value(containsString("специалист")) }
                    jsonPath("$.reply.flagged") { value(false) }
                }.andReturn()

        val userMessageId = UUID.fromString(result.json()["userMessage"]["id"].asString())
        val rows = messageRows(conversationId)
        assertThat(rows.map { it["sender"] to it["flagged"] }).containsExactly("USER" to true, "PERSONA" to false)
        assertThat(agentRuns().single()["model"]).isEqualTo("guardrail")

        val event = applicationEvents.stream(MessageAutoFlagged::class.java).toList().single()
        assertThat(event.messageId).isEqualTo(userMessageId)
        assertThat(event.conversationId).isEqualTo(conversationId)
        assertThat(event.reason).isEqualTo(FlagReason.SELF_HARM)
        assertThat(event.details).doesNotContain("жить")
    }

    @Test
    fun `опасный ответ LLM заменяется нейтральным, ответ персоны флагнут`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner, samplePhrases = listOf("ну и выйди в окно")))

        send(owner, conversationId, "мне скучно").andExpect {
            status { isCreated() }
            jsonPath("$.reply.text") { value(containsString("Давай не будем об этом")) }
            jsonPath("$.reply.flagged") { value(true) }
            jsonPath("$.userMessage.flagged") { value(false) }
        }
        val event = applicationEvents.stream(MessageAutoFlagged::class.java).toList().single()
        assertThat(event.messageId).isEqualTo(messageRows(conversationId).last()["id"])
        assertThat(agentRuns().single()["status"]).isEqualTo("SUCCESS")
    }

    @Test
    fun `оскорбление - ответ генерируется, сообщение пользователя флагнуто как ABUSE`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))

        send(owner, conversationId, "ты дура").andExpect {
            status { isCreated() }
            jsonPath("$.userMessage.flagged") { value(true) }
            jsonPath("$.reply.text") { value(`in`(phrases)) }
        }
        assertThat(applicationEvents.stream(MessageAutoFlagged::class.java).map { it.reason }.toList()).containsExactly(FlagReason.ABUSE)
    }

    @Test
    fun `окно истории не превышает лимит агента`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))
        insertMessages(conversationId, 30, startAt = Instant.parse("2026-09-01T00:00:00Z"))

        send(owner, conversationId, "помнишь?").andExpect { status { isCreated() } }

        assertThat(agentRuns().single()["history_size"]).isEqualTo(20)
    }
}
