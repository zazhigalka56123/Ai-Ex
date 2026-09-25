package ru.itmo.aiex.admin.controller

import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import ru.itmo.aiex.admin.AdminIntegrationTest
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.events.DomainEventPublisher
import ru.itmo.aiex.common.events.MessageAutoFlagged
import ru.itmo.aiex.common.events.ModerationFlagRaised
import ru.itmo.aiex.common.events.ModerationFlagResolved
import ru.itmo.aiex.common.moderation.FlagReason
import ru.itmo.aiex.common.security.RoleCode
import java.time.Instant
import java.util.UUID

@RecordApplicationEvents
class ModerationIT : AdminIntegrationTest() {
    @Autowired
    private lateinit var events: ApplicationEvents

    @Autowired
    private lateinit var publisher: DomainEventPublisher

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private fun review(admin: UUID, id: UUID, body: Map<String, Any?>) = mockMvc.patch("/api/v1/moderation/flags/$id") {
        header(USER_HEADER, admin)
        contentType = MediaType.APPLICATION_JSON
        content = json(body)
    }

    @Test
    fun `клиент жалуется на видимое сообщение - 201, флаг хранит беседу и персону, dialog узнаёт о жалобе`() {
        val client = createUser()
        val message = message(ownerId = client)
        val result =
            report(client, message, reason = "SELF_HARM", comment = "  Пугает  ")
                .andExpect {
                    status { isCreated() }
                    header { string("Location", containsString("/api/v1/moderation/flags/")) }
                    jsonPath("$.messageId") { value(message.id.toString()) }
                    jsonPath("$.conversationId") { value(message.conversationId.toString()) }
                    jsonPath("$.personaId") { value(message.personaId.toString()) }
                    jsonPath("$.reporterId") { value(client.toString()) }
                    jsonPath("$.source") { value("USER") }
                    jsonPath("$.reason") { value("SELF_HARM") }
                    jsonPath("$.comment") { value("Пугает") }
                    jsonPath("$.status") { value("OPEN") }
                    jsonPath("$.message") { value(nullValue()) }
                }.andReturn()
        assertThat(result.response.getHeader("Location")).endsWith(result.json()["id"].asString())
        val raised = events.stream(ModerationFlagRaised::class.java).toList().single()
        assertThat(raised.messageId).isEqualTo(message.id)
        assertThat(raised.reason).isEqualTo(FlagReason.SELF_HARM)
    }

    @Test
    fun `повторная жалоба того же автора - 409, другой автор - можно`() {
        val client = createUser()
        val specialist = createUser(RoleCode.SPECIALIST)
        val message = message(ownerId = client)
        reportOk(client, message)
        report(client, message, reason = "SPAM").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("FLAG_ALREADY_REPORTED") }
        }
        report(specialist, message).andExpect { status { isCreated() } }
    }

    @Test
    fun `невидимое сообщение - 404 MESSAGE_NOT_FOUND, администратор без роли USER жаловаться не может - 403`() {
        val client = createUser()
        val hidden = UUID.randomUUID()
        every { dialogs.findMessageVisibleTo(hidden, any()) } returns null
        mockMvc
            .post("/api/v1/moderation/flags") {
                header(USER_HEADER, client)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("messageId" to hidden, "reason" to "ABUSE"))
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("MESSAGE_NOT_FOUND") }
            }
        mockMvc
            .post("/api/v1/moderation/flags") {
                header(USER_HEADER, createUser(RoleCode.ADMIN))
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("messageId" to hidden, "reason" to "ABUSE"))
            }.andExpect { status { isForbidden() } }
        verify(exactly = 1) { dialogs.findMessageVisibleTo(hidden, any()) }
    }

    @Test
    fun `очередь - только администратору, с X-Total-Count, фильтрами и превью флагнутого сообщения`() {
        val admin = createAdmin()
        val client = createUser()
        val kept = message(ownerId = client, body = "Флагнутый ответ")
        val gone = message(ownerId = client)
        val spam = message(ownerId = client)
        val keptFlag = reportOk(client, kept, "ABUSE")
        reportOk(client, gone, "ABUSE")
        reportOk(client, spam, "SPAM")
        every { dialogs.findMessage(kept.id) } returns kept
        every { dialogs.findMessage(gone.id) } returns null
        every { dialogs.findMessage(spam.id) } returns spam

        mockMvc.get("/api/v1/moderation/flags?reason=ABUSE&sort=createdAt,asc") { header(USER_HEADER, admin) }.andExpect {
            status { isOk() }
            header { string("X-Total-Count", "2") }
            jsonPath("$[0].id") { value(keptFlag.toString()) }
            jsonPath("$[0].message.body") { value("Флагнутый ответ") }
            jsonPath("$[0].message.sender") { value("PERSONA") }
            jsonPath("$[1].message") { value(nullValue()) }
        }
        mockMvc.get("/api/v1/moderation/flags?size=1") { header(USER_HEADER, admin) }.andExpect {
            header { string("X-Total-Count", "3") }
            header { string("X-Total-Pages", "3") }
            jsonPath("$.length()") { value(1) }
        }
        mockMvc.get("/api/v1/moderation/flags?status=RESOLVED") { header(USER_HEADER, admin) }.andExpect {
            header { string("X-Total-Count", "0") }
        }
        mockMvc.get("/api/v1/moderation/flags/$keptFlag") { header(USER_HEADER, admin) }.andExpect {
            status { isOk() }
            jsonPath("$.message.body") { value("Флагнутый ответ") }
        }
        verify(exactly = 0) { dialogs.findMessageVisibleTo(any(), match { it.isAdmin }) }
    }

    @Test
    fun `не администратор очередь не видит - 403, без заголовка - 401, чужой id - 404`() {
        val client = createUser()
        mockMvc.get("/api/v1/moderation/flags") { header(USER_HEADER, client) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
        mockMvc.get("/api/v1/moderation/flags/${UUID.randomUUID()}") { header(USER_HEADER, client) }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/moderation/flags").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/moderation/flags/${UUID.randomUUID()}") { header(USER_HEADER, createAdmin()) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("FLAG_NOT_FOUND") }
        }
    }

    @Test
    fun `вердикт - OPEN, IN_REVIEW, RESOLVED, терминальный статус - 409, событие с автором жалобы`() {
        val admin = createAdmin()
        val client = createUser()
        val message = message(ownerId = client)
        val id = reportOk(client, message)
        every { dialogs.findMessage(message.id) } returns message

        review(admin, id, mapOf("status" to "IN_REVIEW")).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("IN_REVIEW") }
            jsonPath("$.assigneeId") { value(admin.toString()) }
            jsonPath("$.resolvedAt") { value(nullValue()) }
        }
        assertThat(events.stream(ModerationFlagResolved::class.java).count()).isZero()

        review(admin, id, mapOf("status" to "RESOLVED", "resolution" to "Нарушение подтверждено")).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("RESOLVED") }
            jsonPath("$.resolution") { value("Нарушение подтверждено") }
            jsonPath("$.resolvedAt") { exists() }
            jsonPath("$.personaArchived") { value(nullValue()) }
        }
        val resolved = events.stream(ModerationFlagResolved::class.java).toList().single()
        assertThat(resolved.status).isEqualTo("RESOLVED")
        assertThat(resolved.reporterId).isEqualTo(client)

        review(admin, id, mapOf("status" to "REJECTED")).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("FLAG_INVALID_STATE") }
        }
        review(client, id, mapOf("status" to "REJECTED")).andExpect { status { isForbidden() } }
        verify(exactly = 0) { personas.archive(any(), any()) }
    }

    @Test
    fun `archivePersona вместе с RESOLVED архивирует персону после фиксации вердикта`() {
        val admin = createAdmin()
        val client = createUser()
        val message = message(ownerId = client)
        val id = reportOk(client, message)
        every { dialogs.findMessage(message.id) } returns message
        every { personas.archive(message.personaId, any()) } just runs

        review(admin, id, mapOf("status" to "RESOLVED", "archivePersona" to true)).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("RESOLVED") }
            jsonPath("$.personaArchived") { value(true) }
        }
        verify(exactly = 1) { personas.archive(message.personaId, match { it.userId == admin && it.isAdmin }) }
    }

    @Test
    fun `неудачная архивация не отменяет вердикт, archivePersona без RESOLVED - 400`() {
        val admin = createAdmin()
        val client = createUser()
        val first = message(ownerId = client)
        val second = message(ownerId = client)
        val firstId = reportOk(client, first)
        val secondId = reportOk(client, second)
        every { dialogs.findMessage(any()) } returns null
        every { personas.archive(first.personaId, any()) } throws NotFoundException.of(ErrorCode.PERSONA_NOT_FOUND, first.personaId)

        review(admin, firstId, mapOf("status" to "RESOLVED", "archivePersona" to true)).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("RESOLVED") }
            jsonPath("$.personaArchived") { value(false) }
        }
        review(admin, secondId, mapOf("status" to "REJECTED", "archivePersona" to true)).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("archivePersona") }
        }
        mockMvc.get("/api/v1/moderation/flags/$secondId") { header(USER_HEADER, admin) }.andExpect { jsonPath("$.status") { value("OPEN") } }
    }

    @Test
    fun `MessageAutoFlagged дважды в одной транзакции - ровно один системный флаг`() {
        val admin = createAdmin()
        val message = message()
        every { dialogs.findMessage(message.id) } returns message
        val event = MessageAutoFlagged(message.id, message.conversationId, FlagReason.SELF_HARM, "детектор: самоповреждение", Instant.now())

        TransactionTemplate(transactionManager).executeWithoutResult {
            publisher.publish(event)
            publisher.publish(event.copy(eventId = UUID.randomUUID()))
        }

        val count =
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM admin.moderation_flags WHERE message_id = ? AND source = 'GUARDRAIL'",
                Long::class.java,
                message.id,
            )
        assertThat(count).isEqualTo(1)
        mockMvc.get("/api/v1/moderation/flags") { header(USER_HEADER, admin) }.andExpect {
            header { string("X-Total-Count", "1") }
            jsonPath("$[0].source") { value("GUARDRAIL") }
            jsonPath("$[0].reporterId") { value(nullValue()) }
            jsonPath("$[0].reason") { value("SELF_HARM") }
            jsonPath("$[0].comment") { value("детектор: самоповреждение") }
            jsonPath("$[0].personaId") { value(message.personaId.toString()) }
        }
    }

    @Test
    fun `системный флаг вне транзакции создаётся сразу, другая причина - отдельный флаг, пропавшее сообщение - без флага`() {
        val message = message()
        val missing = UUID.randomUUID()
        every { dialogs.findMessage(message.id) } returns message
        every { dialogs.findMessage(missing) } returns null

        publisher.publish(MessageAutoFlagged(message.id, message.conversationId, FlagReason.SELF_HARM, "", Instant.now()))
        publisher.publish(MessageAutoFlagged(message.id, message.conversationId, FlagReason.ABUSE, "мат", Instant.now()))
        publisher.publish(MessageAutoFlagged(missing, UUID.randomUUID(), FlagReason.ABUSE, "мат", Instant.now()))

        val reasons =
            jdbcTemplate.queryForList("SELECT reason FROM admin.moderation_flags WHERE source = 'GUARDRAIL' ORDER BY reason", String::class.java)
        assertThat(reasons).containsExactly("ABUSE", "SELF_HARM")
        assertThat(events.stream(ModerationFlagRaised::class.java).count()).isEqualTo(2)
    }
}
